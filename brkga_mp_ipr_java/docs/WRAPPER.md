# WRAPPER.md — Implementation notes for the Java BRKGA-MP-IPR wrapper

This document covers the **implementation** of the Java edition of BRKGA-MP-IPR:
the architecture, the design decisions, performance, threading, known issues, and
next steps. If you only want to *use* the library, read the
[GUIDE](GUIDE.md) instead; this document explains how it works under the hood.

The Java edition is an idiomatic Java API built on top of the original
**header-only C++ library** (`third_party/brkga_mp_ipr_cpp`, a pinned git
submodule), which is reused
**unmodified**. The entire BRKGA core (evolution, path relinking, shaking,
exchange, reset) runs in native C++; only the *decoder* and the problem-specific
code are written in Java. Java and C++ communicate through the **Foreign Function
& Memory API (FFM, `java.lang.foreign`)**.

## Contents

- [Architecture](#architecture)
- [The C++ bridge](#the-c-bridge)
- [The Java FFM binding](#the-java-ffm-binding)
- [Public API design decisions](#public-api-design-decisions)
- [Multiple objectives](#multiple-objectives)
- [Multi-threading and reproducibility](#multi-threading-and-reproducibility)
- [Decoder rewrite = true](#decoder-rewrite--true)
- [Extra per-chromosome blob](#extra-per-chromosome-blob)
- [Performance](#performance)
- [Testing and validation](#testing-and-validation)
- [Known issues and limitations](#known-issues-and-limitations)
- [Next steps](#next-steps)
- [Appendix: C++ → Java API mapping](#appendix-c--java-api-mapping)

---

## Architecture

### Why a C++ bridge is mandatory

The library `brkga_mp_ipr/brkga_mp_ipr.hpp` is **header-only and
template-based**: the main class is `BRKGA::BRKGA_MP_IPR<Decoder>`, parameterized
at *compile time* by the user's decoder type. Two consequences follow:

1. **There is no compiled shared object with stable C symbols.** FFM can only
   call functions with a **C ABI** (unmangled symbols, POD types). So Java cannot
   call the library directly.
2. **The decoder is user code that the library calls back**, and it does so from
   inside `#pragma omp parallel for` loops — i.e. **concurrently from several
   OpenMP threads**.

Therefore a **minimal C++ bridge** (`native/brkga_bridge.cpp` →
`libbrkga_bridge_*.so`) is required. It:

- compiles against the header-only library (the template **instantiation** happens
  here, in C++);
- exposes a handful of `extern "C"` functions with a C ABI that FFM can call;
- contains a **`ProxyDecoder`** whose `decode(...)` method forwards the call to
  Java through a function pointer (an FFM **upcall stub**).

> The bridge **does not modify** the BRKGA-MP-IPR library. It is only the glue
> that replaces the `main` and the `#include` that, in C++, instantiated the
> template. What is genuinely *ported to Java* — and where the problem-specific
> logic lives — is the **decoder**, the **instance/data**, and the **main
> program**. The genetic loop keeps running in native C++.

### Components

| Piece | Language | Ported? |
|---|---|---|
| BRKGA-MP-IPR algorithm (header-only, templates) | C++ (untouched) | No |
| `native/brkga_bridge.cpp` → `libbrkga_bridge_{1..5,generic}.so` (`extern "C"` + `ProxyDecoder` + `ProxyDistance`) | C++ (new glue) | New glue |
| FFM binding (`NativeBrkga`, package-private) | Java (new glue) | New glue |
| Public API (`brkga` package: `Brkga`, `BrkgaMpIpr`, `Decoder`, `Chromosome`, params, enums, callbacks, `Population`) | Java | Yes |
| Problem code (`brkga.tsp.*`) and runnable examples (`brkga.single/.multi/.multi3`), in the separate `examples/` project | Java | Yes |
| Decode-overhead benchmark (self-contained, own minimal C++/Java TSP) under `bench/` | C++ + Java | New |

---

## The C++ bridge

`native/brkga_bridge.cpp` is parameterized by the number of objectives and built
into several shared libraries (see [Multiple objectives](#multiple-objectives)).
It contains:

- **`ProxyDecoder`** — a C++ class used as the `Decoder` template argument. Its
  `decode(BRKGA::Chromosome& chromosome, bool rewrite)` forwards to the Java
  upcall, passing `chromosome.data()` (the **live** gene buffer), the size, the
  `rewrite` flag, and an `out_fitness` array the Java side fills:

  ```c
  typedef void (*decode_fn)(double* chromosome, size_t size, int rewrite,
                            double* extra, size_t extra_size,
                            void* user_data, double* out_fitness);
  ```

  Because `chromosome.data()` is the real buffer, Java reads it with no copy and,
  when `rewrite` is set, may write back into it (see
  [Decoder rewrite = true](#decoder-rewrite--true)). The `extra` pointer is the
  chromosome's inline extra blob (see
  [Extra per-chromosome blob](#extra-per-chromosome-blob)).

- **`ProxyDistance`** — a `BRKGA::DistanceFunctionBase` subclass that forwards
  `distance` / `affectSolution` / `affectSolution(block)` to Java upcalls, used
  for a CUSTOM IPR distance function.

- **Flat POD structs** mirrored in Java via `MemoryLayout`/`StructLayout`, so FFM
  can read/write them directly:
  - `BrkgaParamsC` (7 doubles + 11 ints) ↔ `BrkgaParams`;
  - `ControlParamsC` (1 long + 5 ints) ↔ `ControlParams`;
  - `BridgeStatus` (`best_fitness[CAP]`, three duration doubles, `num_objectives`,
    and the integer counters) ↔ `AlgorithmStatus`. The C++
    `std::chrono::duration` fields are flattened to `double` seconds, and the
    `fitness_t` to a `double[]`.

- **~43 `extern "C"` functions** covering the whole public surface:
  configuration I/O (`brkga_read_configuration`, `brkga_config_*`,
  `brkga_write_configuration`, `brkga_*_to_string`), lifecycle (`brkga_create`,
  `brkga_destroy`), callbacks (`brkga_set_stopping_criteria`,
  `brkga_add_new_solution_observer`, `brkga_set_initial_population`,
  `brkga_set_bias_custom_function`, `brkga_set_shaking_method`,
  `brkga_config_set_custom_distance`), operations (`brkga_run`, `brkga_evolve`,
  `brkga_path_relink`, `brkga_exchange_elite`, `brkga_shake`, `brkga_reset`,
  `brkga_inject_chromosome`), getters, and the `brkga_shaking_*` helpers that back
  the mutable shaking view. The authoritative list is the set of
  `dc("...")`/`lookup` calls in
  [`NativeBrkga.java`](../src/main/java/brkga/NativeBrkga.java).

The bridge is compiled with the same aggressive flags as the original example
(`-std=c++20 -O3 -fPIC -shared -fopenmp -DMATING_SEQUENTIAL`); see
[`native/Makefile`](../native/Makefile).

---

## The Java FFM binding

`NativeBrkga` (package-private) is the only class that touches
`java.lang.foreign`. Each instance:

- loads one bridge `.so` with `SymbolLookup.libraryLookup` over a shared
  `Arena` (`GLOBAL_ARENA`, `Arena.ofShared()`, so upcalls stay valid when invoked
  from OpenMP threads);
- builds one `MethodHandle` per `extern "C"` function via
  `Linker.nativeLinker().downcallHandle(...)`;
- turns Java callbacks into native function pointers with
  `Linker.upcallStub(...)` (one stub builder per callback shape: decode, info
  observer/stopping, bias, distance, affect-key, affect-block, shaking).

The decode upcall reinterprets the incoming address as a sized `MemorySegment`,
wraps it in a `Chromosome`, calls the user's `Decoder`, and writes the returned
`double[]` into the native `out_fitness` segment. All of this is internal; no FFM
type ever crosses the public API.

---

## Public API design decisions

1. **Single package, FFM hidden.** Everything lives in package `brkga`. The FFM
   binding (`NativeBrkga` and its callback interfaces) is **package-private**, so
   the public API exposes no `MemorySegment`, `Arena`, `StructLayout`, etc. The
   `LAYOUT`/`writeTo`/`readFrom` marshalling members of the POJOs are
   package-private for the same reason.

2. **`Brkga` is the clean handle.** Users never see the native binding. A handle
   is created for a fixed number of objectives with `Brkga.forObjectives(n)` (or
   `Brkga.single()` / `Brkga.multi()`), which loads the right bridge internally.
   `readConfiguration`, `writeConfiguration` and `format` are **instance methods**
   on the handle, returning/accepting Java POJOs.

3. **`Decoder` returns `double[]`; `Chromosome` is a clean view.** The decoder is
   `double[] decode(Chromosome chromosome, boolean rewrite)` — faithful to the C++
   `fitness_t decode(Chromosome&, bool)`. `Chromosome` exposes `size()`,
   `get(i)`, `set(i, v)`, `toArray()`; reads go straight to native memory
   (zero-copy) without exposing it.

4. **POJOs and status.** `BrkgaParams` / `ControlParams` are mutable POJOs with
   idiomatic camelCase fields (the underlying C struct uses snake_case; the
   `withName(...)` layout labels keep that spelling to mirror the ABI).
   `AlgorithmStatus` is a read-only mirror whose `toString()` reproduces the C++
   `operator<<` output **verbatim** (its labels are intentionally snake_case).

5. **Enums** carry the exact integer value the bridge understands (`Sense`,
   `BiasFunctionType`, `ShakingType`, and the nested `PathRelinking.*`).

6. **Distance functions.** The built-in `HAMMING` / `KENDALLTAU` are selected by
   enum and run in C++; `CUSTOM` is a Java `DistanceFunction` (Chromosome-based)
   invoked through an upcall and supplied at construction.

7. **`Population`** is a read-only view backed by native getters; the C++
   `Population` is documented "not meant to be used externally", so its mutable
   interface is not ported.

8. **Callbacks are upcalls** (observer, stopping criteria, bias, shaking,
   distance). Note a subtlety: the new-solution **observer returns `true` to keep
   running** (`false` to stop), which matches the actual C++ `run()` loop
   (`run &= callback(status)`); the C++ documentation comment claims the opposite
   of what its own code does, so the Java contract follows the code.

9. **Lifecycle.** `BrkgaMpIpr` is `AutoCloseable`; the native algorithm and the
   native configuration are released on `close()`. Use try-with-resources.

---

## Multiple objectives

In C++ the number of objectives is fixed at compile time via `fitness_t`. In Java
it is chosen at run time: the wire format is uniform (`MAX_OBJECTIVES = CAP = 20`
doubles) and there are several bridge binaries:

- `libbrkga_bridge_1.so` … `libbrkga_bridge_5.so` — dedicated `std::tuple`
  instantiations for N = 1..5 (efficient);
- `libbrkga_bridge_generic.so` — a custom `fitness_t` of capacity `CAP = 20` with
  the arity chosen at run time (`g_num_objectives`).

`Brkga.forObjectives(n)` prefers the dedicated tuple binary and falls back to the
generic one (up to `CAP`). The decoder writes N values into the returned
`double[]`, and `AlgorithmStatus.bestFitness` has length N. To support N > 20, or
to add dedicated tuple binaries beyond 5, see
[JAVA_GUIDE.md](JAVA_GUIDE.md#number-of-objectives).

---

## Multi-threading and reproducibility

The native core decodes in parallel with OpenMP, calling the Java decoder from
several threads through FFM upcalls (the JVM attaches non-JVM threads
automatically). Consequences and decisions:

- **The decoder must be thread-safe.** Keep writable scratch state per thread
  (e.g. a `ThreadLocal`) or allocate it per call. The TSP decoders use a
  `ThreadLocal<long[]>` buffer.
- **Mating parallelism** is a compile-time choice of the bridge, controlled by
  `MATING_SEQUENTIAL` / `MATING_SEED_ONLY` / `MATING_FULL_SPEED` (see
  `native/Makefile`). This project builds with **`MATING_SEQUENTIAL`** so results
  are deterministic for a given seed and comparable, allele-for-allele, with the
  native C++ reference.
  - `MATING_SEED_ONLY`: parallel mating, reproducible from the seed alone.
  - `MATING_FULL_SPEED`: fastest, but reproducibility depends on *both* the seed
    and the number of threads.
- **Determinism criterion.** With the same seed, one thread, and a fixed number
  of iterations, the Java wrapper and the C++ reference produce identical
  `best_fitness`, counters, sizes and best-gene — verified by `EquivalenceTest`.

Hardening still worth doing: long multi-thread stress runs to detect native-memory
leaks or thread-attach issues; explicit checks of `MATING_SEED_ONLY` /
`MATING_FULL_SPEED` effects.

---

## Decoder rewrite = true

BRKGA calls `decode(..., true)` during evolution, injection and initialization,
and `decode(..., false)` during path relinking (so the path between solutions is
preserved). Rewrite is supported end-to-end:

- the bridge passes the **live** chromosome buffer (`chromosome.data()`) to the
  upcall;
- a decoder called with `rewrite = true` may write back with
  `Chromosome.set(i, value)`; the write lands in that native buffer and BRKGA
  keeps the modified chromosome;
- with `rewrite = false` the decoder must not modify the chromosome (this is the
  decoder's responsibility, as in C++; the wrapper does not enforce it).

Verified by `RewriteTest`: after `evolve(1)`, every stored chromosome reflects a
sentinel the decoder wrote on `rewrite = true`; and `pathRelink` is observed to
decode candidates with `rewrite = false`.

---

## Extra per-chromosome blob

This is the Java rendering of the C++ "generic chromosome" pattern (extending
`std::vector<double>` with extra fields). The user-facing API and staleness rules
live in [GUIDE.md](GUIDE.md#keeping-extra-data-in-the-chromosome); this section
records the implementation.

**Custom chromosome type.** [`native/custom_chromosome.hpp`](../native/custom_chromosome.hpp)
defines `BRKGA::Chromosome` as a subclass of `std::vector<double>` with one extra
member, a fixed-size C array `double extra[EXTRA_CAP]`. The Makefile vendors this
header over the library's `chromosome.hpp` in every build's include dir (exactly
as it already swaps `generic_fitness_type.hpp`), so **upstream `brkga_mp_ipr.hpp`
is not modified.** `chromosome.hpp` itself documents that `Chromosome` is an alias
precisely to allow this. The genes are still the `std::vector<double>` base, so
the algorithm and the gene buffer the decoder sees (`chromosome.data()`) are
byte-for-byte unchanged — the `EquivalenceTest` against the C++ reference still
passes.

**Value semantics carry the blob.** The blob is a value-type member, so the
implicitly-declared copy/move special members copy it along with the genes. The
subclass keeps a deliberately **minimal** surface — it only adds the data member
and inherits the base constructors and `operator=`:

```cpp
class Chromosome: public std::vector<double> {
public:
    double extra[CHROMOSOME_EXTRA_CAP] {};
    using std::vector<double>::vector;
    using std::vector<double>::operator=;
};
```

This is what makes `chromosome = otherChromosome` (the elite carry-over) use the
implicit Chromosome assignment, which copies `extra`, while
`chromosome = geneVector` (the mating step assigns a `std::vector<double>`
offspring) uses the inherited base assignment, which touches only the genes (the
decoder refreshes the blob immediately afterwards). Two pitfalls were hit and
avoided here:

- *Do not include `<array>`*: pulling it in before the library's `<chrono>` trips
  a GCC 13 libstdc++ bug in `<format>` (incomplete `std::array<_Arg_t,N>`). A
  plain C array needs no extra header.
- *Do not add user-declared converting constructors/operators*: they make
  libstdc++'s `std::vector` relocation/move-insert traits ill-formed under GCC 13
  (`constexpr call flows off the end`). The minimal `using`-only surface keeps the
  traits happy.

**Bridge.** `ProxyDecoder::decode` passes `chromosome.extra` and
`CHROMOSOME_EXTRA_CAP` to the upcall. Three getters expose the blob:
`brkga_extra_capacity`, `brkga_get_best_chromosome_extra` and
`brkga_get_chromosome_extra`. `EXTRA_CAP` (default 8) is a `-D` set uniformly for
all binaries, so the layout is identical everywhere.

**Java.** `Chromosome` gains `extraSize()`, `getExtra(int)` and
`setExtra(int, double)`; the extra `MemorySegment` is reinterpreted **lazily**, so
decoders that never touch the blob pay nothing. `BrkgaMpIpr` adds
`extraCapacity()`, `getBestChromosomeExtra()` and `getChromosomeExtra(pop, pos)`.
`ExtraBlobTest` checks the blob round-trips and is consistent with the best
chromosome's genes in a single-population run.

---

## Performance

### What is measured and why it is fair

In the Java edition **the entire BRKGA core runs in the same native code** a pure
C++ program would use (the bridge compiles the same `brkga_mp_ipr.hpp` with the
same flags). The **only** difference is objective evaluation (*decode*): in Java
it crosses the FFM boundary (an upcall into the JVM) per chromosome; in C++ it is
a native call. So, running the **same work** (same instance, seed, iteration count
and threads), the time difference is the net effect of solving the problem with
Java+FFM instead of pure C++. The benchmark fixes the iteration count (like
`MainMaximumIterations`) and times only `run()` (logger disabled via
`setQuiet`).

The benchmark is **self-contained** under [`bench/`](../bench): it has its own
minimal TSP code in C++ and Java. The C++ baseline (`bench_native`) links the
**stock** BRKGA core (same `brkga_mp_ipr.hpp`, flags and mating mode the bridge
uses), so for each side the only difference is crossing into the JVM to decode.

Two decoder variants are benchmarked, each kept **algorithmically equivalent**
between C++ and Java:

- **basic** — read each gene once, sort the node indices (`Integer[]` + comparator
  in Java; `vector<pair<key,index>>` + `std::sort` in C++), sum the tour.
- **optimized** — a reusable per-thread buffer (no per-decode allocation) and a
  primitive **packed sort**: each gene's IEEE-754 bits (high) packed with the node
  index (low) into a 64-bit integer, sorted as a primitive array — no boxing, no
  comparator. (`ThreadLocal long[]` + `Arrays.sort` in Java; `thread_local
  vector<uint64_t>` + `std::sort` in C++.)

### Results

Measured in a shared dev container (x86-64; **1 thread**; JDK 25; g++ 13 `-O3`;
min of 3 repetitions; seed `27000001`). Times in ms (only `run()`). Absolute
times and exact percentages vary substantially with the machine and load — these
are **indicative**; reproduce on your own hardware with `bench.py`.

| instance | nodes | iters | java_basic | cpp_basic | ovh | java_opt | cpp_opt | ovh |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| burma14  | 14   | 500 |  8990 | 8098 | +11 % |  8746 | 8938 | −2 % |
| brazil58 | 58   | 200 |  6598 | 4994 | +32 % |  5433 | 4839 | +12 % |
| rd400    | 400  | 35  |  7251 | 4943 | +47 % |  4152 | 3768 | +10 % |
| vm1084   | 1084 | 10  |  7194 | 4076 | +76 % |  3651 | 3574 |  +2 % |

`ovh = java / cpp − 1`. All four configurations find the same solution per instance
(e.g. 26692 on brazil58), confirming the decoders are equivalent.

### Interpretation

- **The BRKGA core itself runs at native speed** — the bridge compiles the same
  `brkga_mp_ipr.hpp`. The only measurable cost is the **decode boundary**: each
  chromosome evaluation makes one FFM upcall into the JVM and reads its genes
  through the `Chromosome` view.
- **With the basic decoder, the Java overhead grows with the decode size** (+11 %
  on `burma14` up to +76 % on `vm1084`). The cause is JVM-side, not the BRKGA core:
  per-gene reads through the view plus boxing (`Integer[]`), comparator callbacks
  and per-decode allocation — all `O(n)` or worse per decode.
- **The optimized decoder almost erases the gap** (−2 % to +12 %, i.e. roughly par
  with native C++) **and cuts absolute time** for both languages (e.g. `vm1084`
  Java 7194 → 3651 ms). Reusing a per-thread buffer and sorting a primitive packed
  array removes the allocation, boxing and comparator costs; what remains is
  essentially the bare FFM boundary, which is small.
- **Takeaway**: the boundary cost is real but low; an efficient decoder (bulk gene
  reads, primitives, no per-call allocation) makes the Java edition perform on par
  with native C++. See
  [JAVA_GUIDE.md → Writing a fast decoder](JAVA_GUIDE.md#writing-a-fast-decoder).
  (An earlier version of this benchmark compared an *optimized* Java decoder
  against a *basic* C++ one and reported Java as faster — an apples-to-oranges
  artifact now fixed by comparing like with like.)

### Reproduce

The benchmark builds everything it needs (native baseline, wrapper, Java classes):

```bash
cd bench
python3 bench.py 5           # min of 5 repetitions per case
```

See [`bench/README.md`](../bench/README.md) for details. Notes: 1 thread isolates
the FFM boundary from OpenMP thread-attach noise and contention; absolute times
are machine-dependent — the **ratio** java / cpp is what matters.

Minor optimizations still open: explore `Linker.Option.critical` for short
downcalls that do not re-enter the JVM (does not apply to `brkga_run`), and
micro-optimize the upcall (avoid the per-call `reinterpret` if profiling shows it
matters).

---

## Testing and validation

`mvn test` builds the `.so`'s and the native `tests_reference` binary, then runs
the JUnit suite (20 tests):

- **Black-box** (from the docs): `readConfiguration`/`writeConfiguration`
  (`ConfigurationTest`), enum integer values (`EnumTest`).
- **White-box** (from the code): constructor-derived getters (`GettersTest`);
  determinism — same seed + 1 thread ⇒ identical result (`DeterminismTest`);
  `evolve`/`pathRelink`/`Population`/`inject`/`shake`/`reset` (`OperationsTest`);
  the CUSTOM callbacks — custom bias `1/r` ≡ `LINEAR`, custom distance ≡
  `HAMMING`, shaking invoked (`CustomCallbacksTest`); decoder write-back with
  `rewrite = true` (`RewriteTest`); the inline extra blob round-trips and stays
  consistent with the best chromosome's genes (`ExtraBlobTest`).
- **Java↔C++ equivalence** (`EquivalenceTest`): the wrapper and the native
  `tests_reference` run the same deterministic scenario (fixed seed, 1 thread,
  fixed iteration count) and must agree on `best_fitness`, the counters, the
  sizes and the first gene of the best chromosome.

Against the C++ reference binaries (same seed), the single- and multi-objective
examples reach the `burma14` cost optimum (3323) and share the early
deterministic trajectory; `MainMaximumIterations` stops exactly at the requested
iteration, and `MainComplete` (rule `G`) at the requested number of generations.

### Documented deviations

- **Warm start** (`MainComplete`): the C++ code draws the initial chromosome's
  keys with `std::mt19937`; Java uses `java.util.Random`. The injected **tour**
  (the greedy solution) is identical — only the relative order of the keys matters
  — but the concrete gene values differ, which may slightly affect later
  crossover. See `brkga.tsp.WarmStart`.
- **Time formatting**: durations are printed with an `s` suffix, with a
  floating-point format that may differ slightly from libstdc++.

---

## Known issues and limitations

- **Linux-only.** The native bridge and the FFM flags are set up for Linux.
- **FFM native access.** When running `java` directly, pass
  `--enable-native-access=ALL-UNNAMED`; otherwise the JVM warns (and, in future
  releases, may fail) on native calls. `run.sh` sets it.
- **One bridge library per objective count.** The number of objectives is bound
  to a `.so` (`libbrkga_bridge_<n>.so` or the generic one). `Brkga.forObjectives(n)`
  picks the right one; N > 20 needs a larger `CAP` (see
  [JAVA_GUIDE.md](JAVA_GUIDE.md#number-of-objectives)).
- **`rewrite = false` is a contract, not enforced.** As in C++, the decoder must
  not modify the chromosome when `rewrite = false` (during IPR); the wrapper
  trusts the decoder.

---

## Next steps

- **Distributable packaging**: a JAR with the `.so`(s) embedded and
  self-extracting, hiding the JVM flags from the user (the public API already
  exposes no FFM types).
- **CI**: build the bridge and run `mvn test` automatically.
- **`jextract` alternative**: evaluate generating the binding from a C header
  (`brkga_bridge.h`).
- **More problems/examples** beyond the TSP, to exercise API paths the current
  examples do not.
- **N-objective extensions** if ever needed: raise `CAP` for N > 20, or add
  dedicated tuple binaries for N > 5.

---

## Appendix: C++ → Java API mapping

For readers who know the C++ library. The public API was derived from
`brkga_mp_ipr/brkga_mp_ipr.hpp` and the Doxygen docs (only `public:` members,
documented types, enums and free functions are ported; `protected:`/`private:`
members and anything "not meant to be used externally" are implementation
details and are not exposed).

### Types

| C++ | Java |
|---|---|
| `Chromosome = std::vector<double>` | `Chromosome` (`size()`/`get`/`set`/`toArray`) / `double[]` |
| `fitness_t = double` (or `tuple<double,…>`) | `double[]` (length 1..N) |
| `EQUALITY_THRESHOLD`, `FITNESS_T_MIN/MAX` | internal (not exposed) |

### Free functions

- C++ `readConfiguration` / `writeConfiguration` (free functions taking
  streams/filenames) → Java **instance methods** on the `Brkga` handle:
  `brkga.readConfiguration(file)` → `Brkga.Config(brkgaParams, controlParams)`;
  `brkga.writeConfiguration(file, brkgaParams, controlParams)`; plus
  `brkga.format(params)`.

### `BRKGA_MP_IPR<Decoder>` → `BrkgaMpIpr`

- **Constructor.** C++
  `BRKGA_MP_IPR(decoder, sense, seed, chromosome_size, params, max_threads=1, evolutionary_mechanism_on=true)`
  → Java `new BrkgaMpIpr(brkga, decoder, sense, seed, chromosomeSize, params, numThreads[, evolution[, customDistance]])`.
  The first Java argument is the `Brkga` handle (no C++ equivalent); the optional
  `customDistance` is the CUSTOM IPR `DistanceFunction`.
- **Callbacks** (each is an FFM upcall): `setInitialPopulation`,
  `setBiasCustomFunction`, `setShakingMethod`, `setStoppingCriteria`,
  `addNewSolutionObserver`.
- **Execution**: `run(ControlParams)` → `AlgorithmStatus` (no `ostream*` logger;
  use `setQuiet(boolean)`); `evolve(int)` / `evolve()`.
- **Path relinking**: the two C++ overloads are unified into
  `pathRelink(Type, Selection, DistanceFunctionType, numberPairs, minimumDistance, blockSize, maxTimeSeconds, percentage)`.
  The distance is chosen by enum (`HAMMING`/`KENDALLTAU`) or `CUSTOM` (the
  `DistanceFunction` passed at construction); there is no `shared_ptr` parameter
  and `maxTimeSeconds` is a `long`.
- **Population ops**: `exchangeElite(int)`, `shake(int, ShakingType, int)`,
  `reset()`, `injectChromosome(double[], int, int)`.
- **Getters**: `getCurrentPopulation`, `getBestChromosome`, `getBestFitness`,
  `getChromosome`, `getFitness`, `getBrkgaParams`, `getOptimizationSense`,
  `getChromosomeSize`, `getEliteSize`, `getNumMutants`, `getMaxThreads`.

### Distance functions

- C++ `DistanceFunctionBase` (+ built-ins `HammingDistance`,
  `KendallTauDistance`) → Java `DistanceFunction` with `Chromosome`-based
  signatures: `double distance(Chromosome v1, Chromosome v2)`,
  `boolean affectSolution(double key1, double key2)`,
  `boolean affectSolutionBlock(Chromosome v1, Chromosome v2, int blockSize)`. The
  two built-ins are selected by `PathRelinking.DistanceFunctionType`.

### Streams

- C++ `operator<<` for `AlgorithmStatus`, `BrkgaParams`, `ControlParams` →
  `AlgorithmStatus.toString()` (verbatim) and `Brkga.format(...)`.
