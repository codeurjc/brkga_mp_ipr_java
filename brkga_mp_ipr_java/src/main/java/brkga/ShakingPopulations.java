/******************************************************************************
 * ShakingPopulations.java: mutable view of the populations passed to a custom
 * ShakingMethod. Valid ONLY during the shaking callback. Backed by the native
 * `populations` / `shaken` pointers via the brkga_shaking_* helpers.
 *****************************************************************************/

package brkga;

import java.lang.foreign.MemorySegment;

/**
 * Mutable view of the populations passed to a custom {@link ShakingMethod}.
 *
 * <p>Backed by the native {@code populations} / {@code shaken} pointers via the
 * {@code brkga_shaking_*} helpers.
 *
 * <p><b>Warning:</b> this view is valid ONLY during the shaking callback; do
 * not retain a reference to it afterwards. Shaking is intrusive, so the
 * caller must preserve the BRKGA invariants (chromosome size, population
 * size).
 */
public final class ShakingPopulations {

    private final NativeBrkga lib;
    private final MemorySegment populations;
    private final MemorySegment shaken;

    ShakingPopulations(NativeBrkga lib, MemorySegment populations, MemorySegment shaken) {
        this.lib = lib;
        this.populations = populations;
        this.shaken = shaken;
    }

    /**
     * Returns the number of populations.
     *
     * @return the number of populations.
     */
    public int numPopulations() {
        try { return (int) lib.shakingNumPopulations.invoke(populations); }
        catch(Throwable t) { throw new RuntimeException("shaking_num_populations", t); }
    }

    /**
     * Returns the number of chromosomes in population {@code p}.
     *
     * @param p the population index.
     * @return the population size.
     */
    public int populationSize(int p) {
        try { return (int) lib.shakingPopulationSize.invoke(populations, p); }
        catch(Throwable t) { throw new RuntimeException("shaking_population_size", t); }
    }

    /**
     * Returns the number of genes (alleles) per chromosome in population
     * {@code p}.
     *
     * @param p the population index.
     * @return the chromosome size.
     */
    public int chromosomeSize(int p) {
        try { return (int) lib.shakingChromosomeSize.invoke(populations, p); }
        catch(Throwable t) { throw new RuntimeException("shaking_chromosome_size", t); }
    }

    /**
     * Returns the value of an allele.
     *
     * @param p          the population index.
     * @param chromosome the chromosome index within the population.
     * @param allele     the allele (gene) index within the chromosome.
     * @return the allele value, in {@code [0, 1]}.
     */
    public double getAllele(int p, int chromosome, int allele) {
        try { return (double) lib.shakingGetAllele.invoke(populations, p, chromosome, allele); }
        catch(Throwable t) { throw new RuntimeException("shaking_get_allele", t); }
    }

    /**
     * Sets the value of an allele.
     *
     * @param p          the population index.
     * @param chromosome the chromosome index within the population.
     * @param allele     the allele (gene) index within the chromosome.
     * @param value      the new allele value, in {@code [0, 1]}.
     */
    public void setAllele(int p, int chromosome, int allele, double value) {
        try { lib.shakingSetAllele.invoke(populations, p, chromosome, allele, value); }
        catch(Throwable t) { throw new RuntimeException("shaking_set_allele", t); }
    }

    /**
     * Records that chromosome {@code chromosome} of population {@code p} was
     * shaken, so it gets re-decoded.
     *
     * <p><b>Note:</b> if no chromosome is marked during the callback, ALL
     * chromosomes are re-decoded.
     *
     * @param p          the population index.
     * @param chromosome the chromosome index within the population.
     */
    public void markShaken(int p, int chromosome) {
        try { lib.shakingMark.invoke(shaken, p, chromosome); }
        catch(Throwable t) { throw new RuntimeException("shaking_mark", t); }
    }
}
