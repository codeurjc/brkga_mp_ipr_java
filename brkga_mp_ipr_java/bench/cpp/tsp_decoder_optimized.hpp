/******************************************************************************
 * tsp_decoder_optimized.hpp: optimized single-objective TSP decoder.
 *
 * Same RESULT as tsp_decoder, but tuned for the hot path with the very same
 * technique as the Java bench.TspDecoderOptimized, so the two stay equivalent:
 *
 *   1. A reusable per-thread buffer (thread_local), so no allocation per decode.
 *   2. A primitive packed sort: each gene's IEEE-754 bit pattern (monotonic for
 *      the non-negative keys in [0,1)) is packed in the high bits and the node
 *      index in the low `index_bits` bits, so a single ascending sort of a
 *      uint64_t array orders by gene and breaks ties by index — no pairs, no
 *      comparator, cache-friendly.
 *****************************************************************************/

#ifndef BENCH_TSP_DECODER_OPTIMIZED_HPP_
#define BENCH_TSP_DECODER_OPTIMIZED_HPP_

#include "tsp_instance.hpp"
// Only the chromosome/fitness types (see tsp_decoder.hpp for why not the full header).
#include "fitness_type.hpp"
#include "chromosome.hpp"

#include <cstdint>

class TSPDecoderOptimized {
public:
    explicit TSPDecoderOptimized(const TSPInstance& instance);

    BRKGA::fitness_t decode(BRKGA::Chromosome& chromosome, bool rewrite);

    const TSPInstance& instance;

private:
    std::uint64_t index_mask;
    std::uint64_t key_mask;
};

#endif // BENCH_TSP_DECODER_OPTIMIZED_HPP_
