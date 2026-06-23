/******************************************************************************
 * tsp_decoder.hpp: minimal single-objective TSP decoder for the benchmark.
 *
 * The benchmark's OWN copy, kept algorithmically equivalent to the Java
 * bench.TspDecoder so the comparison isolates the decode-boundary overhead
 * (same O(n log n) sort + O(n) tour cost). Independent of the library examples.
 *****************************************************************************/

#ifndef BENCH_TSP_DECODER_HPP_
#define BENCH_TSP_DECODER_HPP_

#include "tsp_instance.hpp"
// Only the chromosome/fitness types are needed here. We deliberately do NOT
// include the full brkga_mp_ipr.hpp: it defines non-inline free functions (e.g.
// operator<< for AlgorithmStatus) that would be emitted in every translation
// unit and clash at link time. Only bench_native.cpp pulls the full header.
#include "fitness_type.hpp"
#include "chromosome.hpp"

class TSPDecoder {
public:
    explicit TSPDecoder(const TSPInstance& instance): instance(instance) {}

    BRKGA::fitness_t decode(BRKGA::Chromosome& chromosome, bool rewrite);

    const TSPInstance& instance;
};

#endif // BENCH_TSP_DECODER_HPP_
