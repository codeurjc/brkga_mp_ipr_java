/******************************************************************************
 * BrkgaParams.java: Java POJO mirroring BRKGA::BrkgaParams (scalar fields).
 *
 * Holds the BRKGA and IPR hyper-parameters. Built by hand or via
 * Brkga.readConfiguration(). Marshalled to/from the native flat struct
 * `BrkgaParamsC` (see brkga_bridge.cpp). The functor fields of the C++ struct
 * (pr_distance_function, custom_shaking) are not represented here: the IPR
 * distance functor is derived natively from `prDistanceFunctionType`
 * (HAMMING/KENDALLTAU); CUSTOM callbacks are handled separately.
 *
 * Note: the StructLayout member names below keep the snake_case spelling of the
 * native `BrkgaParamsC` struct on purpose (they document the C ABI mapping);
 * only the Java fields use idiomatic camelCase.
 *****************************************************************************/

package brkga;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Represents the BRKGA and IPR hyper-parameters.
 *
 * <p>This is a Java POJO mirroring the C++ {@code BRKGA::BrkgaParams} class. It
 * holds the hyper-parameters that govern the multi-parent BRKGA evolution and
 * the Implicit Path Relinking (IPR) procedure. Instances are built by hand or
 * loaded via {@code Brkga.readConfiguration()}, and are marshalled to/from the
 * native flat struct {@code BrkgaParamsC}.</p>
 */
public final class BrkgaParams {

    // BRKGA hyper-parameters.

    /** Number of elements in the population ({@code > 0}). */
    public int populationSize = 0;

    /** Percentage of individuals to become the elite set; range (0.0, 1.0]. */
    public double elitePercentage = 0.0;

    /** Percentage of mutants to be inserted in the population; range (0.0, 1.0]. */
    public double mutantsPercentage = 0.0;

    /** Number of elite parents for mating ({@code > 0}). */
    public int numEliteParents = 0;

    /** Number of total parents for mating ({@code > 0}). */
    public int totalParents = 0;

    /** Type of bias that will be used. See {@link BiasFunctionType}. */
    public BiasFunctionType biasType = BiasFunctionType.CONSTANT;

    /** Number of independent parallel populations ({@code > 0}). */
    public int numIndependentPopulations = 0;

    // Path-relinking parameters.

    /** Number of pairs of chromosomes to be tested to path relinking ({@code > 0}). */
    public int prNumberPairs = 0;

    /**
     * Minimum distance between chromosomes selected to path-relinking. Valid
     * value range depends on the used distance function.
     */
    public double prMinimumDistance = 0.0;

    /** Path relinking type. See {@link PathRelinking.Type}. */
    public PathRelinking.Type prType = PathRelinking.Type.DIRECT;

    /** Individual selection to path-relinking. See {@link PathRelinking.Selection}. */
    public PathRelinking.Selection prSelection = PathRelinking.Selection.BESTSOLUTION;

    /**
     * Type of the distance function used on path-relinking. See
     * {@link PathRelinking.DistanceFunctionType}.
     */
    public PathRelinking.DistanceFunctionType prDistanceFunctionType =
        PathRelinking.DistanceFunctionType.CUSTOM;

    /** Defines the block size based on the size of the population ({@code > 0}). */
    public double alphaBlockSize = 0.0;

    /** Percentage / path size to be computed; range (0.0, 1.0]. */
    public double prPercentage = 0.0;

    // Exchange parameter.

    /** Number of elite chromosomes exchanged from each population ({@code > 0}). */
    public int numExchangeIndividuals = 0;

    // Shaking parameters.

    /** Type of the shaking procedure. See {@link ShakingType}. */
    public ShakingType shakingType = ShakingType.CHANGE;

    /**
     * Shaking intensity lower bound; range (0.0, 1.0]. For the default CHANGE
     * and SWAP procedures, this value is a percentage of the chromosome size.
     * If {@code shakingIntensityLowerBound < shakingIntensityUpperBound}, a
     * random intensity is uniformly drawn between these bounds on each shaking
     * call; if they are equal, a fixed intensity is always used. See
     * {@link #shakingIntensityUpperBound}.
     */
    public double shakingIntensityLowerBound = 0.0;

    /**
     * Shaking intensity upper bound; range (0.0, 1.0]. For the default CHANGE
     * and SWAP procedures, this value is a percentage of the chromosome size.
     * If {@code shakingIntensityLowerBound < shakingIntensityUpperBound}, a
     * random intensity is uniformly drawn between these bounds on each shaking
     * call; if they are equal, a fixed intensity is always used. See
     * {@link #shakingIntensityLowerBound}.
     */
    public double shakingIntensityUpperBound = 0.0;

    /**
     * Creates a parameter object with all fields at their defaults. Populate the
     * fields by hand, or load them via {@link Brkga#readConfiguration}.
     */
    public BrkgaParams() {}

    //-------------------------- Native struct layout ------------------------//

    /// Matches `struct BrkgaParamsC` in brkga_bridge.cpp (snake_case member
    /// names kept to mirror the C struct).
    static final StructLayout LAYOUT = MemoryLayout.structLayout(
        JAVA_DOUBLE.withName("elite_percentage"),
        JAVA_DOUBLE.withName("mutants_percentage"),
        JAVA_DOUBLE.withName("pr_minimum_distance"),
        JAVA_DOUBLE.withName("alpha_block_size"),
        JAVA_DOUBLE.withName("pr_percentage"),
        JAVA_DOUBLE.withName("shaking_intensity_lower_bound"),
        JAVA_DOUBLE.withName("shaking_intensity_upper_bound"),
        JAVA_INT.withName("population_size"),
        JAVA_INT.withName("num_elite_parents"),
        JAVA_INT.withName("total_parents"),
        JAVA_INT.withName("bias_type"),
        JAVA_INT.withName("num_independent_populations"),
        JAVA_INT.withName("pr_number_pairs"),
        JAVA_INT.withName("pr_type"),
        JAVA_INT.withName("pr_selection"),
        JAVA_INT.withName("pr_distance_function_type"),
        JAVA_INT.withName("num_exchange_individuals"),
        JAVA_INT.withName("shaking_type")
    );

