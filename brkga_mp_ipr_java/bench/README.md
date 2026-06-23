# Decode-overhead benchmark (self-contained)

Measures the **Java/FFM decode-boundary overhead** of this wrapper against an
all-native C++ baseline, by running the single-objective TSP optimization for a
fixed number of iterations and comparing wall-clock time.

In the Java path the whole BRKGA core runs in the same native code as the C++
baseline; only the *decode* crosses into the JVM. Both sides use
**algorithmically equivalent** decoders, so the runtime difference is the
boundary overhead, not a decoder mismatch.

Two decoder variants are benchmarked, each shared (same logic) between C++ and
Java:

- **basic** — straightforward: read each gene once, sort the node indices
  (`Integer[]` + comparator in Java; `vector<pair>` + `std::sort` in C++), sum the
  tour.
- **optimized** — tuned hot path: a reusable per-thread buffer (no per-decode
  allocation) and a primitive **packed sort** — each gene's IEEE-754 bits (high)
  packed with the node index (low) into a 64-bit integer, sorted as a primitive
  array (no boxing, no comparator, ties broken by index).

## Self-contained

This directory has everything the benchmark needs, independent of the library's
C++ examples and of the Java `examples/` project, so it can be adapted freely:

```
bench/
├── cpp/                 minimal C++: tsp_instance, tsp_decoder[_optimized], bench_native
├── java/                minimal Java: TspInstance, TspDecoder[Optimized], Benchmark (package `bench`)
├── config.conf          BRKGA parameters (shared by both sides)
├── Makefile             builds the native baseline (bench_native)
└── bench.py             driver: builds everything, runs both decoders, reports
```

The only external input reused is the library's TSP **instances**
(`third_party/brkga_mp_ipr_cpp/examples/tsp/instances/*.dat`). The C++ baseline
links the **stock** BRKGA-MP-IPR headers from the submodule (not the FFM bridge);
the Java side uses the wrapper's published API and bundled native bridge.

## Requirements

JDK 22+, Maven, and g++ with OpenMP (the same toolchain used to build the
wrapper). The driver builds the native baseline, the wrapper, and the Java
benchmark classes itself.

## Run

```bash
cd brkga_mp_ipr_java/bench
python3 bench.py        # default 5 repetitions per case
python3 bench.py 3      # 3 repetitions
```

Run a single case directly (after `bench.py` has built things once, or
`make` + `mvn -q -f .. compile` + `javac -cp ../target/classes -d out java/*.java`).
The last argument / property selects the decoder (`basic` or `optimized`):

```bash
# C++ baseline, optimized decoder  (... <num_threads> <basic|optimized>)
./bench_native 27000001 config.conf 200 \
    ../../third_party/brkga_mp_ipr_cpp/examples/tsp/instances/brazil58.dat 1 optimized

# Java, optimized decoder
java --enable-native-access=ALL-UNNAMED -Dbrkga.bridge.dir=../native \
    -Dbrkga.bench.decoder=optimized -cp "../target/classes:out" \
    bench.Benchmark 27000001 config.conf 200 \
    ../../third_party/brkga_mp_ipr_cpp/examples/tsp/instances/brazil58.dat
```

## Notes

- **1 thread** isolates the FFM boundary from OpenMP thread-attach noise and
  contention.
- Absolute times are machine-dependent — the **ratio** java/cpp is what matters.
- The minimum over repetitions is reported (least affected by system jitter).
