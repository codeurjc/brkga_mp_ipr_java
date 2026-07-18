# Java edition — user guide

This document covers the concerns specific to the **Java edition** of
BRKGA-MP-IPR that a user needs to know: how to add it to a project and run the
examples, the platform requirements, how to choose the number of objectives, the
runtime flags, how to recompile the native library for advanced options, and the
high-level performance overhead.

It complements the two other documents:

- **[GUIDE.md](GUIDE.md)** — how to *use the algorithm* (decoder, parameters,
  path relinking, etc.). A faithful Java port of the official C++ guide.
- **[WRAPPER.md](WRAPPER.md)** — the *internals* of the wrapper (architecture,
  the C++ bridge, the FFM binding, design decisions, the full benchmark).

The Java edition reuses the original header-only C++ library **unmodified**: the
optimization core runs natively, and only your decoder and problem code are
Java. The public API (the `brkga` package) never exposes any low-level interop
type.

## Contents

- [Installing the library (Maven)](#installing-the-library-maven)
- [Requirements and platform](#requirements-and-platform)
- [Running the examples](#running-the-examples)
- [Runtime flags and the bridge library](#runtime-flags-and-the-bridge-library)
- [Number of objectives](#number-of-objectives)
- [Recompiling the native library](#recompiling-the-native-library)
- [Running on an older Linux (GLIBC / GLIBCXX too new)](#running-on-an-older-linux-glibc--glibcxx-too-new)
- [Keeping extra data in the chromosome](#keeping-extra-data-in-the-chromosome)
- [Writing a fast decoder](#writing-a-fast-decoder)
- [Performance](#performance)
- [Reproducibility and threads](#reproducibility-and-threads)
- [Behavioral differences from the C++ edition](#behavioral-differences-from-the-c-edition)

---

## Installing the library (Maven)

The wrapper is published to **Maven Central** with the precompiled native
binaries **bundled inside the JAR**, so you need **no C++ compiler** to use it —
just a JDK and your build tool. Add the dependency:

```xml
<dependency>
    <groupId>io.github.codeurjc</groupId>
    <artifactId>brkga-mp-ipr-java</artifactId>
    <version>0.2.0</version>
</dependency>
```

Your application must run with native access enabled:

```bash
java --enable-native-access=ALL-UNNAMED -cp ... your.Main
```

That is all. At first use the wrapper extracts the right `.so` from the JAR to a
temporary file and loads it. You only need the toolchain (or Docker) if you want
to **recompile** the native library for advanced options — see
[Recompiling the native library](#recompiling-the-native-library).

> **On a system whose glibc/libstdc++ are too old** (glibc < 2.32 or a libstdc++
> that does not export GLIBCXX_3.4.32) the bundled binary may fail to load with
> `Cannot open library: /tmp/brkga-native-*/libbrkga_bridge_1.so` (the underlying
> reason is the too-new `GLIBC_2.32` / `GLIBCXX_3.4.32`). Build a compatible bridge
> locally — see [Running on an older Linux](#running-on-an-older-linux-glibc--glibcxx-too-new).

---

## Requirements and platform

**To use the library (the common case):**

- **JDK 25 or newer** (tested with JDK 25). The wrapper uses the Foreign Function
  & Memory API, stable since JDK 22.
- **Linux x86-64.** The bundled native binaries target this platform. On other
  platforms you must build your own (the loader will report that no binary
  applies); see [Recompiling the native library](#recompiling-the-native-library).
  The bundled binaries need **glibc ≥ 2.32 and a libstdc++ exporting
  GLIBCXX_3.4.32 or newer**; on a system with older versions, build them locally —
  see [Running on an older Linux](#running-on-an-older-linux-glibc--glibcxx-too-new).
- Your build tool (Maven/Gradle) to pull the dependency.

**To build the wrapper from source / recompile the native core** (maintainers,
advanced options): additionally **Maven 3.9+** and **g++** with OpenMP
(`-fopenmp`, tested with g++ 13) — or just **Docker**, which carries the whole
toolchain (recommended).

---

## Running the examples

The examples are a **separate, standalone project** (they are not part of the
published library). Get them in one of two ways:

- Download `brkga_mp_ipr_java_examples.zip` from the project's
  [GitHub releases](https://github.com/codeurjc/brkga_mp_ipr_java/releases) and
  unzip it, **or**
- clone the repository and use the [`examples/`](../../examples) directory.

The example project depends on the published artifact, so running it needs **only
a JDK and Maven** (no compiler). The bundled TSP instances and config files live
under `data/`. Use the helper script (the first argument is the main class):

```bash
# single, minimal
./run.sh brkga.single.MainMinimal 27000001 \
  data/config/single.conf 10 data/instances/burma14.dat

# single, fixed number of iterations
./run.sh brkga.single.MainMaximumIterations 27000001 \
  data/config/single.conf 100 data/instances/burma14.dat

# single, complete (docopt-style named options)
./run.sh brkga.single.MainComplete \
  --config data/config/single.conf \
  --seed 27000001 --stop_rule G --stop_arg 100 --maxtime 30 \
  --instance data/instances/brazil58.dat --threads 4

# multi, minimal
./run.sh brkga.multi.MainMinimal 27000001 \
  data/config/multi.conf 10 data/instances/brazil58.dat
```

The ported examples:

| Java class | C++ origin |
|---|---|
| `brkga.single.MainMinimal` | `single_obj/main_minimal.cpp` |
| `brkga.single.MainMaximumIterations` | `single_obj/main_maximum_iterations.cpp` |
| `brkga.single.MainComplete` | `single_obj/main_complete.cpp` |
| `brkga.multi.MainMinimal` | `multi_obj/main_minimal.cpp` |
| `brkga.multi.MainComplete` | `multi_obj/main_complete.cpp` |
| `brkga.multi3.MainMinimal` | (synthetic, 3-objective demo) |

For a deterministic single-thread run: `BRKGA_NUM_THREADS=1 ./run.sh ...` (applies
to the minimal / maximum-iterations examples).

---

## Runtime flags and the bridge library

When invoking `java` directly:

- **`--enable-native-access=ALL-UNNAMED`** — required to allow native access;
  otherwise the JVM warns (and, in future releases, may fail) on native calls.
- **`-Dbrkga.bridge.dir=<dir>`** — directory holding the native `.so` libraries.
  **If unset (the default), the libraries bundled in the JAR are used.** Set it to
  point at your own recompiled binaries (see
  [Recompiling the native library](#recompiling-the-native-library)); it takes
  precedence over the bundled ones.
- **`-Dbrkga.bridge.force_generic=true`** — always use the generic bridge binary
  instead of a dedicated one (see [Number of objectives](#number-of-objectives)).
  The `run.sh` equivalent is `BRKGA_FORCE_GENERIC=1 ./run.sh ...`.
- **`-Dbrkga.num_threads=N`** — number of decoding threads (used by the minimal /
  maximum-iterations examples; the `MainComplete` examples take the thread count
  via `--threads`). The `run.sh` equivalent is `BRKGA_NUM_THREADS=N ./run.sh ...`.

---

## Number of objectives

In the C++ library, `fitness_t` (the objective-value type) is fixed at **compile
time**: `BRKGA_MP_IPR<Decoder>` is instantiated with it, and `AlgorithmStatus`
stores, copies and compares it constantly. So **each objective count is a
different type = a different instantiation = different machine code**.

To balance efficiency and flexibility, the Java edition uses a **hybrid
strategy** and the handle picks the right binary at run time.

### What is built

`make -C native` produces (all share the same status-struct layout):

| Binary | `fitness_t` | Objectives |
|---|---|---|
| `libbrkga_bridge_1.so` | `double` | 1 (fixed at compile time) |
| `libbrkga_bridge_2.so` | `tuple<double,double>` | 2 (fixed) |
| `libbrkga_bridge_3.so` | `tuple<double×3>` | 3 (fixed) |
| `libbrkga_bridge_4.so` | `tuple<double×4>` | 4 (fixed) |
| `libbrkga_bridge_5.so` | `tuple<double×5>` | 5 (fixed) |
| `libbrkga_bridge_generic.so` | custom type (array of `CAP=20`) | 1..20 (at **run time**) |

- **Tuple binaries (1–5):** full native efficiency (unrolled tuples, branch-free
  comparisons) and semantics identical to the C++ examples. They cover the vast
  majority of real cases.
- **Generic binary:** a custom `fitness_t`
  ([`native/generic_fitness_type.hpp`](../native/generic_fitness_type.hpp)) that
  stores a `std::array<double, CAP>` and reads the *active* objective count from a
  run-time global. A single binary serves any N ≤ 20, at the cost of some overhead
  in the decode/sort hot path.

### How the Java side selects a binary

`Brkga.forObjectives(n)` (the handle loads the right `.so` internally):

1. If `libbrkga_bridge_<n>.so` exists, use it (dedicated, efficient).
2. Otherwise, if `n ≤ CAP (20)`, use `libbrkga_bridge_generic.so` with the arity
   fixed to `n`.
3. Otherwise, throw an error explaining how to generate a dedicated binary.

`Brkga.single()` and `Brkga.multi()` are aliases for `forObjectives(1)` and
`forObjectives(2)`. With `-Dbrkga.bridge.force_generic=true` the generic binary is
always used (useful to test that path even when a dedicated binary exists).

### Unified struct layout

All binaries reserve `MAX_OBJECTIVES = CAP = 20` doubles for `best_fitness` in the
status struct, so the memory layout Java reads
([`AlgorithmStatus`](../src/main/java/brkga/AlgorithmStatus.java)) is **identical**
across all binaries; a `num_objectives` field says how many are valid. The cost is
160 bytes in the status struct (not in the hot path).

### Building a dedicated binary for a fixed N > 5

If you need, say, **8 objectives** at maximum performance (instead of the generic
binary), generate a dedicated tuple binary — the same mechanism as for 1–5:

1. Add N to the `TUPLE_NS` list when building (e.g. `TUPLE_NS="1 2 3 4 5 8"`). The
   build generates the `fitness_type.hpp` with `std::tuple<double×N>` from the base
   header automatically. Use Docker (recommended) or a local `make` — see
   [Recompiling the native library](#recompiling-the-native-library). This produces
   `libbrkga_bridge_8.so`.
2. Point the JVM at the output dir with `-Dbrkga.bridge.dir=...`. No Java change is
   needed: `Brkga.forObjectives(8)` detects the dedicated binary and prefers it
   over the generic one.

Why it is faster: the generic binary copies and compares a `std::array<double,20>`
**by value** on every population sort (thousands of operations per generation); a
tuple of the exact size N unrolls and moves only `8·N` bytes.

> For N > 20 (beyond the generic `CAP`) a dedicated binary is **mandatory**; you
> would also have to raise `CAP`/`MAX_OBJECTIVES` (and `Brkga.CAP`) to extend the
> generic binary.

### Generic-binary limitations

- **Process-global arity:** the generic binary cannot optimize two problems with
  a different number of objectives at the same time in the same JVM process; the
  tuple binaries do not have this limitation.
- **Overhead** in decode/sort (above).
- **`CAP = 20`** fixed at compile time.

Verified: for a 3-objective problem, the dedicated binary (`_3.so`) and the
generic one (arity 3) produce identical results.

---

## Recompiling the native library

The bundled binaries cover the common cases. You only need to recompile to:

- build a **dedicated binary for N > 5** objectives (or **N > 20**, beyond the
  generic cap) — see [Number of objectives](#number-of-objectives);
- change the **parallel-mating mode** — see
  [Reproducibility and threads](#reproducibility-and-threads);
- change the **extra-blob capacity** (`EXTRA_CAP`) — see
  [Keeping extra data in the chromosome](#keeping-extra-data-in-the-chromosome);
- target a **platform other than Linux x86-64**.

The recommended way is **Docker**, so you need no local C++ toolchain. Build args
map to the Makefile variables: `MATING`, `TUPLE_NS`, `EXTRA_CAP`, `CAP`.

```bash
# 0. ensure the upstream submodule is present
git submodule update --init

# 1. build the image (context = repo root)
docker build -t brkga-native -f docker/Dockerfile.build .

# 2. produce custom binaries into ./out (override any variable you need)
docker run --rm -v "$PWD/out:/out" \
    -e MATING=MATING_FULL_SPEED \
    -e TUPLE_NS="1 2 3 8" \
    -e EXTRA_CAP=16 \
    -e CAP=32 \
    brkga-native

# 3. run your application against the new binaries
java --enable-native-access=ALL-UNNAMED -Dbrkga.bridge.dir=./out -cp ... your.Main
```

If you prefer a local toolchain (g++ + make), the same variables apply directly:

```bash
make -C brkga_mp_ipr_java/native clean
make -C brkga_mp_ipr_java/native all MATING=MATING_FULL_SPEED TUPLE_NS="1 2 3 8" EXTRA_CAP=16 CAP=32
```

For each requested filename, `-Dbrkga.bridge.dir` takes precedence over the
binary bundled in the JAR. If the file is absent from that directory, the loader
falls back to the bundled version, so place a complete custom set there when the
bundled binaries cannot run on the host.

> For **N > 20** you must also raise `CAP` (and the `Brkga.CAP` constant if you
> rebuild the wrapper) to extend the generic binary, or build a dedicated tuple
> binary for that N.

---

## Running on an older Linux (GLIBC / GLIBCXX too new)

### The symptom

The native binaries bundled in the JAR are compiled on **Ubuntu 24.04**, so they
require **`GLIBC_2.32`** and **`GLIBCXX_3.4.32`**. On an older host — for example
Ubuntu 20.04, which ships glibc 2.31 / GLIBCXX 3.4.28 — the first BRKGA call
(`Brkga.single()` / `Brkga.multi()` / `forObjectives(...)`, which is where the
bridge is loaded) throws. Your application sees only the top-level message — the
JVM does **not** print the reason:

```
java.lang.IllegalArgumentException: Cannot open library: /tmp/brkga-native-<random>/libbrkga_bridge_1.so
```

(the examples print it as `Exception Occurred: Cannot open library: /tmp/brkga-native-…/libbrkga_bridge_1.so`).

The real cause is the too-new glibc/libstdc++. To confirm it, extract the bundled
`.so` and inspect it with `ldd` — you will see the missing versions:

```console
$ ldd libbrkga_bridge_1.so
    ... /lib/x86_64-linux-gnu/libstdc++.so.6: version `GLIBCXX_3.4.32' not found ...
    ... /lib/x86_64-linux-gnu/libc.so.6:      version `GLIBC_2.32' not found ...
```

If you see `Cannot open library: …/libbrkga_bridge_*.so` on a Linux older than
Ubuntu 24.04, this is almost certainly why — build the bridge locally as below.

### Which systems are affected (and which are not)

The real requirement is **not** "the Ubuntu 24.04 distribution"; it is **two
version thresholds, both of which must be met**:

- **`glibc ≥ 2.32`**, and
- a **libstdc++ exporting `GLIBCXX_3.4.32` or newer**. This symbol version first
  appeared in GCC 13.2 (`GLIBCXX_3.4.31` = GCC 13.1, `GLIBCXX_3.4.33` = GCC 14).
  Ubuntu 24.04 is the first Ubuntu LTS whose default runtime meets this threshold.

Because glibc and libstdc++ are **backward-compatible** (a newer library still
exports the older symbol versions), any system **at or above** both thresholds
loads the bundled binary fine. Installing a newer GCC/libstdc++ alone is not
enough when the host glibc is still below 2.32; both checks are independent.

Watch out for the intuition "newer release date = fine": what matters is the
libstdc++ version, not how recent the distro is. For example **Ubuntu 22.04**
has glibc 2.35 (≥ 2.32, fine) but its default libstdc++ is from GCC 12, i.e. only
GLIBCXX_3.4.30 (< 3.4.32) — so it **also fails**, on the GLIBCXX side.

| System | glibc | GLIBCXX (libstdc++) | Loads the bundled `.so`? |
|---|---|---|---|
| Ubuntu 24.04 | 2.39 ✅ | 3.4.32 ✅ | ✅ yes |
| Other distributions | ≥ 2.32 ✅ | ≥ 3.4.32 ✅ | ✅ yes |
| Ubuntu 22.04 | 2.35 ✅ | 3.4.30 ❌ | ❌ no (GLIBCXX too old) |
| Ubuntu 20.04 | 2.31 ❌ | 3.4.28 ❌ | ❌ no |

If your system is in a ❌ row, build the bridge locally as below.

### The fix

Build the bridge yourself so it targets your host. Two things make
the result run on an old system:

1. **Compile against an old-enough glibc** — either directly on the old host, or
   inside an old base image. A newer glibc cannot be "downgraded" by a flag, so
   the [*Recompiling the native library*](#recompiling-the-native-library) Docker
   route (base `gcc:13`) does **not** help here: its glibc is newer still.
2. **Statically link the C++ runtime** with `-static-libstdc++ -static-libgcc`,
   so the binary carries its own libstdc++ and no longer depends on the host's
   `GLIBCXX`. The Makefile compiles with `$(CXX)`, so the simplest way to inject
   those flags (keeping all its tuned optimization flags) is to fold them into
   `CXX`: `CXX="g++-13 -static-libstdc++ -static-libgcc"`.

> **Toolchain note.** The bridge needs a real C++20 standard library
> (`<concepts>`, and `operator<<` for `std::chrono::duration`), so **g++ 13** is
> required — older compilers on old distros (g++-9/10) are not enough.

### Route A — Docker (no local C++ toolchain)

Use the helper script. It clones the published tag, builds inside an older base
image with g++-13 from the `ubuntu-toolchain-r` PPA, statically links libstdc++,
and writes the `.so` to `./native` (override with `OUT_DIR`):

```bash
# single-objective, default Ubuntu 20.04 base, output into the examples project
OUT_DIR="$PWD/examples/native" scripts/build_brkga_native.sh

# Other knobs (all optional env vars). This builds objective counts 1, 2 and 3,
# adds the generic bridge, and targets the oldest supported PPA base:
TUPLE_NS="1 2 3" \
GENERIC=1 \
IMAGE=ubuntu:18.04 \
    scripts/build_brkga_native.sh
```

Then either:

- put the output in **`examples/native/`** (the default when you run the examples
  from `examples/`): [`run.sh`](../../examples/run.sh) picks it up automatically,
  no flags needed; **or**
- point your own app at it explicitly:

  ```bash
  java --enable-native-access=ALL-UNNAMED -Dbrkga.bridge.dir=/path/to/native ...
  ```

  A file in `-Dbrkga.bridge.dir` wins over the same bundled file. If a requested
  bridge is absent from that directory, the loader falls back to the JAR.

### Route B — local build, no Docker (the most portable)

Building on the old host itself links against exactly that host's glibc, which is
the surest match. You need g++ 13 + make + OpenMP on that host:

```bash
git submodule update --init          # fetch the upstream headers
make -C brkga_mp_ipr_java/native tuples TUPLE_NS="1" \
    CXX="g++-13 -static-libstdc++ -static-libgcc"
```

Copy the resulting `libbrkga_bridge_*.so` next to your working directory as
`native/…`, or point `-Dbrkga.bridge.dir` at them.

### Notes

- **Multiple objectives.** `TUPLE_NS="1"` only builds the single-objective
  binary. For `Brkga.forObjectives(2)` or `(3)`, the loader would then fall back
  to the corresponding binary bundled in the JAR, which still cannot load on
  the older host. Build every count you need (`TUPLE_NS="1 2 3"`) or add
  `GENERIC=1` for a runtime-selectable local binary.
- **Remaining runtime dependency.** The binaries still need OpenMP at run time
  (`libgomp.so.1`, from the `libgomp1` package) — that is a small, universally
  available shared library, not a GLIBCXX-versioned one. The script's final
  `ldd` check reports any unresolved dependency.

---

## Keeping extra data in the chromosome

The `Chromosome` class can carry an inline *extra blob* — a few auxiliary
`double`s that travel with each chromosome, separate from the genes — so the
decoder can store per-solution metrics (e.g. makespan, total completion time)
and read them back later. The user-facing API and the all-important
**consistency/staleness rules** are documented in
[GUIDE.md](GUIDE.md#keeping-extra-data-in-the-chromosome). This section only
covers the wrapper-specific part: **how big the blob is and how to resize it.**

The capacity is fixed at **compile time** and is uniform across every bridge
binary, so the memory layout the Java side reads is identical regardless of the
objective count (the same reason the status struct reserves `CAP` objectives).
The default is **8 doubles** per chromosome. At run time, `algo.extraCapacity()`
(or `chromosome.extraSize()`) reports it.

To change it, recompile the native libraries with a different `EXTRA_CAP` (via
Docker or a local `make`) — see
[Recompiling the native library](#recompiling-the-native-library), e.g.
`-e EXTRA_CAP=16`. Point the JVM at the result with `-Dbrkga.bridge.dir=...`. No
Java change is needed; `extraCapacity()` will report the new value.

Notes:

- The blob lives in each chromosome object as a fixed-size array
  ([`native/custom_chromosome.hpp`](../native/custom_chromosome.hpp)), so it costs
  `EXTRA_CAP × 8` bytes per chromosome (e.g. 64 bytes with the default of 8) — a
  negligible amount next to the gene buffer for any realistic population. The hot
  path is unaffected: the decode boundary just passes one extra pointer, and the
  Java view of the blob is built lazily only if the decoder actually touches it.
- `EXTRA_CAP` may be `0` if you never use the feature (the accessors then report
  capacity `0`).
- The `tests_reference` program and the benchmark's `bench_native` baseline use
  the stock `std::vector<double>` chromosome and ignore `EXTRA_CAP` entirely.

---

## Writing a fast decoder

The decoder shown in [GUIDE.md](GUIDE.md#back-to-the-decoder) — and the one the
examples use ([`brkga.tsp.single.TspDecoder`](../src/main/java/brkga/tsp/single/TspDecoder.java))
— is written to be **read and understood**, not to be maximally fast. It mirrors
the C++ example: build an `Integer[]` permutation, sort it with a `Comparator`,
and sum the edge costs.

Since the decoder is the hot path (it is called once per chromosome, every
generation, in parallel), the repository also ships an additional, optimized
variant with **identical results**,
[`brkga.tsp.single.TspDecoderOptimized`](../src/main/java/brkga/tsp/single/TspDecoderOptimized.java).
This is the same idea the C++ suite illustrates with its
`tsp_decoder_pre_allocating` variant. It applies two optimizations:

1. **Reuse a per-thread buffer (no per-decode allocation).** The basic decoder
   allocates a fresh `Integer[]` on every call; under multi-threaded decoding
   that is constant allocation and GC pressure on the hot path. The optimized
   version keeps a `ThreadLocal<long[]>` and reuses it across calls, so the
   scratch buffer is allocated once per thread:

   ```java
   private final ThreadLocal<long[]> packedPerThread =
       ThreadLocal.withInitial(() -> new long[n]);
   ```

   A `ThreadLocal` is the right tool because the library decodes from several
   OpenMP threads at once, and each must have its own scratch (see
   [GUIDE.md](GUIDE.md#multi-thread-decoding)).

2. **Sort primitives, not boxed objects.** Sorting an `Integer[]` with a
   `Comparator` boxes every index and calls back into the comparator for each
   comparison. Instead, pack each gene together with its node index into a single
   `long` and sort a primitive `long[]` with `Arrays.sort` — no boxing, no
   callbacks, branch-free comparisons:

   ```java
   // High bits: the gene's IEEE-754 bit pattern (monotonic for keys in [0,1));
   // low `indexBits` bits: the node index. One ascending sort orders by gene and
   // breaks ties by index, exactly as a std::sort over (key, index) pairs would.
   long bits = Double.doubleToRawLongBits(chromosome.get(i));
   packed[i] = (bits & keyMask) | i;
   // ...
   Arrays.sort(packed, 0, size);
   ```

   This works because the keys are non-negative doubles in `[0, 1)`, whose raw
   bit patterns sort in the same order as the values. Reserving the low bits for
   the index makes the tie-breaking deterministic (and identical to the C++
   reference, which is what the equivalence test checks).

Both decoders are validated to produce the same tours; the optimized one is what
the [benchmark](WRAPPER.md#performance) uses. Use the basic decoder while
developing and learning, and reach for techniques like these only once profiling
shows the decoder is the bottleneck.

---

## Performance

The **entire BRKGA core runs in the same native code** a pure C++ program would
use, so it runs at native speed. The only added cost is the objective evaluation
(*decode*), which crosses into the JVM once per chromosome (an FFM upcall plus the
per-gene reads through `Chromosome`).

- With a **basic decoder** (boxed objects, a comparator, per-call allocation), the
  overhead grows with the decode size — in the benchmark from about +11 % on a
  14-node instance to +76 % on a 1084-node one — because those JVM-side costs are
  paid per gene, per decode.
- With an **optimized decoder** (reused per-thread buffer, primitive packed sort,
  no boxing), the overhead almost disappears — **−2 % to +12 %, roughly par with
  native C++** — and absolute time drops too.

So the boundary itself is cheap; what matters is keeping the decoder efficient:
read genes in bulk and avoid boxing/allocation on the hot path (see
[Writing a fast decoder](#writing-a-fast-decoder)). The full methodology, the
both-decoder results table and how to reproduce are in
[WRAPPER.md](WRAPPER.md#performance) (run it yourself with `bench/bench.py`).

---

## Reproducibility and threads

- **Determinism.** With the same seed, a single thread, and a fixed iteration
  count, the Java edition produces results identical to the native C++ reference
  (verified by the test suite). Use `BRKGA_NUM_THREADS=1` for a deterministic run.
- **Decoder thread-safety.** When using more than one thread, your decoder **must
  be thread-safe** (see [GUIDE.md](GUIDE.md#multi-thread-decoding)).
- **Mating parallelism.** Whether the mating (crossover) runs in parallel is a
  build-time setting of the native library, chosen by a compile flag:
  - `MATING_SEQUENTIAL` (the default here): no parallel mating; fully reproducible
    for a given seed and comparable, allele-for-allele, with the C++ reference.
  - `MATING_SEED_ONLY`: parallel mating, reproducible from the seed alone.
  - `MATING_FULL_SPEED`: fastest, but reproducibility depends on **both** the seed
    **and** the number of threads.

  To change it, recompile with a different `MATING` value (e.g.
  `-e MATING=MATING_FULL_SPEED`) — see
  [Recompiling the native library](#recompiling-the-native-library). Details in
  [WRAPPER.md](WRAPPER.md#multi-threading-and-reproducibility).

---

## Behavioral differences from the C++ edition

These are user-visible differences from the original C++ examples (not bugs):

- **Warm start** (`MainComplete`): the C++ code draws the initial chromosome's
  keys with `std::mt19937`; Java uses `java.util.Random`. The injected **tour**
  (the greedy solution) is identical — only the relative order of the keys matters
  — but the concrete gene values differ, which may slightly affect later
  crossover. See [`brkga.tsp.WarmStart`](../src/main/java/brkga/tsp/WarmStart.java).
- **Time formatting**: durations are printed with an `s` suffix, with a
  floating-point format that may differ slightly from libstdc++.

The validation against the C++ reference (and the test suite) is described in
[WRAPPER.md](WRAPPER.md#testing-and-validation).
