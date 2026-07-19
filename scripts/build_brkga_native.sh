#!/usr/bin/env bash
###############################################################################
# build_brkga_native.sh: build the BRKGA native bridge so it runs on THIS host.
#
# Why this exists: brkga-mp-ipr-java bundles prebuilt libbrkga_bridge_*.so, but
# they are compiled on Ubuntu 24.04 and need GLIBC_2.32 + GLIBCXX_3.4.32. Hosts
# older than that (Ubuntu 20.04 ships glibc 2.31 / GLIBCXX 3.4.28) fail to load
# them with "Cannot open library: /tmp/brkga-native-*/libbrkga_bridge_1.so".
#
# The build runs inside an older base image ($IMAGE, default ubuntu:20.04) so the
# result links against that image's (older) glibc, and it statically links
# libstdc++/libgcc so it does not need a newer GLIBCXX either. Docker is used
# once, here; afterwards the BRKGA runs natively.
#
# The bridge needs a real C++20 library (<concepts>, and operator<< for
# std::chrono::duration), so g++-13 from the ubuntu-toolchain-r PPA is used: the
# distro's g++-9 has no <concepts> and g++-10's libstdc++ lacks the chrono
# inserters. The PPA covers Ubuntu LTS (18.04/20.04/22.04); to target a
# non-Ubuntu / non-Docker host, build directly with `make` on that host instead
# (see JAVA_GUIDE.md "Running on an older Linux").
#
# Output: $OUT_DIR/libbrkga_bridge_*.so (default ./native). The library picks up
# a "native" directory relative to the JVM working directory automatically, so
# running the examples from examples/ finds examples/native/... with no flags;
# for any other layout point the JVM at it with -Dbrkga.bridge.dir=$OUT_DIR.
#
# The temporary clone is removed on exit. Set KEEP_WORK_DIR=1 to retain it for
# debugging; its path is printed as soon as it is created.
#
# Usage (all knobs are optional environment variables):
#   scripts/build_brkga_native.sh
#
#   BRKGA_TAG   git tag to clone (keep in sync with the version you use)  [v0.3.0]
#   IMAGE       older base image to build in                        [ubuntu:20.04]
#   TUPLE_NS    objective counts to build, space-separated                    [1]
#               e.g. TUPLE_NS="1 2 3" for the single + two/three-objective demos
#   GENERIC     set to 1 to also build libbrkga_bridge_generic.so             [0]
#   EXTRA_CAP   inline extra-blob capacity (doubles), uniform across binaries  [8]
#   CAP         ABI capacity; only 20 is accepted (must match Java 0.3.0)     [20]
#   MATING      mating mode (MATING_SEQUENTIAL|MATING_SEED_ONLY|MATING_FULL_SPEED)
#                                                              [MATING_SEQUENTIAL]
#   OUT_DIR     where the .so files are written                       [$PWD/native]
#   KEEP_WORK_DIR  set to 1 to retain the temporary clone                    [0]
#
# Example: build single + two-objective on an Ubuntu 18.04 base, into the
# examples project so ./run.sh finds them automatically:
#   TUPLE_NS="1 2" IMAGE=ubuntu:18.04 OUT_DIR="$PWD/examples/native" \
#       scripts/build_brkga_native.sh
###############################################################################
set -euo pipefail

# Keep BRKGA_TAG in sync with the artifact version you depend on.
BRKGA_TAG="${BRKGA_TAG:-v0.3.0}"
IMAGE="${IMAGE:-ubuntu:20.04}"
TUPLE_NS="${TUPLE_NS:-1}"
GENERIC="${GENERIC:-0}"
EXTRA_CAP="${EXTRA_CAP:-8}"
CAP="${CAP:-20}"
MATING="${MATING:-MATING_SEQUENTIAL}"
OUT_DIR="${OUT_DIR:-$PWD/native}"
KEEP_WORK_DIR="${KEEP_WORK_DIR:-0}"

# Validate everything that is interpolated into the Make invocation before any
# network access or Docker work. CAP is part of BridgeStatus's binary layout and
# therefore must match AlgorithmStatus.CAP in the published Java artifact.
if [ "${CAP}" != "20" ]; then
    echo "CAP=${CAP} is incompatible with the Java 0.3.0 ABI; only CAP=20 is supported." >&2
    echo "Changing CAP requires rebuilding the Java wrapper and AlgorithmStatus layout too." >&2
    exit 2
fi
case "${GENERIC}" in
    0|1) ;;
    *) echo "GENERIC must be 0 or 1 (got '${GENERIC}')." >&2; exit 2 ;;
esac
case "${KEEP_WORK_DIR}" in
    0|1) ;;
    *) echo "KEEP_WORK_DIR must be 0 or 1 (got '${KEEP_WORK_DIR}')." >&2; exit 2 ;;
esac
case "${EXTRA_CAP}" in
    ''|*[!0-9]*|0) echo "EXTRA_CAP must be a positive integer (got '${EXTRA_CAP}')." >&2; exit 2 ;;
esac
case "${MATING}" in
    MATING_SEQUENTIAL|MATING_SEED_ONLY|MATING_FULL_SPEED) ;;
    *) echo "Invalid MATING mode: '${MATING}'." >&2; exit 2 ;;
