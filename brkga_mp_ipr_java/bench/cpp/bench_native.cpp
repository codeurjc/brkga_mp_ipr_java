/******************************************************************************
 * bench_native.cpp: all-native C++ baseline for the decode-overhead benchmark.
 *
 * Runs the single-objective TSP optimization for a FIXED number of iterations
 * (the same setup as the Java bench.Benchmark), using the benchmark's own
 * minimal TSP instance + decoder and the stock BRKGA-MP-IPR core. Times only
 * the run() call; the progress logger is off.
 *
 * This is a pure C++ program (it does NOT use the FFM bridge); it provides the
 * "all native" reference the Java path is compared against.
 *
 * Usage: <seed> <config-file> <iterations> <tsp-instance-file> [num_threads]
 *****************************************************************************/

#include "tsp_instance.hpp"
#include "tsp_decoder.hpp"
#include "tsp_decoder_optimized.hpp"
#include "brkga_mp_ipr.hpp"

#include <chrono>
#include <cstdio>
#include <iostream>
#include <limits>
#include <string>

using namespace std;

template <class Decoder>
static void run(const TSPInstance& instance, const BRKGA::BrkgaParams& brkga_params,
                unsigned seed, unsigned iterations, unsigned num_threads,
                const char* decoder_label) {
    BRKGA::ControlParams control_params;
    control_params.maximum_running_time = chrono::seconds::max();
    control_params.stall_offset = numeric_limits<unsigned>::max();

    Decoder decoder(instance);
    BRKGA::BRKGA_MP_IPR<Decoder> algorithm(
        decoder, BRKGA::Sense::MINIMIZE, seed,
        instance.num_nodes, brkga_params, num_threads);

    algorithm.setStoppingCriteria(
        [&](const BRKGA::AlgorithmStatus& status) {
            return status.current_iteration == iterations;
        });

    auto t0 = chrono::steady_clock::now();
    const auto status = algorithm.run(control_params, nullptr);
    auto t1 = chrono::steady_clock::now();

    double elapsed_ms = chrono::duration<double, milli>(t1 - t0).count();

    printf("CPP    | decoder=%-9s | threads=%u | nodes=%u | iters=%u "
           "| time=%.1f ms | best=%.0f\n",
           decoder_label, num_threads, instance.num_nodes, iterations,
           elapsed_ms, status.best_fitness);
}

int main(int argc, char* argv[]) {
    if(argc < 5) {
        cerr << "Usage: " << argv[0]
             << " <seed> <config-file> <iterations> <tsp-instance-file>"
             << " [num_threads] [basic|optimized]" << endl;
        return 1;
    }

    const unsigned seed = stoi(argv[1]);
    const string config_file = argv[2];
    const unsigned iterations = stoi(argv[3]);
    const string instance_file = argv[4];
    const unsigned num_threads = (argc > 5) ? stoi(argv[5]) : 1;
    const string decoder = (argc > 6) ? argv[6] : "basic";

    TSPInstance instance(instance_file);
    auto [brkga_params, _] = BRKGA::readConfiguration(config_file);

    if(decoder == "optimized")
        run<TSPDecoderOptimized>(instance, brkga_params, seed, iterations,
                                 num_threads, "optimized");
    else
        run<TSPDecoder>(instance, brkga_params, seed, iterations,
                        num_threads, "basic");
    return 0;
}
