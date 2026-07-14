#!/usr/bin/env bash
###############################################################################
# run.sh: compiles (if needed) and runs one of the BRKGA-MP-IPR Java examples.
#
# It fetches the published wrapper artifact (with its bundled native binaries)
# from Maven Central, so NO C++ toolchain is required — only a JDK 25+ and Maven.
#
# Usage:
#   ./run.sh <main-class> <args...>
#
# Examples:
#   ./run.sh brkga.single.MainMinimal 27000001 \
#       data/config/single.conf 10 data/instances/burma14.dat
#
#   ./run.sh brkga.single.MainComplete \
#       --config data/config/single.conf --seed 27000001 \
#       --stop_rule G --stop_arg 100 --maxtime 30 \
#       --instance data/instances/brazil58.dat --threads 4
#
#   ./run.sh brkga.multi.MainMinimal 27000001 \
#       data/config/multi.conf 10 data/instances/brazil58.dat
#
# Optional: BRKGA_NUM_THREADS overrides the thread count for the
# minimal/maximum-iterations examples (default 4).
###############################################################################
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"

if [ $# -lt 1 ]; then
    echo "Usage: ./run.sh <main-class> <args...>" >&2
    exit 1
fi

MAIN_CLASS="$1"; shift

# Compile and resolve the runtime classpath (the wrapper jar + its deps).
mvn -q compile
CP="$(mvn -q dependency:build-classpath -DincludeScope=runtime \
        -Dmdep.outputFile=/dev/stdout 2>/dev/null | tail -n1)"

NUM_THREADS="${BRKGA_NUM_THREADS:-4}"

exec java --enable-native-access=ALL-UNNAMED \
    -Dbrkga.num_threads="$NUM_THREADS" \
    -cp "target/classes:$CP" \
    "$MAIN_CLASS" "$@"
