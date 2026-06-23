# BRKGA-MP-IPR for Java

Java edition of **BRKGA-MP-IPR** (Multi-Parent Biased Random-Key Genetic
Algorithm with Implicit Path Relinking). It reuses the original C++ library
**unmodified**: the optimization core runs natively and you only write Java (a
*decoder* and your problem data). The public API (the `brkga` package) exposes no
low-level native-interop details.

## Documentation

- **[docs/GUIDE.md](docs/GUIDE.md)** — usage guide / tutorial for the algorithm.
  A faithful Java port of the official C++ guide. **Start here.**
- **[docs/JAVA_GUIDE.md](docs/JAVA_GUIDE.md)** — concerns specific to the Java
  edition that matter to users: building and running, platform (Linux only),
  number of objectives and how to build dedicated binaries, runtime flags, and
  high-level performance overhead.
- **[docs/WRAPPER.md](docs/WRAPPER.md)** — wrapper implementation details:
  architecture, the C++ bridge, the FFM binding, design decisions, the full
  benchmark, known issues and next steps.
- **API reference (JavaDoc)** — in the source under
  [`src/main/java/brkga/`](src/main/java/brkga). Generate the HTML with:
  `javadoc -d docs/api --release 22 -sourcepath src/main/java -subpackages brkga`

## Quick start

```bash
mvn compile
./run.sh brkga.single.MainMinimal 27000001 \
  ../cpp/brkga_mp_ipr_cpp/examples/tsp/src/single_obj/config.conf 10 \
  ../cpp/brkga_mp_ipr_cpp/examples/tsp/instances/burma14.dat
```

Build, run and example details are in [docs/JAVA_GUIDE.md](docs/JAVA_GUIDE.md).
