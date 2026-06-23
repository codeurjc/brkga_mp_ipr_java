/******************************************************************************
 * Population.java: read-only view of a BRKGA population.
 *****************************************************************************/

package brkga;

/**
 * Read-only view of a BRKGA population (its chromosomes and fitness values,
 * ordered by rank).
 *
 * <p>Mirrors what the C++ {@code const Population&} exposes to users. Backed by
 * {@code BrkgaMpIpr} getters; immutable.
 *
 * <p><b>Note:</b> in C++ the {@code Population} class is not meant to be used
 * externally of its unit. This Java class exposes only a read-only view of it.
 */
public final class Population {

    private final BrkgaMpIpr algorithm;
    private final int index;

    Population(BrkgaMpIpr algorithm, int index) {
        this.algorithm = algorithm;
        this.index = index;
    }

    /**
     * Returns the number of chromosomes in this population.
     *
     * @return the population size.
     */
    public int size() {
        return algorithm.getPopulationSize(index);
    }

    /**
     * Returns the chromosome at the given rank.
     *
     * @param position the rank ({@code 0} = best of this population).
     * @return a copy of the chromosome's genes.
     */
    public double[] getChromosome(int position) {
        return algorithm.getChromosome(index, position);
    }

    /**
     * Returns the fitness at the given rank.
     *
     * @param position the rank ({@code 0} = best of this population).
     * @return the fitness values for that chromosome.
     */
    public double[] getFitness(int position) {
        return algorithm.getFitness(index, position);
    }

    /**
     * Returns the best fitness of this population (rank {@code 0}).
     *
     * @return the best fitness values.
     */
    public double[] getBestFitness() {
        return getFitness(0);
    }
}
