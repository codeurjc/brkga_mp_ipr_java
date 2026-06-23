/******************************************************************************
 * tsp_decoder_optimized.cpp: implementation of the optimized TSP decoder.
 *****************************************************************************/

#include "tsp_decoder_optimized.hpp"

#include <algorithm>
#include <bit>
#include <vector>

TSPDecoderOptimized::TSPDecoderOptimized(const TSPInstance& inst):
    instance(inst)
{
    // Bits needed to hold a node index in [0, num_nodes). Matches the Java side:
    //   indexBits = max(1, 32 - numberOfLeadingZeros(max(1, n - 1))).
    unsigned n = inst.num_nodes;
    unsigned index_bits = 1;
    while((1u << index_bits) < n)
        ++index_bits;
    index_mask = (std::uint64_t(1) << index_bits) - 1;
    key_mask = ~index_mask;
}

BRKGA::fitness_t TSPDecoderOptimized::decode(BRKGA::Chromosome& chromosome,
                                             bool /* rewrite, unused */) {
    const std::size_t n = chromosome.size();

    // Reused across calls within each (OpenMP) thread: no per-decode allocation.
    static thread_local std::vector<std::uint64_t> packed;
    if(packed.size() < n)
        packed.resize(n);

    for(std::size_t i = 0; i < n; ++i) {
        std::uint64_t bits = std::bit_cast<std::uint64_t>(chromosome[i]);
        packed[i] = (bits & key_mask) | i;
    }

    std::sort(packed.begin(), packed.begin() + n);

    unsigned first = static_cast<unsigned>(packed[0] & index_mask);
    unsigned last = static_cast<unsigned>(packed[n - 1] & index_mask);
    double cost = instance.distance(first, last);

    for(std::size_t i = 0; i < n - 1; ++i)
        cost += instance.distance(
            static_cast<unsigned>(packed[i] & index_mask),
            static_cast<unsigned>(packed[i + 1] & index_mask));

    return cost;
}
