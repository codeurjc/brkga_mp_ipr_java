/******************************************************************************
 * brkga_bridge.cpp: thin C-ABI bridge over the header-only, template-based
 *                   BRKGA-MP-IPR C++ library, so it can be driven from Java
 *                   through the Foreign Function & Memory (FFM) API.
 *
 * This file is NOT part of the original BRKGA-MP-IPR library (which stays
 * untouched). It is the minimal "glue" that:
 *
 *   1. Instantiates the template BRKGA::BRKGA_MP_IPR<ProxyDecoder> in C++.
 *   2. Exposes a set of `extern "C"` functions with a stable C ABI that FFM
 *      can find and call.
 *   3. Forwards each chromosome decoding (and the optional observer / stopping
 *      criteria) to Java through function pointers (FFM upcall stubs).
 *
 * It is compiled in several variants, against different `fitness_type.hpp`:
 *
 *   - Tuple variants (efficient, one per objective count N):
 *       fitness_t = double                       -> NUM_OBJECTIVES = 1
 *       fitness_t = tuple<double, ...x N>         -> NUM_OBJECTIVES = N
 *     The objective count is baked in at compile time (best performance).
 *
 *   - Generic variant (-DBRKGA_GENERIC): fitness_t is a custom fixed-capacity
 *     type with the number of objectives chosen at RUNTIME
 *     (BRKGA::g_num_objectives). One binary serves any N up to MAX_OBJECTIVES,
 *     at the cost of some overhead in the decoding/sorting hot path.
 *
 * All variants share the SAME flat status layout (MAX_OBJECTIVES doubles
 * reserved for best_fitness), so the Java FFM side is identical and only picks
 * a library by performance preference.
 *****************************************************************************/

#include "brkga_mp_ipr.hpp"

#include <algorithm>
#include <cstring>
#include <exception>
#include <iostream>
#include <limits>
#include <new>
#include <sstream>
#include <string>
#include <tuple>
#include <utility>
#include <vector>

#ifndef NUM_OBJECTIVES
#define NUM_OBJECTIVES 1
#endif

// Fixed-size best_fitness buffer in the status struct so the Java memory
// layout (the "wire format") is identical across ALL bridge binaries,
// regardless of the objective count. Override with -DMAX_OBJECTIVES=N.
#ifndef MAX_OBJECTIVES
#define MAX_OBJECTIVES 2
#endif

//----------------------------------------------------------------------------//
// fitness_t <-> double[] conversions (depend on NUM_OBJECTIVES).
//----------------------------------------------------------------------------//

namespace {

#ifdef BRKGA_GENERIC

// Generic bridge: fitness_t is the custom fixed-capacity type whose number of
// objectives is chosen at runtime (BRKGA::g_num_objectives).
inline unsigned current_num_objectives() { return BRKGA::g_num_objectives; }

inline void fitness_to_array(const BRKGA::fitness_t& f, double* out) {
    for(unsigned i = 0; i < BRKGA::g_num_objectives; ++i)
        out[i] = f.values[i];
}
inline BRKGA::fitness_t fitness_from_array(const double* in) {
    BRKGA::fitness_t f;
    for(unsigned i = 0; i < BRKGA::g_num_objectives; ++i)
        f.values[i] = in[i];
    return f;
}

#else  // Tuple bridge: fitness_t fixed at compile time (NUM_OBJECTIVES).

inline unsigned current_num_objectives() { return NUM_OBJECTIVES; }

#if NUM_OBJECTIVES == 1
inline void fitness_to_array(const BRKGA::fitness_t& f, double* out) { out[0] = f; }
inline BRKGA::fitness_t fitness_from_array(const double* in) { return in[0]; }
#else
inline void fitness_to_array(const BRKGA::fitness_t& f, double* out) {
    std::apply([&](auto&&... xs){ int i = 0; ((out[i++] = xs), ...); }, f);
}
template<std::size_t... I>
inline BRKGA::fitness_t fitness_from_array_impl(const double* in,
                                                std::index_sequence<I...>) {
    return BRKGA::fitness_t{ in[I]... };
}
inline BRKGA::fitness_t fitness_from_array(const double* in) {
    return fitness_from_array_impl(in, std::make_index_sequence<NUM_OBJECTIVES>{});
}
#endif

#endif

} // end anonymous namespace

//----------------------------------------------------------------------------//
// Callback types (the Java upcalls).
//----------------------------------------------------------------------------//

