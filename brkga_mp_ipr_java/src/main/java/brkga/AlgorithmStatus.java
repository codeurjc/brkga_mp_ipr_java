/******************************************************************************
 * AlgorithmStatus.java: Java mirror of BRKGA::AlgorithmStatus, populated from
 * the flat `BridgeStatus` C struct filled by the native bridge.
 *
 * Supports both single- (1) and multi-objective (2) fitness. The toString()
 * method reproduces the C++ `operator<<` for AlgorithmStatus: the fitness is
 * printed as a single number for one objective, or as "(a, b)" (the tuple
 * streaming operator) for two. Note: chrono durations are printed with an "s"
 * suffix, but the exact floating-point formatting may differ from libstdc++.
 *
 * The StructLayout member names and the toString() labels keep the snake_case
 * spelling on purpose: the layout mirrors the native `BridgeStatus` struct, and
 * the labels reproduce the C++ `operator<<` output verbatim. Only the Java
 * fields use idiomatic camelCase.
 *****************************************************************************/

package brkga;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Defines the current status of the algorithm for a given
 * {@code BRKGA_MP_IPR.run()} call.
 *
 * <p>This is a Java mirror of the C++ {@code BRKGA::AlgorithmStatus} class,
 * populated from the flat {@code BridgeStatus} C struct filled by the native
 * bridge. It supports both single-objective (1 fitness value) and
 * multi-objective (N fitness values) optimization.</p>
 *
 * <p>The C++ class uses {@code std::chrono::duration<double>} for the time
 * fields to keep better precision; these are mapped here to {@code double}
 * values expressed in seconds.</p>
 */
public class AlgorithmStatus {

    //-------------------------- Native struct layout ------------------------//

    /**
     * Capacity of the {@code best_fitness} array in the native status struct
     * (must match {@code MAX_OBJECTIVES} in the bridge build and
     * {@code NativeBrkga.CAP}).
     */
    public static final int CAP = 20;

    /** Memory layout matching {@code struct BridgeStatus} in brkga_bridge.cpp. */
    static final StructLayout LAYOUT = MemoryLayout.structLayout(
        MemoryLayout.sequenceLayout(CAP, JAVA_DOUBLE).withName("best_fitness"),
        JAVA_DOUBLE.withName("current_time"),
        JAVA_DOUBLE.withName("last_update_time"),
        JAVA_DOUBLE.withName("path_relink_time"),
        JAVA_INT.withName("num_objectives"),
        JAVA_INT.withName("current_iteration"),
        JAVA_INT.withName("last_update_iteration"),
        JAVA_INT.withName("largest_iteration_offset"),
        JAVA_INT.withName("stalled_iterations"),
        JAVA_INT.withName("num_path_relink_calls"),
        JAVA_INT.withName("num_homogenities"),
        JAVA_INT.withName("num_best_improvements"),
        JAVA_INT.withName("num_elite_improvements"),
        JAVA_INT.withName("num_exchanges"),
        JAVA_INT.withName("num_shakes"),
        JAVA_INT.withName("num_resets")
    );

