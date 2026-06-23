###############################################################################
# Makefile: builds the all-native C++ baseline (bench_native) for the
# decode-overhead benchmark, from the benchmark's OWN minimal TSP sources and
# the stock (unmodified) BRKGA-MP-IPR headers from the submodule.
#
# The Java side is compiled and run by bench.py (against the wrapper classes).
###############################################################################

CXX = g++

# Stock, unmodified header-only library (fitness_t = double; std::vector<double>
# chromosome). The benchmark baseline uses the library directly, NOT the bridge.
BRKGA_INC = ../../third_party/brkga_mp_ipr_cpp/brkga_mp_ipr

# Same flags / mating mode as the bridge build, so the comparison is fair.
CXXFLAGS  = -DMATING_SEQUENTIAL
CXXFLAGS += -std=c++20
CXXFLAGS += -O3 -fomit-frame-pointer -funroll-loops
CXXFLAGS += -ftracer -fpeel-loops -fprefetch-loop-arrays
CXXFLAGS += -pthread -fopenmp

SRCS = cpp/bench_native.cpp cpp/tsp_instance.cpp cpp/tsp_decoder.cpp \
       cpp/tsp_decoder_optimized.cpp
HDRS = cpp/tsp_instance.hpp cpp/tsp_decoder.hpp cpp/tsp_decoder_optimized.hpp

.PHONY: all clean

all: bench_native

bench_native: $(SRCS) $(HDRS)
	@echo "--> bench_native (all-native C++ baseline)"
	$(CXX) $(CXXFLAGS) -Icpp -I$(BRKGA_INC) $(SRCS) -o bench_native

clean:
	rm -rf bench_native out