extern "C" {

/**
 * Decoder callback. Java fills `out_fitness` (NUM_OBJECTIVES doubles).
 *
 * \param chromosome  pointer to `size` doubles (the genes, in [0, 1]). This is
 *                    the live chromosome buffer: when `rewrite` is non-zero the
 *                    Java decoder may write back into it (the writes are seen by
 *                    BRKGA). When `rewrite` is zero the decoder must not modify it.
 * \param size        number of genes.
 * \param rewrite     0/1; 1 means the decoder may rewrite `chromosome` in place.
 * \param extra       pointer to `extra_size` doubles: the chromosome's inline
 *                    "extra" blob (see custom_chromosome.hpp). The decoder may
 *                    write auxiliary per-solution data here; it travels with the
 *                    chromosome and can be read back later (e.g. for the best
 *                    solution). Writes here never affect the genes or fitness.
 * \param extra_size  capacity of the extra blob (CHROMOSOME_EXTRA_CAP).
 * \param user_data   opaque pointer (reserved; unused here).
 * \param out_fitness out-parameter; the resulting fitness values.
 */
typedef void (*decode_fn)(double* chromosome, size_t size, int rewrite,
                          double* extra, size_t extra_size,
                          void* user_data, double* out_fitness);

// Flat, POD status struct mirroring BRKGA::AlgorithmStatus.
struct BridgeStatus {
    double   best_fitness[MAX_OBJECTIVES]; // only [0..num_objectives) are valid
    double   current_time;                 // chrono::duration<double> -> seconds
    double   last_update_time;
    double   path_relink_time;
    int      num_objectives;
    unsigned current_iteration;
    unsigned last_update_iteration;
    unsigned largest_iteration_offset;
    unsigned stalled_iterations;
    unsigned num_path_relink_calls;
    unsigned num_homogenities;
    unsigned num_best_improvements;
    unsigned num_elite_improvements;
    unsigned num_exchanges;
    unsigned num_shakes;
    unsigned num_resets;
};

/**
 * Observer / stopping-criteria callback. Receives a snapshot of the status
 * and returns an int used as a boolean (its meaning depends on the caller:
 * for the observer, non-zero = keep running; for the stopping criteria,
 * non-zero = stop).
 */
typedef int (*info_fn)(const BridgeStatus* status, void* user_data);

// CUSTOM bias function: weight for a given parent rank.
typedef double (*bias_fn)(unsigned rank, void* user_data);

// CUSTOM distance function (DistanceFunctionBase), as Java upcalls.
typedef double (*distance_fn)(const double* v1, const double* v2, size_t size,
                              void* user_data);
typedef int (*affect_key_fn)(double key1, double key2, void* user_data);
typedef int (*affect_block_fn)(const double* v1, const double* v2,
                               size_t block_size, void* user_data);

// CUSTOM shaking: mutates `populations` and records shaken indices in `shaken`
// (both passed as opaque pointers; manipulated via the brkga_shaking_* helpers).
typedef void (*shaking_fn)(double lower_bound, double upper_bound,
                           void* populations, void* shaken, void* user_data);

// Flat, POD mirror of BRKGA::BrkgaParams (scalar fields only; the functor
// fields pr_distance_function / custom_shaking are handled separately).
// Doubles first, then ints, for clean alignment with the Java layout.
struct BrkgaParamsC {
    double   elite_percentage;
    double   mutants_percentage;
    double   pr_minimum_distance;
    double   alpha_block_size;
    double   pr_percentage;
    double   shaking_intensity_lower_bound;
    double   shaking_intensity_upper_bound;
    unsigned population_size;
    unsigned num_elite_parents;
    unsigned total_parents;
    int      bias_type;
    unsigned num_independent_populations;
    unsigned pr_number_pairs;
    int      pr_type;
    int      pr_selection;
    int      pr_distance_function_type;
    unsigned num_exchange_individuals;
    int      shaking_type;
};

// Flat, POD mirror of BRKGA::ControlParams.
struct ControlParamsC {
    long     maximum_running_time;   // seconds
    unsigned exchange_interval;
    unsigned ipr_interval;
    unsigned shake_interval;
    unsigned reset_interval;
    unsigned stall_offset;
};

} // extern "C"

//----------------------------------------------------------------------------//
// Proxy decoder: forwards BRKGA's decode() call to the Java callback.
//----------------------------------------------------------------------------//

namespace {

class ProxyDecoder {
public:
    ProxyDecoder(decode_fn callback, void* user_data):
        callback(callback), user_data(user_data)
    {}

