/******************************************************************************
 * Decoder.java: the user-supplied decoder, the heart of a BRKGA.
 *
 * A decoder maps a chromosome (a vector of genes in [0, 1)) to a solution of
 * the problem and returns its fitness. This is the Java counterpart of the C++
 * decoder method `fitness_t decode(Chromosome&, bool rewrite)`.
 *****************************************************************************/

package brkga;

/**
 * The user-supplied decoder, the heart of a BRKGA.
 *
 * <p>A decoder maps a {@link Chromosome} (a vector of genes in [0, 1)) to a
 * solution of the problem and returns its fitness. Java counterpart of the C++
 * decoder method {@code fitness_t decode(Chromosome&, bool rewrite)}. The
 * decoder is run in Java and invoked by the native algorithm through an FFM
 * upcall, but it never sees any Foreign Function &amp; Memory type.</p>
 */
@FunctionalInterface
public interface Decoder {
    /**
     * Decodes a chromosome into a solution and returns its fitness.
     *
     * <p>The returned array is the fitness used by BRKGA to rank chromosomes: a
     * single element for single-objective problems, or N elements (compared
     * lexicographically) for an N-objective problem. In general the fitness is
     * the cost/value of the solution, but it may also encode penalties for
     * constraint violations.</p>
     *
     * <p><b>Warning:</b> When the algorithm uses more than one thread, this
     * method is called concurrently from several threads, so it <b>must be
     * thread-safe</b>. Keep any writable scratch state per thread (e.g. with a
     * {@link ThreadLocal}) or allocate it on each call.</p>
     *
     * <p><b>Warning:</b> The decoder must be a deterministic function: the same
     * chromosome must always yield the same fitness. Otherwise BRKGA's
     * convergence degrades and runs are not reproducible.</p>
     *
     * @param chromosome the chromosome to decode (genes in [0, 1)).
     * @param rewrite    whether the decoder may rewrite the chromosome (via
     *                   {@link Chromosome#set}) to reflect a local search. The
     *                   TSP example does not use it, but the parameter must be
     *                   honored for Implicit Path Relinking (during which the
     *                   algorithm always passes {@code false}).
     * @return the fitness values (length 1 for single-objective, N for
     *         N-objective problems).
     */
    double[] decode(Chromosome chromosome, boolean rewrite);
}
