/******************************************************************************
 * Sense.java: Java mirror of BRKGA::Sense (enum class Sense).
 *****************************************************************************/

package brkga;

/**
 * Specifies the objective as minimization or maximization.
 *
 * <p>Java mirror of {@code BRKGA::Sense}. The {@link #value} matches the
 * C++ enum and the bridge's {@code sense} argument.
 */
public enum Sense {
    /** Minimization. */
    MINIMIZE(0),
    /** Maximization. */
    MAXIMIZE(1);

    /** Value matching the C++ enum and the bridge's {@code sense} argument. */
    public final int value;

    Sense(int value) {
        this.value = value;
    }

    /**
     * Returns the {@code Sense} matching the given native value.
     *
     * @param v {@code 0} for {@link #MINIMIZE}, otherwise {@link #MAXIMIZE}.
     * @return the corresponding {@code Sense}.
     */
    public static Sense fromValue(int v) {
        return v == 0 ? MINIMIZE : MAXIMIZE;
    }
}