    BRKGA::fitness_t decode(BRKGA::Chromosome& chromosome, bool rewrite) {
        double out[MAX_OBJECTIVES] = {0.0};
        callback(chromosome.data(), chromosome.size(), rewrite ? 1 : 0,
                 chromosome.extra, BRKGA::CHROMOSOME_EXTRA_CAP,
                 user_data, out);
        return fitness_from_array(out);
    }

private:
    decode_fn callback;
    void* user_data;
};

// Proxy distance function: forwards DistanceFunctionBase calls to Java upcalls.
class ProxyDistance: public BRKGA::DistanceFunctionBase {
public:
    ProxyDistance(distance_fn d, affect_key_fn ak, affect_block_fn ab, void* ud):
        dist(d), affect_key(ak), affect_block(ab), user_data(ud)
    {}

    double distance(const BRKGA::Chromosome& v1,
                    const BRKGA::Chromosome& v2) override {
        return dist(v1.data(), v2.data(), v1.size(), user_data);
    }

    bool affectSolution(const BRKGA::Chromosome::value_type key1,
                        const BRKGA::Chromosome::value_type key2) override {
        return affect_key(key1, key2, user_data) != 0;
    }

    bool affectSolution(BRKGA::Chromosome::const_iterator v1_begin,
                        BRKGA::Chromosome::const_iterator v2_begin,
                        const std::size_t block_size) override {
        return affect_block(&(*v1_begin), &(*v2_begin), block_size, user_data) != 0;
    }

private:
    distance_fn dist;
    affect_key_fn affect_key;
    affect_block_fn affect_block;
    void* user_data;
};

//----------------------------------------------------------------------------//
// Opaque contexts handed back to Java as void*.
//----------------------------------------------------------------------------//

// Holds the parameters parsed from the configuration file.
struct ConfigurationContext {
    BRKGA::BrkgaParams brkga_params;
    BRKGA::ControlParams control_params;
};

// Holds the proxy decoder, the instantiated algorithm, and the last status.
struct AlgorithmContext {
    ProxyDecoder decoder;
    BRKGA::BRKGA_MP_IPR<ProxyDecoder> algorithm;
    BRKGA::AlgorithmStatus last_status;
    bool quiet{false};   // if true, run() uses a null logger (for benchmarks).

