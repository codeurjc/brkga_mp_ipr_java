/******************************************************************************
 * generic_fitness_type.hpp: a CUSTOM fitness_t for the "generic" bridge.
 *
 * Replaces brkga_mp_ipr/fitness_type.hpp when building libbrkga_bridge_generic.
 * Instead of fixing the number of objectives at compile time (as the original
 * `double` / `std::tuple<...>` typedefs do), it stores a fixed-capacity array
 * (FITNESS_CAP) and reads the *active* number of objectives from a runtime
 * global, BRKGA::g_num_objectives. One binary therefore serves any N up to
 * FITNESS_CAP.
 *
 * This is the library's sanctioned extension point: fitness_type.hpp is meant
 * to be replaced by the user, and the library documents that a custom fitness_t
 * must provide operator< / > / ==, operator<<, and FITNESS_T_MIN / MAX. We also
 * supply a threshold-based close_enough() so the IPR equality behaviour matches
 * the tuple variants. brkga_mp_ipr.hpp itself is NOT modified.
 *****************************************************************************/

#ifndef BRKGA_MP_IPR_FITNESS_TYPE_HPP_
#define BRKGA_MP_IPR_FITNESS_TYPE_HPP_

#include <array>
#include <cmath>
#include <iostream>
#include <limits>

namespace BRKGA {

/// Maximum number of objectives this binary can handle.
constexpr unsigned FITNESS_CAP = 20;

/// Active number of objectives (set once before optimizing, process-global).
inline unsigned g_num_objectives = 1;

/// Threshold to compare floating-point objectives for equality.
constexpr double EQUALITY_THRESHOLD = 1e-6;

/**
 * \brief Custom fitness type with a fixed capacity and a runtime arity.
 *
 * Only the first `g_num_objectives` entries are meaningful. Comparisons are
 * lexicographic over those entries.
 */
struct fitness_t {
    std::array<double, FITNESS_CAP> values{};

    constexpr fitness_t() = default;

    bool operator<(const fitness_t& other) const {
        for(unsigned i = 0; i < g_num_objectives; ++i) {
            if(values[i] < other.values[i]) return true;
            if(other.values[i] < values[i]) return false;
        }
        return false;
    }

    bool operator>(const fitness_t& other) const { return other < *this; }

    bool operator==(const fitness_t& other) const {
        for(unsigned i = 0; i < g_num_objectives; ++i)
            if(values[i] != other.values[i]) return false;
        return true;
    }
};

/// Streaming operator: prints "(v0, v1, ...)" over the active objectives.
inline std::ostream& operator<<(std::ostream& os, const fitness_t& f) {
    os << "(";
    for(unsigned i = 0; i < g_num_objectives; ++i) {
        if(i) os << ", ";
        os << f.values[i];
    }
    return os << ")";
}

/// Threshold-based equality (replicates the tuple specialization used by IPR).
inline bool close_enough(const fitness_t& a, const fitness_t& b) {
    for(unsigned i = 0; i < g_num_objectives; ++i)
        if(std::fabs(a.values[i] - b.values[i]) >= EQUALITY_THRESHOLD)
            return false;
    return true;
}

constexpr fitness_t fitness_filled(double v) {
    fitness_t f;
    for(unsigned i = 0; i < FITNESS_CAP; ++i)
        f.values[i] = v;
    return f;
}

/// Minimum/maximum fitness values (every slot filled; comparisons stop at arity).
static constexpr fitness_t FITNESS_T_MIN =
    fitness_filled(std::numeric_limits<double>::lowest());
static constexpr fitness_t FITNESS_T_MAX =
    fitness_filled(std::numeric_limits<double>::max());

} // end namespace BRKGA

#endif // BRKGA_MP_IPR_FITNESS_TYPE_HPP_
