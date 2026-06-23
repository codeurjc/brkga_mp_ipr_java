/******************************************************************************
 * DistanceFunction.java: custom IPR distance function
 * (DistanceFunctionType.CUSTOM). Mirrors BRKGA::DistanceFunctionBase, used
 * during implicit path relinking.
 *****************************************************************************/

package brkga;

/**
 * Interface for functors that compute the distance between two chromosomes,
 * used during implicit path relinking when
 * {@link PathRelinking.DistanceFunctionType#CUSTOM} is selected.
 *
 * <p>Java mirror of {@code BRKGA::DistanceFunctionBase}. Chromosomes are passed
 * as clean {@link Chromosome} objects (no Foreign Function &amp; Memory types are
 * exposed).</p>
 */
public interface DistanceFunction {
    /**
     * Computes the distance between two chromosomes.
     *
     * @param v1 first chromosome.
     * @param v2 second chromosome.
     * @return the distance between {@code v1} and {@code v2}.
     */
    double distance(Chromosome v1, Chromosome v2);

    /**
     * Returns {@code true} if changing {@code key1} to {@code key2} affects the
     * solution.
     *
     * @param key1 the first key.
     * @param key2 the second key.
     * @return {@code true} if the change affects the solution.
     */
    boolean affectSolution(double key1, double key2);

    /**
     * Returns {@code true} if changing the block of keys {@code v1} to the block
     * of keys {@code v2} affects the solution.
     *
     * <p>{@code v1} and {@code v2} are views over the block being considered:
     * each one holds exactly {@code blockSize} genes, accessible as
     * {@code v1.get(0..blockSize-1)}.</p>
     *
     * @param v1        the first block of keys.
     * @param v2        the second block of keys.
     * @param blockSize number of keys to be considered.
     * @return {@code true} if the change affects the solution.
     */
    boolean affectSolutionBlock(Chromosome v1, Chromosome v2, int blockSize);
}