    AlgorithmContext(decode_fn cb, void* user_data, BRKGA::Sense sense,
                     unsigned seed, unsigned chromosome_size,
                     const BRKGA::BrkgaParams& params, unsigned max_threads,
                     bool evolution):
        decoder(cb, user_data),
        algorithm(decoder, sense, seed, chromosome_size, params, max_threads,
                  evolution),
        last_status()
    {}
};

// Copies an error message into the caller-provided buffer (truncating safely).
void set_error(char* errbuf, size_t errlen, const std::string& message) {
    if(errbuf == nullptr || errlen == 0)
        return;
    std::strncpy(errbuf, message.c_str(), errlen - 1);
    errbuf[errlen - 1] = '\0';
}

// Copies a std::string into a caller buffer (truncating safely).
void copy_to_buffer(char* buf, size_t len, const std::string& s) {
    if(buf == nullptr || len == 0)
        return;
    std::strncpy(buf, s.c_str(), len - 1);
    buf[len - 1] = '\0';
}

// Fills the flat status struct from a BRKGA::AlgorithmStatus.
void fill_bridge_status(BridgeStatus* out, const BRKGA::AlgorithmStatus& s) {
    out->num_objectives = (int) current_num_objectives();
    for(int i = 0; i < MAX_OBJECTIVES; ++i)
        out->best_fitness[i] = 0.0;
    fitness_to_array(s.best_fitness, out->best_fitness);
    out->current_time             = s.current_time.count();
    out->last_update_time         = s.last_update_time.count();
    out->path_relink_time         = s.path_relink_time.count();
    out->current_iteration        = s.current_iteration;
    out->last_update_iteration    = s.last_update_iteration;
    out->largest_iteration_offset = s.largest_iteration_offset;
    out->stalled_iterations       = s.stalled_iterations;
    out->num_path_relink_calls    = s.num_path_relink_calls;
    out->num_homogenities         = s.num_homogenities;
    out->num_best_improvements    = s.num_best_improvements;
    out->num_elite_improvements   = s.num_elite_improvements;
    out->num_exchanges            = s.num_exchanges;
    out->num_shakes               = s.num_shakes;
    out->num_resets               = s.num_resets;
}

// Builds an IPR distance functor from the distance-function-type enum value,
// mirroring readConfiguration() (HAMMING -> HammingDistance{0.5},
// KENDALLTAU -> KendallTauDistance). Returns nullptr for CUSTOM (set later).
std::shared_ptr<BRKGA::DistanceFunctionBase> make_distance(int dist_type) {
    switch(static_cast<BRKGA::PathRelinking::DistanceFunctionType>(dist_type)) {
        case BRKGA::PathRelinking::DistanceFunctionType::HAMMING:
            return std::shared_ptr<BRKGA::DistanceFunctionBase>(
                new BRKGA::HammingDistance());
        case BRKGA::PathRelinking::DistanceFunctionType::KENDALLTAU:
            return std::shared_ptr<BRKGA::DistanceFunctionBase>(
                new BRKGA::KendallTauDistance());
        default:
            return nullptr;
    }
}

// Unpacks a flat BrkgaParamsC into a BRKGA::BrkgaParams (instantiating the IPR
// distance functor from the type, as readConfiguration does).
void unpack_brkga_params(const BrkgaParamsC* in, BRKGA::BrkgaParams& p) {
    p.population_size              = in->population_size;
    p.elite_percentage            = in->elite_percentage;
    p.mutants_percentage          = in->mutants_percentage;
    p.num_elite_parents           = in->num_elite_parents;
    p.total_parents               = in->total_parents;
    p.bias_type                   = static_cast<BRKGA::BiasFunctionType>(in->bias_type);
    p.num_independent_populations = in->num_independent_populations;
    p.pr_number_pairs             = in->pr_number_pairs;
    p.pr_minimum_distance         = in->pr_minimum_distance;
    p.pr_type                     = static_cast<BRKGA::PathRelinking::Type>(in->pr_type);
    p.pr_selection                = static_cast<BRKGA::PathRelinking::Selection>(in->pr_selection);
    p.pr_distance_function_type   = static_cast<BRKGA::PathRelinking::DistanceFunctionType>(in->pr_distance_function_type);
    p.pr_distance_function        = make_distance(in->pr_distance_function_type);
    p.alpha_block_size            = in->alpha_block_size;
    p.pr_percentage               = in->pr_percentage;
    p.num_exchange_individuals     = in->num_exchange_individuals;
    p.shaking_type                = static_cast<BRKGA::ShakingType>(in->shaking_type);
    p.shaking_intensity_lower_bound = in->shaking_intensity_lower_bound;
    p.shaking_intensity_upper_bound = in->shaking_intensity_upper_bound;
}

// Packs a BRKGA::BrkgaParams into a flat BrkgaParamsC.
void pack_brkga_params(const BRKGA::BrkgaParams& p, BrkgaParamsC* out) {
    out->population_size              = p.population_size;
    out->elite_percentage            = p.elite_percentage;
    out->mutants_percentage          = p.mutants_percentage;
    out->num_elite_parents           = p.num_elite_parents;
    out->total_parents               = p.total_parents;
    out->bias_type                   = static_cast<int>(p.bias_type);
    out->num_independent_populations = p.num_independent_populations;
    out->pr_number_pairs             = p.pr_number_pairs;
    out->pr_minimum_distance         = p.pr_minimum_distance;
    out->pr_type                     = static_cast<int>(p.pr_type);
    out->pr_selection                = static_cast<int>(p.pr_selection);
    out->pr_distance_function_type   = static_cast<int>(p.pr_distance_function_type);
    out->alpha_block_size            = p.alpha_block_size;
    out->pr_percentage               = p.pr_percentage;
    out->num_exchange_individuals     = p.num_exchange_individuals;
    out->shaking_type                = static_cast<int>(p.shaking_type);
    out->shaking_intensity_lower_bound = p.shaking_intensity_lower_bound;
    out->shaking_intensity_upper_bound = p.shaking_intensity_upper_bound;
}

void unpack_control_params(const ControlParamsC* in, BRKGA::ControlParams& c) {
    c.maximum_running_time = std::chrono::seconds{in->maximum_running_time};
    c.exchange_interval    = in->exchange_interval;
    c.ipr_interval         = in->ipr_interval;
    c.shake_interval       = in->shake_interval;
    c.reset_interval       = in->reset_interval;
    c.stall_offset         = in->stall_offset;
}

void pack_control_params(const BRKGA::ControlParams& c, ControlParamsC* out) {
    out->maximum_running_time = c.maximum_running_time.count();
    out->exchange_interval    = c.exchange_interval;
    out->ipr_interval         = c.ipr_interval;
    out->shake_interval       = c.shake_interval;
    out->reset_interval       = c.reset_interval;
    out->stall_offset         = c.stall_offset;
}

} // end anonymous namespace

//----------------------------------------------------------------------------//
// extern "C" API.
//----------------------------------------------------------------------------//

