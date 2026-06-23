/******************************************************************************
 * StoppingCriteria.java: callback determining whether the algorithm must stop.
 * Mirrors the std::function passed to setStoppingCriteria().
 *****************************************************************************/

package brkga;

/**
 * Callback determining whether the algorithm must stop.
 *
 * <p>Java mirror of the {@code std::function} passed to
 * {@code setStoppingCriteria()}.
 */
@FunctionalInterface
public interface StoppingCriteria {
    /**
     * Decides whether the optimization must stop.
     *
     * @param status a snapshot of the algorithm status.
     * @return {@code true} if the algorithm must stop, {@code false} to keep
     *         running.
     */
    boolean shouldStop(AlgorithmStatus status);
}
