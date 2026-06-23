/******************************************************************************
 * ShakingMethod.java: custom shaking procedure (ShakingType.CUSTOM).
 * Mirrors the std::function passed to setShakingMethod(): it perturbs the
 * populations and records which chromosomes were shaken (so they get
 * re-decoded). Population access goes through ShakingPopulations.
 *****************************************************************************/

package brkga;

/**
 * Custom shaking procedure used when {@link ShakingType#CUSTOM} is selected.
 *
 * <p>Java mirror of the {@code std::function} passed to
 * {@code setShakingMethod()}: it perturbs the populations and records which
 * chromosomes were shaken so they get re-decoded. Population access goes
 * through {@link ShakingPopulations}.
 *
 * <p><b>Note:</b> if no chromosome is marked via
 * {@link ShakingPopulations#markShaken(int, int)}, ALL chromosomes are
 * re-decoded.
 *
 * <p><b>Warning:</b> this procedure is intrusive. It must keep the BRKGA
 * invariants intact (for instance, it must not change the chromosome size or
 * the population size).
 */
@FunctionalInterface
public interface ShakingMethod {
    /**
     * Perturbs the given populations.
     *
     * @param lowerBound  shaking intensity lower bound (from {@code BrkgaParams}).
     * @param upperBound  shaking intensity upper bound.
     * @param populations mutable view of the populations; use
     *                    {@link ShakingPopulations#setAllele(int, int, int, double)}
     *                    to perturb and
     *                    {@link ShakingPopulations#markShaken(int, int)} for each
     *                    changed chromosome. If nothing is marked, ALL
     *                    chromosomes are re-decoded.
     */
    void shake(double lowerBound, double upperBound, ShakingPopulations populations);
}
