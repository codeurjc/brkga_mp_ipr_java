# BRKGA-MP-IPR for Java — examples

Runnable TSP examples for the **BRKGA-MP-IPR** Java edition. They are meant to be
read while following the [user guide](https://github.com/codeurjc/brkga_mp_ipr_java/blob/main/brkga_mp_ipr_java/docs/GUIDE.md).

This is a **standalone Maven project**: it depends on the published
`io.github.codeurjc:brkga-mp-ipr-java` artifact from Maven Central, which ships the
precompiled native binaries. So you need **no C++ compiler** — just:

- **JDK 22 or newer**
- **Maven 3.9+**
- **Linux x86-64** (the platform the bundled binaries target; for other platforms
  or custom builds, see *Recompiling* below)

## Get the examples

Download `brkga_mp_ipr_java_examples.zip` from the
[latest GitHub release](https://github.com/codeurjc/brkga_mp_ipr_java/releases)
and unzip it, or clone the repository and use the `examples/` directory.

## Run

With the helper script (compiles, resolves the classpath, runs with native access on):

```bash
./run.sh brkga.single.MainMinimal 27000001 \
    data/config/single.conf 10 data/instances/burma14.dat
```

Or directly with Maven (prints a harmless native-access warning):

```bash
mvn -q compile exec:java \
    -Dexec.mainClass=brkga.single.MainMinimal \
    -Dexec.args="27000001 data/config/single.conf 10 data/instances/burma14.dat"
```

### Available examples

| Main class | Description |
|---|---|
| `brkga.single.MainMinimal` | single-objective, minimal loop |
| `brkga.single.MainMaximumIterations` | single-objective, fixed iterations |
| `brkga.single.MainComplete` | single-objective, full options (docopt-style) |
| `brkga.multi.MainMinimal` | two-objective, minimal loop |
| `brkga.multi.MainComplete` | two-objective, full options |
| `brkga.multi3.MainMinimal` | synthetic three-objective demo |

`MainComplete` takes named options, e.g.:

```bash
./run.sh brkga.single.MainComplete \
    --config data/config/single.conf --seed 27000001 \
    --stop_rule G --stop_arg 100 --maxtime 30 \
    --instance data/instances/brazil58.dat --threads 4
```

Bundled instances: `burma14.dat` (14 nodes), `brazil58.dat` (58), `rd400.dat`
(400). Larger instances are available in the upstream library repository.

## Decoders: basic vs optimized

- `brkga.tsp.single.TspDecoder` — the simple, readable decoder used throughout the
  guide.
- `brkga.tsp.single.TspDecoderOptimized` — same results, tuned for the hot path
  (the techniques are explained in
  [JAVA_GUIDE.md](https://github.com/codeurjc/brkga_mp_ipr_java/blob/main/brkga_mp_ipr_java/docs/JAVA_GUIDE.md#writing-a-fast-decoder)).

## Recompiling the native library (other objective counts, mating modes, …)

The bundled binaries cover the common cases. To change the number of objectives,
the parallel-mating mode, or the extra-blob capacity, build your own binaries with
Docker (no local toolchain needed) and point the JVM at them with
`-Dbrkga.bridge.dir=...`. See
[JAVA_GUIDE.md → Recompiling the native library](https://github.com/codeurjc/brkga_mp_ipr_java/blob/main/brkga_mp_ipr_java/docs/JAVA_GUIDE.md#recompiling-the-native-library).