extern "C" {

//---------------------------- Configuration ---------------------------------//

/// Mirrors `BRKGA::readConfiguration(config_file)`.
void* brkga_read_configuration(const char* path, char* errbuf, size_t errlen) {
    try {
        auto [brkga_params, control_params] =
            BRKGA::readConfiguration(std::string(path));
        return new ConfigurationContext{
            std::move(brkga_params), std::move(control_params)
        };
    }
    catch(std::exception& e) {
        set_error(errbuf, errlen, e.what());
        return nullptr;
    }
}

/// Writes `operator<<(brkga_params)` for the given flat params into the buffer.
void brkga_params_to_string(const BrkgaParamsC* in, char* buf, size_t len) {
    BRKGA::BrkgaParams p;
    unpack_brkga_params(in, p);
    std::ostringstream oss;
    oss << p;
    copy_to_buffer(buf, len, oss.str());
}

/// Writes `operator<<(control_params)` for the given flat params into the buffer.
void brkga_control_params_to_string(const ControlParamsC* in, char* buf, size_t len) {
    BRKGA::ControlParams c;
    unpack_control_params(in, c);
    std::ostringstream oss;
    oss << c;
    copy_to_buffer(buf, len, oss.str());
}

/// Releases the configuration context.
void brkga_free_configuration(void* cfg) {
    delete static_cast<ConfigurationContext*>(cfg);
}

//------------------------------ Algorithm -----------------------------------//

/// Mirrors the `BRKGA_MP_IPR<...>` constructor.
/// `sense`: 0 = MINIMIZE, 1 = MAXIMIZE. `evolution`: 0/1.
/// `num_objectives` selects the runtime arity in the generic bridge; it is
/// ignored by the tuple bridges (whose arity is fixed at compile time).
void* brkga_create(void* cfg, int sense, unsigned seed, unsigned num_nodes,
                   unsigned num_threads, int evolution, unsigned num_objectives,
                   decode_fn cb, void* user_data, char* errbuf, size_t errlen) {
#ifdef BRKGA_GENERIC
    BRKGA::g_num_objectives = num_objectives;
#else
    (void) num_objectives;
#endif
    try {
        auto* config = static_cast<ConfigurationContext*>(cfg);
        return new AlgorithmContext(
            cb, user_data,
            sense == 0 ? BRKGA::Sense::MINIMIZE : BRKGA::Sense::MAXIMIZE,
            seed, num_nodes, config->brkga_params, num_threads,
            evolution != 0
        );
    }
    catch(std::exception& e) {
        set_error(errbuf, errlen, e.what());
        return nullptr;
    }
}

/// Mirrors `algorithm.setStoppingCriteria(...)`. `cb` returns non-zero to stop.
void brkga_set_stopping_criteria(void* algo, info_fn cb, void* user_data) {
    auto* ctx = static_cast<AlgorithmContext*>(algo);
    ctx->algorithm.setStoppingCriteria(
        [cb, user_data](const BRKGA::AlgorithmStatus& status) -> bool {
            BridgeStatus bs;
            fill_bridge_status(&bs, status);
            return cb(&bs, user_data) != 0;
        });
}

/// Mirrors `algorithm.addNewSolutionObserver(...)`.
/// `cb` returns non-zero to keep running (false stops the optimization).
void brkga_add_new_solution_observer(void* algo, info_fn cb, void* user_data) {
    auto* ctx = static_cast<AlgorithmContext*>(algo);
    ctx->algorithm.addNewSolutionObserver(
        [cb, user_data](const BRKGA::AlgorithmStatus& status) -> bool {
            BridgeStatus bs;
            fill_bridge_status(&bs, status);
            return cb(&bs, user_data) != 0;
        });
}

/// Enables/disables the run() progress logger (for clean benchmark timing).
void brkga_set_quiet(void* algo, int quiet) {
    static_cast<AlgorithmContext*>(algo)->quiet = (quiet != 0);
}

/// Mirrors `algorithm.setInitialPopulation(...)`.
/// `data` holds `num_chromosomes * chromosome_size` doubles, row-major.
void brkga_set_initial_population(void* algo, const double* data,
                                  size_t num_chromosomes,
                                  size_t chromosome_size) {
    auto* ctx = static_cast<AlgorithmContext*>(algo);
    std::vector<BRKGA::Chromosome> chromosomes;
    chromosomes.reserve(num_chromosomes);
    for(size_t k = 0; k < num_chromosomes; ++k) {
        BRKGA::Chromosome chr(chromosome_size);
        for(size_t i = 0; i < chromosome_size; ++i)
            chr[i] = data[k * chromosome_size + i];
        chromosomes.push_back(std::move(chr));
    }
    ctx->algorithm.setInitialPopulation(chromosomes);
}

/// Mirrors `algorithm.run(control_params, &cout)`.
/// Returns 0 on success, non-zero on error (with message in errbuf).
int brkga_run(void* algo, void* cfg, BridgeStatus* out,
              char* errbuf, size_t errlen) {
    try {
        auto* ctx = static_cast<AlgorithmContext*>(algo);
        auto* config = static_cast<ConfigurationContext*>(cfg);

        ctx->last_status = ctx->algorithm.run(
            config->control_params, ctx->quiet ? nullptr : &std::cout);

        if(out != nullptr)
            fill_bridge_status(out, ctx->last_status);
        return 0;
    }
    catch(std::exception& e) {
        set_error(errbuf, errlen, e.what());
        return 1;
    }
}

/// Copies the best chromosome found in the last run into `out` (size doubles).
/// Returns 0 on success, non-zero if no chromosome is available / size mismatch.
int brkga_get_best_chromosome(void* algo, double* out, size_t size) {
    try {
        // Live getter (mirrors C++ getBestChromosome()): best across the current
        // populations, not the snapshot captured in the last run's status.
        const auto& chr = static_cast<AlgorithmContext*>(algo)
            ->algorithm.getBestChromosome();
        if(chr.size() < size)
            return 1;
        for(size_t i = 0; i < size; ++i)
            out[i] = chr[i];
        return 0;
    }
    catch(std::exception&) {
        return 1;
    }
}

/// Capacity (in doubles) of every chromosome's inline extra blob.
unsigned brkga_extra_capacity() {
    return BRKGA::CHROMOSOME_EXTRA_CAP;
}

/// Copies the inline extra blob of the best chromosome into `out` (size doubles,
/// clamped to the capacity). Returns 0 on success, non-zero if unavailable.
/// NOTE: the blob is the decoder's auxiliary output for that chromosome's genes.
/// It is reliable for chromosomes produced by mating/mutation/initialization
/// (always re-decoded). It can be STALE for a chromosome last written by
/// exchangeElite() or by the path-relinking incorporation step (genes copied
/// without re-decoding). See custom_chromosome.hpp.
int brkga_get_best_chromosome_extra(void* algo, double* out, size_t size) {
    try {
        const auto& chr = static_cast<AlgorithmContext*>(algo)
            ->algorithm.getBestChromosome();
        size_t n = std::min(size, (size_t) BRKGA::CHROMOSOME_EXTRA_CAP);
        for(size_t i = 0; i < n; ++i)
            out[i] = chr.extra[i];
        return 0;
    }
    catch(std::exception&) {
        return 1;
    }
}

/// Copies the inline extra blob of getChromosome(pop, pos) into out[size]
/// (clamped to the capacity). Returns 0, or 1 on error. Same staleness note as
/// brkga_get_best_chromosome_extra.
int brkga_get_chromosome_extra(void* algo, unsigned pop, unsigned pos,
                               double* out, size_t size,
                               char* errbuf, size_t errlen) {
    try {
        const auto& chr = static_cast<AlgorithmContext*>(algo)
            ->algorithm.getChromosome(pop, pos);
        size_t n = std::min(size, (size_t) BRKGA::CHROMOSOME_EXTRA_CAP);
        for(size_t i = 0; i < n; ++i)
            out[i] = chr.extra[i];
        return 0;
    }
    catch(std::exception& e) { set_error(errbuf, errlen, e.what()); return 1; }
}

//------------------- Configuration as Java POJOs ----------------------------//

/// Creates an empty configuration (default BrkgaParams + ControlParams).
void* brkga_config_create() {
    return new ConfigurationContext{};
}

void brkga_config_set_brkga_params(void* cfg, const BrkgaParamsC* in) {
    unpack_brkga_params(in, static_cast<ConfigurationContext*>(cfg)->brkga_params);
}

void brkga_config_get_brkga_params(void* cfg, BrkgaParamsC* out) {
    pack_brkga_params(static_cast<ConfigurationContext*>(cfg)->brkga_params, out);
}

void brkga_config_set_control_params(void* cfg, const ControlParamsC* in) {
    unpack_control_params(in, static_cast<ConfigurationContext*>(cfg)->control_params);
}

void brkga_config_get_control_params(void* cfg, ControlParamsC* out) {
    pack_control_params(static_cast<ConfigurationContext*>(cfg)->control_params, out);
}

/// Mirrors `BRKGA::writeConfiguration(filename, brkga_params, control_params)`.
int brkga_write_configuration(void* cfg, const char* path,
                              char* errbuf, size_t errlen) {
    try {
        auto* ctx = static_cast<ConfigurationContext*>(cfg);
        BRKGA::writeConfiguration(std::string(path), ctx->brkga_params,
                                  ctx->control_params);
        return 0;
    }
    catch(std::exception& e) {
        set_error(errbuf, errlen, e.what());
        return 1;
    }
}

//------------------------- Algorithm operations -----------------------------//

/// Mirrors `algorithm.evolve(generations)`.
int brkga_evolve(void* algo, unsigned generations, char* errbuf, size_t errlen) {
    try {
        static_cast<AlgorithmContext*>(algo)->algorithm.evolve(generations);
        return 0;
    }
    catch(std::exception& e) { set_error(errbuf, errlen, e.what()); return 1; }
}

/// Mirrors the full `algorithm.pathRelink(...)` (built-in distance from type).
/// Returns the PathRelinkingResult value (>= 0), or -1 on error.
int brkga_path_relink(void* algo, int pr_type, int pr_selection, int dist_type,
                      unsigned number_pairs, double minimum_distance,
                      long block_size, long max_time_seconds, double percentage,
                      char* errbuf, size_t errlen) {
    try {
        auto* ctx = static_cast<AlgorithmContext*>(algo);
        // For CUSTOM, reuse the distance functor registered at construction
        // (algorithm's params); otherwise build a built-in HAMMING/KENDALLTAU.
        std::shared_ptr<BRKGA::DistanceFunctionBase> dist =
            (static_cast<BRKGA::PathRelinking::DistanceFunctionType>(dist_type)
                == BRKGA::PathRelinking::DistanceFunctionType::CUSTOM)
            ? ctx->algorithm.getBrkgaParams().pr_distance_function
            : make_distance(dist_type);
        auto result = ctx->algorithm.pathRelink(
            static_cast<BRKGA::PathRelinking::Type>(pr_type),
            static_cast<BRKGA::PathRelinking::Selection>(pr_selection),
            dist, number_pairs, minimum_distance,
            static_cast<std::size_t>(block_size),
            std::chrono::seconds{max_time_seconds}, percentage);
        return static_cast<int>(result);
    }
    catch(std::exception& e) { set_error(errbuf, errlen, e.what()); return -1; }
}

/// Mirrors `algorithm.exchangeElite(num_immigrants)`.
int brkga_exchange_elite(void* algo, unsigned num_immigrants,
                         char* errbuf, size_t errlen) {
    try {
        static_cast<AlgorithmContext*>(algo)->algorithm.exchangeElite(num_immigrants);
        return 0;
    }
    catch(std::exception& e) { set_error(errbuf, errlen, e.what()); return 1; }
}

/// Mirrors `algorithm.shake(intensity, shaking_type, population_index)`.
int brkga_shake(void* algo, unsigned intensity, int shaking_type,
                unsigned population_index, char* errbuf, size_t errlen) {
    try {
        static_cast<AlgorithmContext*>(algo)->algorithm.shake(
            intensity, static_cast<BRKGA::ShakingType>(shaking_type),
            population_index);
        return 0;
    }
    catch(std::exception& e) { set_error(errbuf, errlen, e.what()); return 1; }
}

/// Mirrors `algorithm.reset()`.
void brkga_reset(void* algo) {
    static_cast<AlgorithmContext*>(algo)->algorithm.reset();
}

/// Mirrors `algorithm.injectChromosome(chromosome, population_index, position)`.
int brkga_inject_chromosome(void* algo, const double* chr, unsigned size,
                            unsigned population_index, unsigned position,
                            char* errbuf, size_t errlen) {
    try {
        BRKGA::Chromosome chromosome(chr, chr + size);
        static_cast<AlgorithmContext*>(algo)->algorithm.injectChromosome(
            chromosome, population_index, position);
        return 0;
    }
    catch(std::exception& e) { set_error(errbuf, errlen, e.what()); return 1; }
}

//------------------------------- Getters ------------------------------------//

void brkga_get_best_fitness(void* algo, double* out) {
    fitness_to_array(
        static_cast<AlgorithmContext*>(algo)->algorithm.getBestFitness(), out);
}

/// Copies getChromosome(pop, pos) into out[size]. Returns 0, or 1 on error.
int brkga_get_chromosome(void* algo, unsigned pop, unsigned pos,
                         double* out, unsigned size, char* errbuf, size_t errlen) {
    try {
        const auto& chr = static_cast<AlgorithmContext*>(algo)
            ->algorithm.getChromosome(pop, pos);
        for(unsigned i = 0; i < size && i < chr.size(); ++i) out[i] = chr[i];
        return 0;
    }
    catch(std::exception& e) { set_error(errbuf, errlen, e.what()); return 1; }
}

int brkga_get_fitness(void* algo, unsigned pop, unsigned pos,
                      double* out, char* errbuf, size_t errlen) {
    try {
        fitness_to_array(static_cast<AlgorithmContext*>(algo)
            ->algorithm.getFitness(pop, pos), out);
        return 0;
    }
    catch(std::exception& e) { set_error(errbuf, errlen, e.what()); return 1; }
}

void brkga_get_algo_brkga_params(void* algo, BrkgaParamsC* out) {
    pack_brkga_params(
        static_cast<AlgorithmContext*>(algo)->algorithm.getBrkgaParams(), out);
}

int brkga_get_optimization_sense(void* algo) {
    return static_cast<int>(
        static_cast<AlgorithmContext*>(algo)->algorithm.getOptimizationSense());
}

unsigned brkga_get_chromosome_size(void* algo) {
    return static_cast<AlgorithmContext*>(algo)->algorithm.getChromosomeSize();
}

unsigned brkga_get_elite_size(void* algo) {
    return static_cast<AlgorithmContext*>(algo)->algorithm.getEliteSize();
}

unsigned brkga_get_num_mutants(void* algo) {
    return static_cast<AlgorithmContext*>(algo)->algorithm.getNumMutants();
}

unsigned brkga_get_max_threads(void* algo) {
    return static_cast<AlgorithmContext*>(algo)->algorithm.getMaxThreads();
}

/// Number of chromosomes in the given population (read-only Population view).
unsigned brkga_get_population_size(void* algo, unsigned population_index) {
    return static_cast<AlgorithmContext*>(algo)
        ->algorithm.getCurrentPopulation(population_index).getPopulationSize();
}

//------------------------- CUSTOM callbacks ---------------------------------//

/// Mirrors `algorithm.setBiasCustomFunction(func)`. Sets bias_type to CUSTOM.
int brkga_set_bias_custom_function(void* algo, bias_fn cb, void* user_data,
                                   char* errbuf, size_t errlen) {
    try {
        static_cast<AlgorithmContext*>(algo)->algorithm.setBiasCustomFunction(
            [cb, user_data](const unsigned rank) { return cb(rank, user_data); });
        return 0;
    }
    catch(std::exception& e) { set_error(errbuf, errlen, e.what()); return 1; }
}

/// Registers a CUSTOM IPR distance functor on the configuration (before create).
/// Also sets pr_distance_function_type to CUSTOM.
void brkga_config_set_custom_distance(void* cfg, distance_fn d, affect_key_fn ak,
                                      affect_block_fn ab, void* user_data) {
    auto* ctx = static_cast<ConfigurationContext*>(cfg);
    ctx->brkga_params.pr_distance_function =
        std::make_shared<ProxyDistance>(d, ak, ab, user_data);
    ctx->brkga_params.pr_distance_function_type =
        BRKGA::PathRelinking::DistanceFunctionType::CUSTOM;
}

/// Mirrors `algorithm.setShakingMethod(func)`. `populations`/`shaken` are passed
/// to Java as opaque pointers, manipulated via the brkga_shaking_* helpers.
void brkga_set_shaking_method(void* algo, shaking_fn cb, void* user_data) {
    static_cast<AlgorithmContext*>(algo)->algorithm.setShakingMethod(
        [cb, user_data](double lower, double upper,
                std::vector<std::shared_ptr<BRKGA::Population>>& populations,
                std::vector<std::pair<unsigned, unsigned>>& shaken) {
            cb(lower, upper, &populations, &shaken, user_data);
        });
}

// Helpers operating on the opaque `populations` / `shaken` pointers, valid only
// during a shaking callback.
using PopVec = std::vector<std::shared_ptr<BRKGA::Population>>;
using ShakenVec = std::vector<std::pair<unsigned, unsigned>>;

unsigned brkga_shaking_num_populations(void* populations) {
    return static_cast<PopVec*>(populations)->size();
}

unsigned brkga_shaking_population_size(void* populations, unsigned p) {
    return (*static_cast<PopVec*>(populations))[p]->getPopulationSize();
}

unsigned brkga_shaking_chromosome_size(void* populations, unsigned p) {
    return (*static_cast<PopVec*>(populations))[p]->getChromosomeSize();
}

double brkga_shaking_get_allele(void* populations, unsigned p, unsigned c, unsigned k) {
    return (*(*static_cast<PopVec*>(populations))[p])(c, k);
}

void brkga_shaking_set_allele(void* populations, unsigned p, unsigned c,
                              unsigned k, double value) {
    (*(*static_cast<PopVec*>(populations))[p])(c, k) = value;
}

void brkga_shaking_mark(void* shaken, unsigned p, unsigned c) {
    static_cast<ShakenVec*>(shaken)->push_back({p, c});
}

/// Releases the algorithm context.
void brkga_destroy(void* algo) {
    delete static_cast<AlgorithmContext*>(algo);
}

} // extern "C"
