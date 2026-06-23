/******************************************************************************
 * ShakingType.java: Java mirror of BRKGA::ShakingType. Ordinal matches C++.
 *****************************************************************************/

package brkga;

/**
 * Specifies the type of shaking to be performed.
 *
 * <p>Java mirror of {@code BRKGA::ShakingType}. The ordinal matches the C++
 * enum declaration order.
 */
public enum ShakingType {
    /**
     * Applies the following perturbations:
     * <ol>
     *   <li>Inverts the value of a randomly chosen key, i.e., from {@code value}
     *       to {@code 1 - value};</li>
     *   <li>Assigns a random value to a random key.</li>
     * </ol>
     */
    CHANGE,
    /**
     * Applies two swap perturbations:
     * <ol>
     *   <li>Swaps the values of a randomly chosen key {@code i} and its neighbor
     *       {@code i + 1};</li>
     *   <li>Swaps the values of two randomly chosen keys.</li>
     * </ol>
     */
    SWAP,
    /** Indicates a custom shaking procedure supplied by the user (see {@link ShakingMethod}). */
    CUSTOM;

    /**
     * Returns the native value of this shaking type.
     *
     * @return the ordinal, matching the C++ enum value.
     */
    public int value() { return ordinal(); }

    /**
     * Returns the {@code ShakingType} matching the given native value.
     *
     * @param v the native value (ordinal).
     * @return the corresponding {@code ShakingType}.
     */
    public static ShakingType fromValue(int v) { return values()[v]; }
}
