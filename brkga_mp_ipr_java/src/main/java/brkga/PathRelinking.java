/******************************************************************************
 * PathRelinking.java: Java mirror of the BRKGA::PathRelinking namespace enums.
 *****************************************************************************/

package brkga;

/**
 * Container for the enums of the {@code BRKGA::PathRelinking} namespace,
 * which configure the (implicit) path relinking procedure.
 *
 * <p>This class is not instantiable; it only groups the nested
 * {@link Type}, {@link Selection}, {@link DistanceFunctionType} and
 * {@link PathRelinkingResult} enums.
 */
public final class PathRelinking {

    private PathRelinking() {}

    /**
     * Specifies the type of path relinking.
     *
     * <p>Mirrors {@code BRKGA::PathRelinking::Type}. The ordinal matches C++.
     */
    public enum Type {
        /** Changes each key for the corresponding one in the other chromosome. */
        DIRECT,
        /** Switches the order of a key for that in the other chromosome. */
        PERMUTATION;

        /**
         * Returns the native value of this type.
         *
         * @return the ordinal, matching the C++ enum value.
         */
        public int value() { return ordinal(); }

        /**
         * Returns the {@code Type} matching the given native value.
         *
         * @param v the native value (ordinal).
         * @return the corresponding {@code Type}.
         */
        public static Type fromValue(int v) { return values()[v]; }
    }

    /**
     * Specifies which individuals are used to build the path.
     *
     * <p>Mirrors {@code BRKGA::PathRelinking::Selection}. The ordinal matches C++.
     */
    public enum Selection {
        /** Selects, in order, the best solution of each population. */
        BESTSOLUTION,
        /** Chooses uniformly random solutions from the elite sets. */
        RANDOMELITE;

        /**
         * Returns the native value of this selection.
         *
         * @return the ordinal, matching the C++ enum value.
         */
        public int value() { return ordinal(); }

        /**
         * Returns the {@code Selection} matching the given native value.
         *
         * @param v the native value (ordinal).
         * @return the corresponding {@code Selection}.
         */
        public static Selection fromValue(int v) { return values()[v]; }
    }

    /**
     * Specifies the distance function between two chromosomes during IPR.
     *
     * <p>Mirrors {@code BRKGA::PathRelinking::DistanceFunctionType}. The
     * ordinal matches C++.
     */
    public enum DistanceFunctionType {
        /** Uses the default Hamming distance calculator. */
        HAMMING,
        /** Uses the default Kendall Tau distance calculator. */
        KENDALLTAU,
        /** Indicates a custom function supplied by the user (see {@link DistanceFunction}). */
        CUSTOM;

        /**
         * Returns the native value of this distance function type.
         *
         * @return the ordinal, matching the C++ enum value.
         */
        public int value() { return ordinal(); }

        /**
         * Returns the {@code DistanceFunctionType} matching the given native value.
         *
         * @param v the native value (ordinal).
         * @return the corresponding {@code DistanceFunctionType}.
         */
        public static DistanceFunctionType fromValue(int v) { return values()[v]; }
    }

    /**
     * Specifies the result type/status of the path relink procedure.
     *
     * <p>Mirrors {@code BRKGA::PathRelinking::PathRelinkingResult}. The
     * underlying values are the explicit constants {@code 0}, {@code 1},
     * {@code 3} and {@code 7}, chosen so the statuses can be OR-combined
     * (e.g. {@link #BEST_IMPROVEMENT} {@code 7} implies the lower bits of
     * {@link #ELITE_IMPROVEMENT} {@code 3} and {@link #NO_IMPROVEMENT}
     * {@code 1}).
     */
    public enum PathRelinkingResult {
        /**
         * The chromosomes among the populations are too homogeneous and the
         * path relink will not generate improved solutions.
         */
        TOO_HOMOGENEOUS(0),
        /** Path relink was done but no improved solution was found. */
        NO_IMPROVEMENT(1),
        /**
         * An improved solution among the elite set was found, but the best
         * solution was not improved.
         */
        ELITE_IMPROVEMENT(3),
        /** The best solution was improved. */
        BEST_IMPROVEMENT(7);

        /** The explicit native value of this status ({@code 0}, {@code 1}, {@code 3} or {@code 7}). */
        public final int value;
        PathRelinkingResult(int value) { this.value = value; }

        /**
         * Returns the {@code PathRelinkingResult} matching the given native value.
         *
         * @param v the native value ({@code 0}, {@code 1}, {@code 3} or {@code 7}).
         * @return the corresponding {@code PathRelinkingResult}.
         * @throws IllegalArgumentException if {@code v} matches no status.
         */
        public static PathRelinkingResult fromValue(int v) {
            for(PathRelinkingResult r : values())
                if(r.value == v) return r;
            throw new IllegalArgumentException("Unknown PathRelinkingResult: " + v);
        }
    }
}
