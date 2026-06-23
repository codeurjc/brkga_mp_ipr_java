#!/usr/bin/env python3
"""Decode-overhead benchmark: Java/FFM vs all-native C++.

Self-contained in this `bench/` directory: it uses the benchmark's own minimal
TSP code (cpp/ and java/, algorithmically equivalent) and the stock BRKGA-MP-IPR
core, so the comparison isolates the Java/FFM decode-boundary overhead. The only
external input reused is the library's TSP instances.

It builds everything it needs, then runs the single-objective TSP optimization
for a fixed number of iterations on several instance sizes, at 1 thread, several
repetitions each, and reports the best run time and the Java-over-C++ overhead.

Usage:  python3 bench.py [reps]
"""
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent          # .../brkga_mp_ipr_java/bench
WRAPPER = HERE.parent                            # .../brkga_mp_ipr_java
REPO = WRAPPER.parent                            # repo root

CONFIG = HERE / "config.conf"
OUT = HERE / "out"
WRAP_CLASSES = WRAPPER / "target" / "classes"
NATIVE_DIR = WRAPPER / "native"
INST = REPO / "third_party/brkga_mp_ipr_cpp/examples/tsp/instances"

SEED = "27000001"
THREADS = "1"

# (instance, iterations) tuned so each native run takes a few seconds at 1 thread.
CASES = [
    ("burma14.dat", 500),
    ("brazil58.dat", 200),
    ("rd400.dat", 35),
    ("vm1084.dat", 10),
]

TIME_RE = re.compile(r"time=([\d.]+) ms")
BEST_RE = re.compile(r"best=([\d.]+)")


def build():
    """Build the native baseline, the wrapper (bridge .so + classes) and the
    Java benchmark classes."""
    print("Building native baseline ...", flush=True)
    subprocess.run(["make"], cwd=HERE, check=True)
    print("Building wrapper (mvn compile) ...", flush=True)
    subprocess.run(["mvn", "-q", "compile"], cwd=WRAPPER, check=True)
    print("Compiling Java benchmark ...", flush=True)
    OUT.mkdir(exist_ok=True)
    sources = [str(p) for p in (HERE / "java").glob("*.java")]
    subprocess.run(["javac", "-cp", str(WRAP_CLASSES), "-d", str(OUT)] + sources,
                   check=True)


def run(cmd):
    out = subprocess.run(cmd, cwd=HERE, capture_output=True, text=True)
    lines = (out.stdout + out.stderr).strip().splitlines()
    line = next(l for l in reversed(lines) if "time=" in l)
    return float(TIME_RE.search(line).group(1)), float(BEST_RE.search(line).group(1))


def java_cmd(inst, iters, decoder):
    return [
        "java", "--enable-native-access=ALL-UNNAMED",
        f"-Dbrkga.bridge.dir={NATIVE_DIR}", f"-Dbrkga.num_threads={THREADS}",
        f"-Dbrkga.bench.decoder={decoder}",
        "-cp", f"{WRAP_CLASSES}:{OUT}",
        "bench.Benchmark", SEED, str(CONFIG), str(iters), str(INST / inst),
    ]


def cpp_cmd(inst, iters, decoder):
    return [str(HERE / "bench_native"), SEED, str(CONFIG), str(iters),
            str(INST / inst), THREADS, decoder]


def best_time(cmd, reps):
    """Minimum over reps (least system interference => most reproducible)."""
    results = [run(cmd) for _ in range(reps)]
    return min(t for t, _ in results), results[0][1]


def main():
    reps = int(sys.argv[1]) if len(sys.argv) > 1 else 5
    build()
    print(f"\nreps={reps}, threads={THREADS}, seed={SEED}")
    print("times in ms (only run()); overhead = java/cpp - 1\n")
    header = (f"{'instance':<12} {'nodes':>5} {'iters':>6} | "
              f"{'java_basic':>10} {'cpp_basic':>9} {'ovh':>6} | "
              f"{'java_opt':>9} {'cpp_opt':>8} {'ovh':>6}")
    print(header)
    print("-" * len(header))
    for inst, iters in CASES:
        if not (INST / inst).exists():
            print(f"{inst:<12} (missing — skipped)")
            continue
        nodes = int((INST / inst).read_text().split()[0])
        jb, _ = best_time(java_cmd(inst, iters, "basic"), reps)
        cb, _ = best_time(cpp_cmd(inst, iters, "basic"), reps)
        jo, _ = best_time(java_cmd(inst, iters, "optimized"), reps)
        co, _ = best_time(cpp_cmd(inst, iters, "optimized"), reps)
        ob = (jb / cb - 1.0) * 100.0
        oo = (jo / co - 1.0) * 100.0
        print(f"{inst:<12} {nodes:>5} {iters:>6} | "
              f"{jb:>9.0f}m {cb:>8.0f}m {ob:>+5.0f}% | "
              f"{jo:>8.0f}m {co:>7.0f}m {oo:>+5.0f}%")


if __name__ == "__main__":
    main()