    private static VarHandle vh(String n) {
        return LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(n));
    }

    private static final VarHandle ELITE_PCT  = vh("elite_percentage");
    private static final VarHandle MUTANTS_PCT = vh("mutants_percentage");
    private static final VarHandle PR_MIN_DIST = vh("pr_minimum_distance");
    private static final VarHandle ALPHA_BLOCK = vh("alpha_block_size");
    private static final VarHandle PR_PCT      = vh("pr_percentage");
    private static final VarHandle SHK_LO      = vh("shaking_intensity_lower_bound");
    private static final VarHandle SHK_HI      = vh("shaking_intensity_upper_bound");
    private static final VarHandle POP_SIZE    = vh("population_size");
    private static final VarHandle NUM_ELITE_P = vh("num_elite_parents");
    private static final VarHandle TOTAL_P     = vh("total_parents");
    private static final VarHandle BIAS_TYPE   = vh("bias_type");
    private static final VarHandle NUM_INDEP   = vh("num_independent_populations");
    private static final VarHandle PR_PAIRS    = vh("pr_number_pairs");
    private static final VarHandle PR_TYPE     = vh("pr_type");
    private static final VarHandle PR_SEL      = vh("pr_selection");
    private static final VarHandle PR_DIST_T   = vh("pr_distance_function_type");
    private static final VarHandle NUM_EXCH    = vh("num_exchange_individuals");
    private static final VarHandle SHK_TYPE    = vh("shaking_type");

    /**
     * Writes these parameters into the given native {@link #LAYOUT} memory
     * segment ({@code BrkgaParamsC} struct).
     *
     * @param s the target memory segment.
     */
    void writeTo(MemorySegment s) {
        ELITE_PCT.set(s, 0L, elitePercentage);
        MUTANTS_PCT.set(s, 0L, mutantsPercentage);
        PR_MIN_DIST.set(s, 0L, prMinimumDistance);
        ALPHA_BLOCK.set(s, 0L, alphaBlockSize);
        PR_PCT.set(s, 0L, prPercentage);
        SHK_LO.set(s, 0L, shakingIntensityLowerBound);
        SHK_HI.set(s, 0L, shakingIntensityUpperBound);
        POP_SIZE.set(s, 0L, populationSize);
        NUM_ELITE_P.set(s, 0L, numEliteParents);
        TOTAL_P.set(s, 0L, totalParents);
        BIAS_TYPE.set(s, 0L, biasType.value());
        NUM_INDEP.set(s, 0L, numIndependentPopulations);
        PR_PAIRS.set(s, 0L, prNumberPairs);
        PR_TYPE.set(s, 0L, prType.value());
        PR_SEL.set(s, 0L, prSelection.value());
        PR_DIST_T.set(s, 0L, prDistanceFunctionType.value());
        NUM_EXCH.set(s, 0L, numExchangeIndividuals);
        SHK_TYPE.set(s, 0L, shakingType.value());
    }

    /**
     * Reads a new {@code BrkgaParams} from the given native {@link #LAYOUT}
     * memory segment ({@code BrkgaParamsC} struct).
     *
     * @param s the source memory segment.
     * @return a populated {@code BrkgaParams} instance.
     */
    static BrkgaParams readFrom(MemorySegment s) {
        BrkgaParams p = new BrkgaParams();
        p.elitePercentage   = (double) ELITE_PCT.get(s, 0L);
        p.mutantsPercentage = (double) MUTANTS_PCT.get(s, 0L);
        p.prMinimumDistance = (double) PR_MIN_DIST.get(s, 0L);
        p.alphaBlockSize    = (double) ALPHA_BLOCK.get(s, 0L);
        p.prPercentage      = (double) PR_PCT.get(s, 0L);
        p.shakingIntensityLowerBound = (double) SHK_LO.get(s, 0L);
        p.shakingIntensityUpperBound = (double) SHK_HI.get(s, 0L);
        p.populationSize    = (int) POP_SIZE.get(s, 0L);
        p.numEliteParents   = (int) NUM_ELITE_P.get(s, 0L);
        p.totalParents      = (int) TOTAL_P.get(s, 0L);
        p.biasType          = BiasFunctionType.fromValue((int) BIAS_TYPE.get(s, 0L));
        p.numIndependentPopulations = (int) NUM_INDEP.get(s, 0L);
        p.prNumberPairs     = (int) PR_PAIRS.get(s, 0L);
        p.prType            = PathRelinking.Type.fromValue((int) PR_TYPE.get(s, 0L));
        p.prSelection       = PathRelinking.Selection.fromValue((int) PR_SEL.get(s, 0L));
        p.prDistanceFunctionType =
            PathRelinking.DistanceFunctionType.fromValue((int) PR_DIST_T.get(s, 0L));
        p.numExchangeIndividuals = (int) NUM_EXCH.get(s, 0L);
        p.shakingType       = ShakingType.fromValue((int) SHK_TYPE.get(s, 0L));
        return p;
    }
}
