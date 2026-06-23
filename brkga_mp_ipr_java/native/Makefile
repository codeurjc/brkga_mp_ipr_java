###############################################################################
# Makefile: builds the C-ABI bridges over the header-only BRKGA-MP-IPR C++
#           library used by the Java/FFM port.
#
# Produces, from a SINGLE source (brkga_bridge.cpp):
#
#   libbrkga_bridge_1.so .. libbrkga_bridge_5.so  -> efficient tuple variants
#                                                    (objective count fixed at
#                                                    compile time)
#   libbrkga_bridge_generic.so                    -> generic variant, any number
#                                                    of objectives up to CAP=20
#                                                    chosen at runtime
#
# All binaries share the same flat status layout (CAP doubles reserved for
# best_fitness), so the Java side is identical and only picks a library by
# performance preference. See README / N_OBJETIVOS.md for adding N > 5.
###############################################################################

CXX = g++

# Untouched header-only library (fitness_t = double here).
BASE_INC = ../../third_party/brkga_mp_ipr_cpp/brkga_mp_ipr

BUILD = build
CAP = 20

# Capacity (in doubles) of the inline per-chromosome "extra" blob (see
# custom_chromosome.hpp). Uniform across all binaries so the Java wire format is
# identical. Raise it if a problem needs more than EXTRA_CAP auxiliary values.
EXTRA_CAP = 8

# Tuple variants to build (efficient, one per objective count).
TUPLE_NS = 1 2 3 4 5

# Parallel-mating mode (reproducibility vs speed). One of:
#   MATING_SEQUENTIAL  - fully sequential; reproducible and allele-for-allele
#                        comparable with the C++ reference (the default here).
#   MATING_SEED_ONLY   - parallel mating, reproducible from the seed alone.
#   MATING_FULL_SPEED  - fastest; reproducibility depends on seed AND thread count.
MATING = MATING_SEQUENTIAL

CXXFLAGS  = -D$(MATING)
CXXFLAGS += -std=c++20
CXXFLAGS += -O3 -fomit-frame-pointer -funroll-loops
CXXFLAGS += -ftracer -fpeel-loops -fprefetch-loop-arrays
CXXFLAGS += -pthread -fopenmp
CXXFLAGS += -fPIC
CXXFLAGS += -DEXTRA_CAP=$(EXTRA_CAP)

.PHONY: all clean tuples generic tests_reference

all: tuples generic

# Upstream example sources, used to build the C++ reference for the equivalence
# tests. (The decode-overhead benchmark is self-contained under ../bench/.)
EXAMPLE = ../../third_party/brkga_mp_ipr_cpp/examples/tsp/src/single_obj

# Native C++ reference for the Java/C++ equivalence tests.
tests_reference: tests_reference.cpp
	@echo "--> tests_reference (native C++ reference for equivalence tests)"
	$(CXX) $(CXXFLAGS) -I$(EXAMPLE) -I$(EXAMPLE)/brkga_mp_ipr \
		tests_reference.cpp \
		$(EXAMPLE)/tsp/tsp_instance.cpp \
		$(EXAMPLE)/decoders/tsp_decoder.cpp \
		-o tests_reference

tuples:
	@for n in $(TUPLE_NS); do \
		inc=$(BUILD)/inc_$$n; \
		mkdir -p $$inc/third_part; \
		cp $(BASE_INC)/brkga_mp_ipr.hpp $(BASE_INC)/chromosome.hpp $$inc/; \
		cp $(BASE_INC)/third_part/enum_io.hpp $$inc/third_part/; \
		cp custom_chromosome.hpp $$inc/chromosome.hpp; \
		if [ $$n -eq 1 ]; then \
			cp $(BASE_INC)/fitness_type.hpp $$inc/fitness_type.hpp; \
		else \
			type="double"; i=2; \
			while [ $$i -le $$n ]; do type="$$type, double"; i=$$((i+1)); done; \
			sed "s|using fitness_t = double;|using fitness_t = std::tuple<$$type>;|" \
				$(BASE_INC)/fitness_type.hpp > $$inc/fitness_type.hpp; \
		fi; \
		echo "--> libbrkga_bridge_$$n.so (tuple, N=$$n)"; \
		$(CXX) $(CXXFLAGS) -DNUM_OBJECTIVES=$$n -DMAX_OBJECTIVES=$(CAP) \
			-I$$inc -shared brkga_bridge.cpp -o libbrkga_bridge_$$n.so; \
	done

generic:
	@inc=$(BUILD)/inc_generic; \
	mkdir -p $$inc/third_part; \
	cp $(BASE_INC)/brkga_mp_ipr.hpp $(BASE_INC)/chromosome.hpp $$inc/; \
	cp $(BASE_INC)/third_part/enum_io.hpp $$inc/third_part/; \
	cp custom_chromosome.hpp $$inc/chromosome.hpp; \
	cp generic_fitness_type.hpp $$inc/fitness_type.hpp; \
	echo "--> libbrkga_bridge_generic.so (generic, CAP=$(CAP))"; \
	$(CXX) $(CXXFLAGS) -DBRKGA_GENERIC -DMAX_OBJECTIVES=$(CAP) \
		-I$$inc -shared brkga_bridge.cpp -o libbrkga_bridge_generic.so

clean:
	rm -rf $(BUILD) *.so tests_reference
