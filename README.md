# BRKGA-MP-IPR for Java

Java edition of **BRKGA-MP-IPR** (Multi-Parent Biased Random-Key Genetic
Algorithm with Implicit Path Relinking). It reuses the original C++ library
**unmodified** (the optimization core runs natively); you only write Java — a
*decoder* and your problem data. The public API never exposes any low-level
native-interop type, and the published artifact bundles precompiled native
binaries, so **no C++ toolchain is needed to use it**.

## Repository layout

| Path | What it is |
|---|---|
| [`brkga_mp_ipr_java/`](brkga_mp_ipr_java) | The **wrapper** — the Java library published to Maven Central (API, native bridge, tests, docs). |
| [`examples/`](examples) | Standalone **example project** (TSP). Depends on the published artifact; distributed as a zip in each release. |
| [`third_party/brkga_mp_ipr_cpp/`](third_party) | The upstream C++ library, as a pinned **git submodule** (never modified). |
| [`docker/`](docker) | Dockerfiles to recompile the native library or build from source without a local toolchain. |

Clone with the submodule:

```bash
git clone --recurse-submodules <repo-url>
# or, in an existing clone:
git submodule update --init
```

## Use it (no compiler needed)

```xml
<dependency>
    <groupId>io.github.codeurjc</groupId>
    <artifactId>brkga-mp-ipr-java</artifactId>
    <version>0.1.0</version>
</dependency>
```

Run your app with native access enabled:
`java --enable-native-access=ALL-UNNAMED ...`. Linux x86-64 binaries are bundled;
for other platforms or custom builds, see *Recompiling* in the Java guide.

## Documentation

- **[brkga_mp_ipr_java/docs/GUIDE.md](brkga_mp_ipr_java/docs/GUIDE.md)** — usage guide /
  tutorial. A faithful Java port of the official C++ guide. **Start here.**
- **[brkga_mp_ipr_java/docs/JAVA_GUIDE.md](brkga_mp_ipr_java/docs/JAVA_GUIDE.md)** —
  Java-edition concerns: installing via Maven, platform, running the examples,
  number of objectives, recompiling the native library (Docker), performance.
- **[brkga_mp_ipr_java/docs/WRAPPER.md](brkga_mp_ipr_java/docs/WRAPPER.md)** — wrapper
  internals: architecture, the C++ bridge, the FFM binding, design decisions,
  the full benchmark.
- **API reference (JavaDoc)** — generated from
  [`brkga_mp_ipr_java/src/main/java/brkga/`](brkga_mp_ipr_java/src/main/java/brkga).

## Examples

Download `brkga_mp_ipr_java_examples.zip` from the
[releases](../../releases) and unzip it, or use the [`examples/`](examples)
directory. Then:

```bash
./run.sh brkga.single.MainMinimal 27000001 \
    data/config/single.conf 10 data/instances/burma14.dat
```

## Maintainer notes

- Build & test the wrapper: `cd brkga_mp_ipr_java && mvn test` (needs JDK 22+, g++, make)
  — or use [`docker/Dockerfile.dev`](docker/Dockerfile.dev).
- Update the upstream library: `git submodule update --remote third_party/brkga_mp_ipr_cpp`
  and commit the new pointer.
- Publishing to Maven Central and releasing the examples zip are automated by the
  workflows in [`.github/workflows/`](.github/workflows) (see their headers for the
  required secrets; the `io.github.codeurjc` namespace must be claimed on Sonatype
  Central first).
