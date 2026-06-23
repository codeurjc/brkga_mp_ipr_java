#!/usr/bin/env bash
###############################################################################
# build-native.sh: entrypoint for docker/Dockerfile.build. Compiles the native
# bridge libraries with the options given via environment variables and copies
# the resulting .so files to /out (a mounted volume).
###############################################################################
set -euo pipefail

: "${MATING:=MATING_SEQUENTIAL}"
: "${TUPLE_NS:=1 2 3 4 5}"
: "${EXTRA_CAP:=8}"
: "${CAP:=20}"

echo "Building native bridges with:"
echo "  MATING=$MATING  TUPLE_NS='$TUPLE_NS'  EXTRA_CAP=$EXTRA_CAP  CAP=$CAP"

make clean
make all \
    MATING="$MATING" \
    TUPLE_NS="$TUPLE_NS" \
    EXTRA_CAP="$EXTRA_CAP" \
    CAP="$CAP"

mkdir -p /out
cp -v ./*.so /out/
echo "Done. Native libraries are in the mounted ./out directory."
echo "Run your app with:  -Dbrkga.bridge.dir=<path-to-out>"
