/******************************************************************************
 * custom_chromosome.hpp: a custom BRKGA::Chromosome that carries an inline,
 *                        POD "extra" blob alongside the genes.
 *
 * Replaces brkga_mp_ipr/chromosome.hpp at build time (the Makefile vendors it
 * into the per-variant build include dirs, exactly as it does with the generic
 * fitness type). This is the library's SANCTIONED extension point: chromosome.hpp
 * documents that Chromosome is an alias precisely so users can add capabilities
 * to it, e.g. keeping extra per-solution metrics. The upstream brkga_mp_ipr.hpp
 * itself is NOT modified.
 *
 * The blob is a fixed-capacity C array of doubles stored as a member of the
 * derived class. Because it is a value-type member, the implicit copy and move
 * operations carry it with the chromosome through the population (elite
 * carry-over, double-buffering, etc.) with no extra bookkeeping and no GC
 * interaction. The genes (the std::vector base) are untouched, so the gene
 * buffer the decoder sees (chromosome.data()) is identical to the stock library
 * and the algorithm behaves exactly as before.
 *
 * IMPORTANT - staleness: the blob is only made consistent with the genes by a
 * decoder call (the decoder is expected to fill it from the genes, just like it
 * returns the fitness). BRKGA decodes every chromosome it creates by mating,
 * mutation or (re)initialization, so those blobs are consistent after evolve().
 * The library copies genes only (via iterators) in exchangeElite() and in the
 * path-relinking incorporation step, without re-decoding, so a slot written by
 * those keeps its previous occupant blob until decoded again. This mirrors the
 * upstream Chromosome-subclass pattern, which has the very same limitation.
 *****************************************************************************/

#ifndef BRKGA_MP_IPR_CHROMOSOME_HPP_
#define BRKGA_MP_IPR_CHROMOSOME_HPP_

// NOTE: deliberately NOT including <array>. Including <array> before the
// library's <chrono> trips a GCC 13 libstdc++ bug in <format> (incomplete
// std::array<_Arg_t,N>). A plain C array needs no extra header and is still
// copied/moved element-wise by the defaulted special members, so it keeps the
// same value semantics we want for the blob.
#include <vector>

// Capacity of the inline extra blob (doubles). Overridable from the Makefile
// with -DEXTRA_CAP=N; kept uniform across all bridge binaries so the Java
// "wire format" is identical regardless of the objective count.
#ifndef EXTRA_CAP
#define EXTRA_CAP 8
#endif

namespace BRKGA {

/// Number of doubles reserved in each chromosome's inline extra blob.
constexpr unsigned CHROMOSOME_EXTRA_CAP = EXTRA_CAP;

/**
 * \brief Chromosome representation: a vector of real-valued genes in [0, 1)
 * plus an inline, fixed-capacity POD "extra" blob for per-solution data.
 *
 * Derives publicly from std::vector<double> (the genes) and adds `extra`, an
 * array of CHROMOSOME_EXTRA_CAP doubles the decoder may use to store auxiliary
 * metrics that travel with the chromosome. The base-class part is a plain
 * std::vector<double>, so the library and the C bridge use the genes exactly as
 * before; `extra` is invisible to every genetic operator.
 */
class Chromosome: public std::vector<double> {
public:
    /// Inline per-chromosome blob. A plain C array (not std::array, see above);
    /// the implicitly-declared copy/move special members copy it element-wise,
    /// so it keeps value semantics and travels with the genes.
    double extra[CHROMOSOME_EXTRA_CAP] {};

    // Inherit the gene (std::vector<double>) constructors (size, fill,
    // iterator-pair, initializer-list, ...) and assignment operators. The
    // language excludes the base copy/move constructors and the implicit
    // default/copy/move special members of Chromosome remain in place, so:
    //
    //   chromosome = otherChromosome   -> implicit Chromosome copy/move assign,
    //                                     which copies `extra` too (used for the
    //                                     elite carry-over);
    //   chromosome = geneVector        -> inherited std::vector<double> assign,
    //                                     which touches ONLY the genes and leaves
    //                                     `extra` as-is (used by mating; the
    //                                     decoder refreshes the blob right after).
    //
    // This minimal surface is also what keeps libstdc++'s relocate/move-insert
    // traits happy: user-declared converting constructors/operators here trip a
    // GCC 13 constexpr bug in std::vector's relocation path.
    using std::vector<double>::vector;
    using std::vector<double>::operator=;
};

} // end namespace BRKGA

#endif // BRKGA_MP_IPR_CHROMOSOME_HPP_
