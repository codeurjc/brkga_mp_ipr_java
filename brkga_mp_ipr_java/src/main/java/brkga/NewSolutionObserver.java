/******************************************************************************
 * NewSolutionObserver.java: callback invoked when the best solution improves.
 * Mirrors the std::function passed to addNewSolutionObserver().
 *****************************************************************************/

package brkga;

/**
 * Callback invoked when the best solution improves.
 *
 * <p>Java mirror of the {@code std::function} passed to
 * {@code addNewSolutionObserver()}.
 */
@FunctionalInterface
public interface NewSolutionObserver {
    /**
     * Called when a new best solution is found.
     *
     * @param status a snapshot of the algorithm status.
     * @return {@code true} to keep running, {@code false} to stop the
     *         optimization immediately.
     */
    boolean onNewSolution(AlgorithmStatus status);
}
