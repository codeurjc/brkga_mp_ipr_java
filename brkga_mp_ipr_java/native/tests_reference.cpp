/******************************************************************************
 * tests_reference.cpp: native C++ reference for the Java/C++ equivalence tests.
 *
 * Runs a deterministic single-objective scenario (fixed seed, given thread
 * count) for a fixed number of iterations and prints key results as parseable
 * `key=value` lines. The Java EquivalenceTest runs the SAME scenario through
 * the wrapper and asserts identical values.
 *
 * The setup mirrors MainMaximumIterations: default control params (IPR / shake
 * / exchange / reset disabled), unbounded time/stall, stop at `iterations`.
 *
 * Not part of the library or the FFM port; it uses the example's TSP_Decoder
 * and the same BRKGA core the bridge compiles.
 *
 * Usage: <seed> <config-file> <iterations> <tsp-instance-file> [num_threads]
 *****************************************************************************/

#include "tsp/tsp_instance.hpp"
#include "decoders/tsp_decoder.hpp"
#include "brkga_mp_ipr.hpp"

#include <chrono>
#include <cstdio>
#include <limits>
#include <string>

using namespace std;

int main(int argc, char* argv[]) {
    if(argc < 5) {
        fprintf(stderr, "Usage: %s <seed> <config> <iterations> <instance> "
                        "[num_threads]\n", argv[0]);
        return 1;
    }
    const unsigned seed = stoi(argv[1]);
    const string config_file = argv[2];
    const unsigned iterations = stoi(argv[3]);
    const string instance_file = argv[4];
    const unsigned num_threads = (argc > 5) ? stoi(argv[5]) : 1;

    auto instance = TSP_Instance(instance_file);
    auto [brkga_params, _] = BRKGA::readConfiguration(config_file);

    BRKGA::ControlParams control_params;
    control_params.maximum_running_time = chrono::seconds::max();
    control_params.stall_offset = numeric_limits<unsigned>::max();

    TSP_Decoder decoder(instance);
    BRKGA::BRKGA_MP_IPR<TSP_Decoder> algorithm(
        decoder, BRKGA::Sense::MINIMIZE, seed, instance.num_nodes,
        brkga_params, num_threads);

    algorithm.setStoppingCriteria(
        [&](const BRKGA::AlgorithmStatus& s) {
            return s.current_iteration == iterations;
        });

    const auto st = algorithm.run(control_params, nullptr);

    // Deterministic, comparable outputs.
    printf("best_fitness=%.10g\n", st.best_fitness);
    printf("current_iteration=%u\n", st.current_iteration);
    printf("last_update_iteration=%u\n", st.last_update_iteration);
    printf("largest_iteration_offset=%u\n", st.largest_iteration_offset);
    printf("stalled_iterations=%u\n", st.stalled_iterations);
    printf("elite_size=%u\n", algorithm.getEliteSize());
    printf("num_mutants=%u\n", algorithm.getNumMutants());
    printf("chromosome_size=%u\n", algorithm.getChromosomeSize());
    // First gene of the best chromosome (sanity on solution retrieval).
    printf("best_gene0=%.10g\n", algorithm.getBestChromosome()[0]);
    return 0;
}
