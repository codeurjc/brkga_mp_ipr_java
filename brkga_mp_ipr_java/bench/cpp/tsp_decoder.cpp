/******************************************************************************
 * tsp_decoder.cpp: implementation of the benchmark's minimal TSP decoder.
 *
 * Builds the node permutation induced by the chromosome (nodes sorted by their
 * gene/key) and returns the tour cost.
 *****************************************************************************/

#include "tsp_decoder.hpp"

#include <algorithm>
#include <utility>
#include <vector>

BRKGA::fitness_t TSPDecoder::decode(BRKGA::Chromosome& chromosome,
                                    bool /* rewrite, unused */) {
    std::vector<std::pair<double, unsigned>> permutation(instance.num_nodes);
    for(unsigned i = 0; i < instance.num_nodes; ++i)
        permutation[i] = std::make_pair(chromosome[i], i);

    std::sort(permutation.begin(), permutation.end());

    double cost = instance.distance(permutation.front().second,
                                    permutation.back().second);
    for(unsigned i = 0; i < instance.num_nodes - 1; ++i)
        cost += instance.distance(permutation[i].second,
                                  permutation[i + 1].second);

    return cost;
}
