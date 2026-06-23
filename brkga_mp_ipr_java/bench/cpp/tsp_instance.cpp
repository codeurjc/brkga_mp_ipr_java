/******************************************************************************
 * tsp_instance.cpp: implementation of the benchmark's minimal TSP instance.
 *****************************************************************************/

#include "tsp_instance.hpp"

#include <fstream>
#include <stdexcept>
#include <utility>

TSPInstance::TSPInstance(const std::string& filename):
    num_nodes(0),
    distances()
{
    std::ifstream file(filename, std::ios::in);
    if(!file)
        throw std::runtime_error("Cannot open instance file: " + filename);

    file.exceptions(std::ifstream::failbit | std::ifstream::badbit);
    try {
        file >> num_nodes;
        distances.resize((num_nodes * (num_nodes - 1)) / 2);
        for(std::size_t i = 0; i < distances.size(); ++i)
            file >> distances[i];
    }
    catch(std::ifstream::failure&) {
        throw std::runtime_error("Error reading the instance file: " + filename);
    }
}

double TSPInstance::distance(unsigned i, unsigned j) const {
    if(i > j)
        std::swap(i, j);
    return distances[(i * (num_nodes - 1)) - ((i - 1) * i / 2) + (j - i - 1)];
}
