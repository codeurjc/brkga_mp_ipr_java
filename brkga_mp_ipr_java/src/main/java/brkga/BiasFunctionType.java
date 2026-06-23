/******************************************************************************
 * BiasFunctionType.java: Java mirror of BRKGA::BiasFunctionType.
 *****************************************************************************/

package brkga;

/**
 * Specifies a bias function type when choosing parents for mating
 * ({@code r} is the parent rank, a given parameter).
 *
 * <p>This function substitutes the {@code rhoe} parameter from the original
 * BRKGA. The ordinal matches the C++ enum declaration order.
 */
public enum BiasFunctionType {
    /** {@code 1 / (num. parents for mating)} (all individuals have the same probability). */
    CONSTANT,
    /** {@code r^-3}. */
    CUBIC,
    /** {@code e^-r}. */
    EXPONENTIAL,
    /** {@code 1/r}. */
    LINEAR,
    /** {@code 1 / log(r + 1)} (usually works better than other functions). */
    LOGINVERSE,
    /** {@code r^-2}. */
    QUADRATIC,
    /** Indicates a custom bias function supplied by the user (see {@link BiasFunction}). */
    CUSTOM;

    /**
     * Returns the native value of this bias function type.
     *
     * @return the ordinal, matching the C++ enum value.
     */
    public int value() { return ordinal(); }

    /**
     * Returns the {@code BiasFunctionType} matching the given native value.
     *
     * @param v the native value (ordinal).
     * @return the corresponding {@code BiasFunctionType}.
     */
    public static BiasFunctionType fromValue(int v) { return values()[v]; }
}