esac
read -r -a tuple_values <<< "${TUPLE_NS}"
if [ "${#tuple_values[@]}" -eq 0 ]; then
    echo "TUPLE_NS must contain at least one objective count." >&2
    exit 2
fi
for n in "${tuple_values[@]}"; do
    case "${n}" in
        ''|*[!0-9]*) echo "TUPLE_NS contains a non-integer value: '${n}'." >&2; exit 2 ;;
    esac
    if [ "${n}" -lt 1 ] || [ "${n}" -gt 20 ]; then
        echo "TUPLE_NS objective counts must be between 1 and 20 (got '${n}')." >&2
        exit 2
    fi
done
for required_command in git docker readelf ldd; do
    if ! command -v "${required_command}" >/dev/null 2>&1; then
        echo "Required command not found: ${required_command}" >&2
        exit 2
    fi
done

WORK_DIR="$(mktemp -d)"
echo "==> Temporary build tree: ${WORK_DIR}"

cleanup() {
    rc=$?
    trap - EXIT
    if [ "${KEEP_WORK_DIR}" = "1" ]; then
        echo "==> Build tree retained at ${WORK_DIR}"
    elif ! rm -rf -- "${WORK_DIR}"; then
        echo "!!! Could not completely remove ${WORK_DIR}; remove it manually." >&2
    fi
    exit "${rc}"
}
trap cleanup EXIT

echo "==> Cloning brkga_mp_ipr_java ${BRKGA_TAG}"
git clone -q --depth 1 --branch "${BRKGA_TAG}" \
    --recurse-submodules --shallow-submodules \
    https://github.com/codeurjc/brkga_mp_ipr_java.git "${WORK_DIR}/src"

echo "==> Building bridges in ${IMAGE} (g++-13, static libstdc++)"
echo "    TUPLE_NS='${TUPLE_NS}'  GENERIC=${GENERIC}  MATING=${MATING}" \
     "EXTRA_CAP=${EXTRA_CAP}  CAP=${CAP}"
docker run --rm --user root \
    -v "${WORK_DIR}/src":/src -w /src/brkga_mp_ipr_java/native \
    -e TUPLE_NS="${TUPLE_NS}" -e GENERIC="${GENERIC}" -e MATING="${MATING}" \
    -e EXTRA_CAP="${EXTRA_CAP}" -e CAP="${CAP}" \
    -e HOST_UID="$(id -u)" -e HOST_GID="$(id -g)" \
    "${IMAGE}" bash -c '
        set -euo pipefail
        fix_ownership() {
            chown -R "${HOST_UID}:${HOST_GID}" build libbrkga_bridge_*.so 2>/dev/null || true
        }
        trap fix_ownership EXIT
        export DEBIAN_FRONTEND=noninteractive
        apt-get update -qq
        apt-get install -y -qq software-properties-common
        add-apt-repository -y ppa:ubuntu-toolchain-r/test
        apt-get update -qq
        apt-get install -y -qq g++-13 make
        # Statically link libstdc++/libgcc by folding the flags into CXX (the
        # Makefile invokes "$(CXX) $(CXXFLAGS) ... -shared"). This works against
        # any released Makefile and keeps its tuned optimization flags, so the
        # .so carries its own C++ runtime and needs no host GLIBCXX.
        CXX_STATIC="g++-13 -static-libstdc++ -static-libgcc"
        make tuples TUPLE_NS="${TUPLE_NS}" CXX="${CXX_STATIC}" MATING="${MATING}" EXTRA_CAP="${EXTRA_CAP}" CAP="${CAP}"
        if [ "${GENERIC}" = "1" ]; then
            make generic CXX="${CXX_STATIC}" MATING="${MATING}" EXTRA_CAP="${EXTRA_CAP}" CAP="${CAP}"
        fi
    ' >/dev/null

# Stage the fresh build before touching OUT_DIR. Then replace the complete set of
# generated bridge files so stale variants from an earlier run cannot be loaded.
STAGE_DIR="${WORK_DIR}/stage"
mkdir -p "${STAGE_DIR}"
cp "${WORK_DIR}"/src/brkga_mp_ipr_java/native/libbrkga_bridge_*.so "${STAGE_DIR}/"
mkdir -p "${OUT_DIR}"
find "${OUT_DIR}" -maxdepth 1 ! -type d -name 'libbrkga_bridge_*.so' -delete
cp "${STAGE_DIR}"/libbrkga_bridge_*.so "${OUT_DIR}/"

echo "==> Wrote:"
for so in "${OUT_DIR}"/libbrkga_bridge_*.so; do echo "      ${so}"; done

status=0
for so in "${OUT_DIR}"/libbrkga_bridge_*.so; do
    if readelf -d "${so}" 2>/dev/null | grep -Fq 'Shared library: [libstdc++.so.6]'; then
        echo "!!! ${so} still depends directly on libstdc++.so.6; static linking failed." >&2
        status=1
    fi
    if ldd "${so}" 2>&1 | grep -q 'not found'; then
        echo "!!! Unresolved dependencies for ${so} on this host:"
        ldd "${so}" 2>&1 | grep 'not found'
        status=1
    fi
done
if [ "${status}" -ne 0 ]; then
    echo "!!! libgomp.so.1 comes from the libgomp1 package (OpenMP runtime)."
    exit 1
fi
echo "==> All dependencies resolve on this host. BRKGA can now run without Docker."