    private static VarHandle vh(String name) {
        return LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(name));
    }

    private static final VarHandle CURRENT_TIME             = vh("current_time");
    private static final VarHandle LAST_UPDATE_TIME         = vh("last_update_time");
    private static final VarHandle PATH_RELINK_TIME         = vh("path_relink_time");
    private static final VarHandle NUM_OBJECTIVES           = vh("num_objectives");
    private static final VarHandle CURRENT_ITERATION        = vh("current_iteration");
    private static final VarHandle LAST_UPDATE_ITERATION    = vh("last_update_iteration");
    private static final VarHandle LARGEST_ITERATION_OFFSET = vh("largest_iteration_offset");
    private static final VarHandle STALLED_ITERATIONS       = vh("stalled_iterations");
    private static final VarHandle NUM_PATH_RELINK_CALLS    = vh("num_path_relink_calls");
    private static final VarHandle NUM_HOMOGENITIES         = vh("num_homogenities");
    private static final VarHandle NUM_BEST_IMPROVEMENTS    = vh("num_best_improvements");
    private static final VarHandle NUM_ELITE_IMPROVEMENTS   = vh("num_elite_improvements");
    private static final VarHandle NUM_EXCHANGES            = vh("num_exchanges");
    private static final VarHandle NUM_SHAKES               = vh("num_shakes");
    private static final VarHandle NUM_RESETS               = vh("num_resets");

    //--------------------------------- Fields -------------------------------//

    /**
     * Current best fitness value(s). Holds 1 element for single-objective
     * optimization, or N elements for multi-objective optimization (the C++
     * {@code fitness_t}, mapped here to {@code double[]}).
     */
    public final double[] bestFitness;

    /** The current elapsed time, in seconds. */
    public final double currentTime;

    /** The time of the last update, in seconds. */
    public final double lastUpdateTime;

    /** Total time spent on path relinking so far, in seconds. */
    public final double pathRelinkTime;

    /** The current algorithm iteration / generation. */
    public final int    currentIteration;

    /** The iteration of the last update. */
    public final int    lastUpdateIteration;

    /** The largest number of iterations between two updates. */
    public final int    largestIterationOffset;

    /** Last number of iterations without improvement in the best solution. */
    public final int    stalledIterations;

    /** Number of calls to path relinking. */
    public final int    numPathRelinkCalls;

    /**
     * Number of homogenities, i.e., number of times that the populations were
     * too homogeneous and the path relinking could not be performed.
     */
    public final int    numHomogenities;

    /** Number of improvements in the best solution by the path relinking. */
    public final int    numBestImprovements;

    /**
     * Number of improvements in elite individuals (but not the best one) by
     * path relinking.
     */
    public final int    numEliteImprovements;

    /** Number of exchanges between populations performed (not number of individuals). */
    public final int    numExchanges;

    /** Number of shakes performed. */
    public final int    numShakes;

    /** Number of population resets performed. */
    public final int    numResets;

    //----------------------------- Construction -----------------------------//

    private AlgorithmStatus(MemorySegment s) {
        int n = (int) NUM_OBJECTIVES.get(s, 0L);
        this.bestFitness = new double[n];
        for(int i = 0; i < n; ++i)
            this.bestFitness[i] = s.getAtIndex(JAVA_DOUBLE, i);

        this.currentTime            = (double) CURRENT_TIME.get(s, 0L);
        this.lastUpdateTime         = (double) LAST_UPDATE_TIME.get(s, 0L);
        this.pathRelinkTime         = (double) PATH_RELINK_TIME.get(s, 0L);
        this.currentIteration       = (int)    CURRENT_ITERATION.get(s, 0L);
        this.lastUpdateIteration    = (int)    LAST_UPDATE_ITERATION.get(s, 0L);
        this.largestIterationOffset = (int)    LARGEST_ITERATION_OFFSET.get(s, 0L);
        this.stalledIterations      = (int)    STALLED_ITERATIONS.get(s, 0L);
        this.numPathRelinkCalls     = (int)    NUM_PATH_RELINK_CALLS.get(s, 0L);
        this.numHomogenities        = (int)    NUM_HOMOGENITIES.get(s, 0L);
        this.numBestImprovements    = (int)    NUM_BEST_IMPROVEMENTS.get(s, 0L);
        this.numEliteImprovements   = (int)    NUM_ELITE_IMPROVEMENTS.get(s, 0L);
        this.numExchanges           = (int)    NUM_EXCHANGES.get(s, 0L);
        this.numShakes              = (int)    NUM_SHAKES.get(s, 0L);
        this.numResets              = (int)    NUM_RESETS.get(s, 0L);
    }

    /**
     * Reads a new {@code AlgorithmStatus} from the given native {@link #LAYOUT}
     * memory segment ({@code BridgeStatus} struct).
     *
     * @param segment the source memory segment.
     * @return a populated {@code AlgorithmStatus} instance.
     */
    static AlgorithmStatus readFrom(MemorySegment segment) {
        return new AlgorithmStatus(segment);
    }

    //------------------------------- Printing -------------------------------//

    /**
     * Reproduces the C++ printing of {@code fitness_t}: a single value for
     * single-objective optimization, or a {@code "(a, b)"} tuple for
     * multi-objective.
     *
     * @return the formatted fitness string.
     */
    public String fitnessToString() {
        if(bestFitness.length == 1)
            return Double.toString(bestFitness[0]);
        StringBuilder sb = new StringBuilder("(");
        for(int i = 0; i < bestFitness.length; ++i) {
            if(i > 0) sb.append(", ");
            sb.append(bestFitness[i]);
        }
        return sb.append(")").toString();
    }

    /**
     * Reproduces the C++ {@code operator<<} output for {@code AlgorithmStatus}.
     * The field labels are kept in snake_case on purpose to match the C++
     * output verbatim.
     *
     * @return the multi-line string representation of this status.
     */
    @Override
    public String toString() {
        return  "\nbest_fitness: " + fitnessToString()
              + "\ncurrent_iteration: " + currentIteration
              + "\nlast_update_iteration: " + lastUpdateIteration
              + "\ncurrent_time: " + currentTime + "s"
              + "\nlast_update_time: " + lastUpdateTime + "s"
              + "\nlargest_iteration_offset: " + largestIterationOffset
              + "\nstalled_iterations: " + stalledIterations
              + "\npath_relink_time: " + pathRelinkTime + "s"
              + "\nnum_path_relink_calls: " + numPathRelinkCalls
              + "\nnum_homogenities: " + numHomogenities
              + "\nnum_best_improvements: " + numBestImprovements
              + "\nnum_elite_improvements: " + numEliteImprovements
              + "\nnum_exchanges: " + numExchanges
              + "\nnum_shakes: " + numShakes
              + "\nnum_resets: " + numResets;
    }
}
