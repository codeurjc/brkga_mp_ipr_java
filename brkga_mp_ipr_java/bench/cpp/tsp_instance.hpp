/******************************************************************************
 * tsp_instance.hpp: minimal, self-contained TSP instance for the benchmark.
 *
 * This is the benchmark's OWN copy, independent of the library's C++ examples,
 * so it can be adapted freely without affecting them. It loads an upper
 * triangular distance matrix:
 *
 *     number of nodes (n)
 *     dist12 dist13 dist14 ... dist1n
 *     dist23 dist24 ... dist2(n-1)
 *     ...
 *     dist(n-2)(n-1)
 *****************************************************************************/

#ifndef BENCH_TSP_INSTANCE_HPP_
#define BENCH_TSP_INSTANCE_HPP_

#include <string>
#include <vector>

class TSPInstance {
public:
    explicit TSPInstance(const std::string& filename);

    /// Distance between nodes i and j.
    double distance(unsigned i, unsigned j) const;

    /// Number of nodes.
    unsigned num_nodes;

    /// Upper-triangular distances, row-major.
    std::vector<double> distances;
};

#endif // BENCH_TSP_INSTANCE_HPP_
