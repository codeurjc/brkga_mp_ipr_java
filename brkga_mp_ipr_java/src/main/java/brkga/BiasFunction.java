/******************************************************************************
 * BiasFunction.java: custom bias function (BiasFunctionType.CUSTOM).
 * Mirrors the std::function<double(unsigned)> of setBiasCustomFunction().
 *****************************************************************************/

package brkga;

/**
 * Custom bias function used when {@link BiasFunctionType#CUSTOM} is selected.
 *
 * <p>Java mirror of the {@code std::function<double(unsigned)>} passed to
 * {@code setBiasCustomFunction()}: it maps a parent rank to a bias weight.
 *
 * <p><b>Note:</b> the returned weight must be a positive, non-increasing
 * function of the rank (the library validates this).
 */
@FunctionalInterface
public interface BiasFunction {
    /**
     * Returns the bias weight for the given parent rank.
     *
     * @param rank the parent rank (1-based, as passed by the library).
     * @return the bias weight; must be a positive non-increasing function of
     *         the rank (the library validates this).
     */
    double rankBias(int rank);
}
