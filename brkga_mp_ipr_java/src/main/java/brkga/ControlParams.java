/******************************************************************************
 * ControlParams.java: Java POJO mirroring BRKGA::ControlParams.
 *
 * Out-of-loop control parameters used by BRKGA_MP_IPR.run(). Marshalled to/from
 * the native flat struct `ControlParamsC`. maximumRunningTime is in seconds.
 *
 * Note: the StructLayout member names keep the snake_case spelling of the
 * native `ControlParamsC` struct on purpose; only the Java fields use camelCase.
 *****************************************************************************/

package brkga;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Represents additional control parameters that can be used outside this
 * framework.
 *
 * <p>This is a Java POJO mirroring the C++ {@code BRKGA::ControlParams} class.
 * These parameters are not used directly in the BRKGA nor in the path
 * relinking. However, they are loaded from the configuration file and can be
 * used by the user to perform out-of-loop controlling of
 * {@code BRKGA_MP_IPR.run()}.</p>
 */
public final class ControlParams {

    /**
     * Maximum running time, in seconds (C++ {@code std::chrono::seconds}).
     */
    public long maximumRunningTime = 0;   // seconds

    /**
     * Interval / number of iterations without improvement in the best solution
     * at which elite chromosomes are exchanged (0 means no exchange).
     */
    public int exchangeInterval = 0;

    /**
     * Interval / number of iterations without improvement in the best solution
     * at which the Implicit Path Relink is called (0 means no IPR).
     */
    public int iprInterval = 0;

    /**
     * Interval / number of iterations without improvement in the best solution
     * at which the populations are shaken (0 means no shake).
     */
    public int shakeInterval = 0;

    /**
     * Interval / number of iterations without improvement in the best solution
     * at which the populations are reset (0 means no reset).
     */
    public int resetInterval = 0;

    /**
     * Number of iterations to stop when the best solution is not improved,
     * i.e., the algorithm converged (0 means don't stop by stall).
     */
    public int stallOffset = 0;

    /**
     * Creates a control-parameter object with all fields at their defaults
     * (i.e. every interval disabled and no time/stall limit). Populate the
     * fields by hand, or load them via {@link Brkga#readConfiguration}.
     */
    public ControlParams() {}

    /// Matches `struct ControlParamsC` in brkga_bridge.cpp (snake_case member
    /// names kept to mirror the C struct).
    static final StructLayout LAYOUT = MemoryLayout.structLayout(
        JAVA_LONG.withName("maximum_running_time"),
        JAVA_INT.withName("exchange_interval"),
        JAVA_INT.withName("ipr_interval"),
        JAVA_INT.withName("shake_interval"),
        JAVA_INT.withName("reset_interval"),
        JAVA_INT.withName("stall_offset")
    );

    private static VarHandle vh(String n) {
        return LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(n));
    }

    private static final VarHandle MAX_TIME = vh("maximum_running_time");
    private static final VarHandle EXCHANGE = vh("exchange_interval");
    private static final VarHandle IPR      = vh("ipr_interval");
    private static final VarHandle SHAKE    = vh("shake_interval");
    private static final VarHandle RESET    = vh("reset_interval");
    private static final VarHandle STALL    = vh("stall_offset");

    /**
     * Writes these parameters into the given native {@link #LAYOUT} memory
     * segment ({@code ControlParamsC} struct).
     *
     * @param s the target memory segment.
     */
    void writeTo(MemorySegment s) {
        MAX_TIME.set(s, 0L, maximumRunningTime);
        EXCHANGE.set(s, 0L, exchangeInterval);
        IPR.set(s, 0L, iprInterval);
        SHAKE.set(s, 0L, shakeInterval);
        RESET.set(s, 0L, resetInterval);
        STALL.set(s, 0L, stallOffset);
    }

    /**
     * Reads a new {@code ControlParams} from the given native {@link #LAYOUT}
     * memory segment ({@code ControlParamsC} struct).
     *
     * @param s the source memory segment.
     * @return a populated {@code ControlParams} instance.
     */
    static ControlParams readFrom(MemorySegment s) {
        ControlParams c = new ControlParams();
        c.maximumRunningTime = (long) MAX_TIME.get(s, 0L);
        c.exchangeInterval = (int) EXCHANGE.get(s, 0L);
        c.iprInterval = (int) IPR.get(s, 0L);
        c.shakeInterval = (int) SHAKE.get(s, 0L);
        c.resetInterval = (int) RESET.get(s, 0L);
        c.stallOffset = (int) STALL.get(s, 0L);
        return c;
    }
}
