/******************************************************************************
 * Brkga.java: the entry point and handle of the Java BRKGA-MP-IPR wrapper.
 *
 * A Brkga instance is created for a given number of objectives (it loads the
 * proper native bridge library under the hood) and is the only object the user
 * needs to build algorithms and read/write configurations. It exposes NO
 * Foreign Function & Memory types.
 *****************************************************************************/

package brkga;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Entry point and handle of the Java BRKGA-MP-IPR wrapper.
 *
 * <p>A {@code Brkga} instance is created for a fixed number of objectives via
 * {@link #forObjectives(int)} (or the {@link #single()} / {@link #multi()}
 * shortcuts). It loads the appropriate native bridge library internally and is
 * then used to read/write configurations ({@link #readConfiguration} /
 * {@link #writeConfiguration}), format parameters, and build
 * {@link BrkgaMpIpr} algorithm objects.</p>
 *
 * <p>This class (and the rest of the {@code brkga} public API) exposes no
 * Foreign Function &amp; Memory types; all native plumbing is internal.</p>
 *
 * @see BrkgaMpIpr
 * @see Decoder
 */
public final class Brkga {

    /// The native bridge bindings (internal; never exposed to users).
    final NativeBrkga lib;

    private static final long ERR_LEN = 1024;

    private Brkga(NativeBrkga lib) {
        this.lib = lib;
    }

    //------------------------------- Factories ------------------------------//

    /**
     * Creates a handle for problems with the given number of objectives, loading
     * the appropriate native bridge library.
     *
     * @param numObjectives the number of objective values the decoder produces
     *                      ({@code >= 1}).
     * @return a {@code Brkga} handle bound to that number of objectives.
     * @throws IllegalArgumentException if {@code numObjectives < 1}.
     * @throws RuntimeException if no suitable native bridge is available.
     */
    public static Brkga forObjectives(int numObjectives) {
        return new Brkga(NativeBrkga.forObjectives(numObjectives));
    }

    /**
     * Creates a single-objective handle ({@code forObjectives(1)}).
     *
     * @return a single-objective {@code Brkga} handle.
     */
    public static Brkga single() { return forObjectives(1); }

    /**
     * Creates a two-objective handle ({@code forObjectives(2)}).
     *
     * @return a two-objective {@code Brkga} handle.
     */
    public static Brkga multi() { return forObjectives(2); }

    /**
     * Returns the number of objectives this handle was created for.
     *
     * @return the number of objectives.
     */
    public int numObjectives() { return lib.numObjectives; }

    //--------------------------- Configuration I/O --------------------------//

    /**
     * Pair of parameter objects, as returned by {@link #readConfiguration}.
     *
     * @param brkgaParams   the BRKGA parameters.
     * @param controlParams the external control parameters.
     */
    public record Config(BrkgaParams brkgaParams, ControlParams controlParams) {}

    /**
     * Reads the parameters from a configuration file.
     *
     * <p>Mirrors {@code BRKGA::readConfiguration(filename)}.</p>
     *
     * <p><b>Note:</b> the file format ignores blank lines and lines starting
     * with {@code '#'}, and the parameter order does not matter. If the control
     * parameters are absent from the file, their default values are used.</p>
     *
     * @param configFile the path to the configuration file.
     * @return a {@link Config} with the BRKGA and external control parameters.
     * @throws RuntimeException if the file cannot be read or parsed.
     */
    public Config readConfiguration(String configFile) {
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment path = temp.allocateFrom(configFile);
            MemorySegment errbuf = temp.allocate(ERR_LEN);
            MemorySegment cfg = (MemorySegment)
                lib.readConfiguration.invoke(path, errbuf, ERR_LEN);
            if(cfg.address() == 0)
                throw new RuntimeException(NativeBrkga.cString(errbuf));
            try {
                MemorySegment bp = temp.allocate(BrkgaParams.LAYOUT);
                MemorySegment cp = temp.allocate(ControlParams.LAYOUT);
                lib.configGetBrkgaParams.invoke(cfg, bp);
                lib.configGetControlParams.invoke(cfg, cp);
                return new Config(BrkgaParams.readFrom(bp), ControlParams.readFrom(cp));
            } finally {
                lib.freeConfiguration.invoke(cfg);
            }
        }
        catch(RuntimeException e) { throw e; }
        catch(Throwable t) { throw new RuntimeException("readConfiguration failed", t); }
    }

    /**
     * Writes the parameters into a configuration file.
     *
     * <p>Mirrors
     * {@code BRKGA::writeConfiguration(filename, brkga_params, control_params)}.</p>
     *
     * <p><b>Note:</b> this overwrites the file if it already exists. All
     * floating point parameters are written with two-decimal precision.</p>
     *
     * @param configFile    the path to the configuration file to write.
     * @param brkgaParams   the BRKGA parameters.
     * @param controlParams the external control parameters.
     * @throws RuntimeException if the file cannot be written.
     */
    public void writeConfiguration(String configFile, BrkgaParams brkgaParams,
                                   ControlParams controlParams) {
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment cfg = (MemorySegment) lib.configCreate.invoke();
            try {
                MemorySegment bp = temp.allocate(BrkgaParams.LAYOUT);
                MemorySegment cp = temp.allocate(ControlParams.LAYOUT);
                brkgaParams.writeTo(bp);
                controlParams.writeTo(cp);
                lib.configSetBrkgaParams.invoke(cfg, bp);
                lib.configSetControlParams.invoke(cfg, cp);
                MemorySegment path = temp.allocateFrom(configFile);
                MemorySegment errbuf = temp.allocate(ERR_LEN);
                int rc = (int) lib.writeConfiguration.invoke(cfg, path, errbuf, ERR_LEN);
                if(rc != 0)
                    throw new RuntimeException(NativeBrkga.cString(errbuf));
            } finally {
                lib.freeConfiguration.invoke(cfg);
            }
        }
        catch(RuntimeException e) { throw e; }
        catch(Throwable t) { throw new RuntimeException("writeConfiguration failed", t); }
    }

    //------------------------------- Formatting -----------------------------//

    /**
     * Renders the BRKGA parameters exactly as the C++ {@code operator<<} would.
     *
     * @param params the parameters to format.
     * @return the multi-line text rendering.
     */
    public String format(BrkgaParams params) { return lib.format(params); }

    /**
     * Renders the control parameters exactly as the C++ {@code operator<<} would.
     *
     * @param params the control parameters to format.
     * @return the multi-line text rendering.
     */
    public String format(ControlParams params) { return lib.format(params); }
}
