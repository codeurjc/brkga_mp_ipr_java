/******************************************************************************
 * NativeBrkga.java: low-level FFM binding to a bridge shared library
 * (libbrkga_bridge_<n>.so or libbrkga_bridge_generic.so).
 *
 * Each instance loads one bridge library, builds one MethodHandle per
 * `extern "C"` function, and provides helpers to turn Java callbacks into
 * native function pointers (upcall stubs).
 *
 * Linux-only. Requires the JVM flag --enable-native-access=ALL-UNNAMED.
 *****************************************************************************/

package brkga;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Low-level FFM binding to a bridge shared library
 * ({@code libbrkga_bridge_<n>.so} or {@code libbrkga_bridge_generic.so}).
 *
 * <p>Each instance loads ONE bridge library, builds one {@code MethodHandle} per
 * {@code extern "C"} function, and provides helpers to turn Java callbacks into
 * native function pointers (upcall stubs).</p>
 *
 * <p>Linux-only. Requires the JVM flag {@code --enable-native-access=ALL-UNNAMED}.</p>
 *
 * <p><b>Internal:</b> this class is package-private plumbing. Users interact with
 * the clean {@link Brkga} handle and {@link BrkgaMpIpr}, never with this binding.</p>
 *
 * @see BrkgaMpIpr
 * @see Brkga
 */
final class NativeBrkga {

    static final Arena GLOBAL_ARENA = Arena.ofShared();
    private static final Linker LINKER = Linker.nativeLinker();

    /** Number of objectives this bridge instance was loaded for (1..{@link #CAP}). */
    public final int numObjectives;
    private final SymbolLookup lookup;

    // Configuration.
    final MethodHandle readConfiguration;
    final MethodHandle configCreate;
    final MethodHandle configSetBrkgaParams;
    final MethodHandle configGetBrkgaParams;
    final MethodHandle configSetControlParams;
    final MethodHandle configGetControlParams;
    final MethodHandle writeConfiguration;
    final MethodHandle paramsToString;
    final MethodHandle controlParamsToString;
    final MethodHandle freeConfiguration;
    // Algorithm lifecycle.
    final MethodHandle create;
    final MethodHandle destroy;
    final MethodHandle setStoppingCriteria;
    final MethodHandle addNewSolutionObserver;
    final MethodHandle setInitialPopulation;
    final MethodHandle setQuiet;
    final MethodHandle run;
    // Algorithm operations.
    final MethodHandle evolve;
    final MethodHandle pathRelink;
    final MethodHandle exchangeElite;
    final MethodHandle shake;
    final MethodHandle reset;
    final MethodHandle injectChromosome;
    // Getters.
    final MethodHandle getBestFitness;
    final MethodHandle getBestChromosome;
    final MethodHandle getBestChromosomeExtra;
    final MethodHandle getChromosome;
    final MethodHandle getChromosomeExtra;
    final MethodHandle extraCapacity;
    final MethodHandle getFitness;
    final MethodHandle getAlgoBrkgaParams;
    final MethodHandle getOptimizationSense;
    final MethodHandle getChromosomeSize;
    final MethodHandle getEliteSize;
    final MethodHandle getNumMutants;
    final MethodHandle getMaxThreads;
    final MethodHandle getPopulationSize;
    // CUSTOM callbacks.
    final MethodHandle setBiasCustomFunction;
    final MethodHandle configSetCustomDistance;
    final MethodHandle setShakingMethod;
    final MethodHandle shakingNumPopulations;
    final MethodHandle shakingPopulationSize;
    final MethodHandle shakingChromosomeSize;
    final MethodHandle shakingGetAllele;
    final MethodHandle shakingSetAllele;
    final MethodHandle shakingMark;

    private NativeBrkga(Path soPath, int numObjectives) {
        this.numObjectives = numObjectives;
        this.lookup = SymbolLookup.libraryLookup(soPath, GLOBAL_ARENA);

        readConfiguration = dc("brkga_read_configuration",
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));
        configCreate = dc("brkga_config_create", FunctionDescriptor.of(ADDRESS));
        configSetBrkgaParams = dc("brkga_config_set_brkga_params",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
        configGetBrkgaParams = dc("brkga_config_get_brkga_params",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
        configSetControlParams = dc("brkga_config_set_control_params",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
        configGetControlParams = dc("brkga_config_get_control_params",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
        writeConfiguration = dc("brkga_write_configuration",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));
        paramsToString = dc("brkga_params_to_string",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_LONG));
        controlParamsToString = dc("brkga_control_params_to_string",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_LONG));
        freeConfiguration = dc("brkga_free_configuration",
            FunctionDescriptor.ofVoid(ADDRESS));

        create = dc("brkga_create",
            FunctionDescriptor.of(ADDRESS,
                ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT,
                ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));
        destroy = dc("brkga_destroy", FunctionDescriptor.ofVoid(ADDRESS));
        setStoppingCriteria = dc("brkga_set_stopping_criteria",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));
        addNewSolutionObserver = dc("brkga_add_new_solution_observer",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));
        setInitialPopulation = dc("brkga_set_initial_population",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG));
        setQuiet = dc("brkga_set_quiet", FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT));
        run = dc("brkga_run",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));

        evolve = dc("brkga_evolve",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_LONG));
        pathRelink = dc("brkga_path_relink",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT,
                JAVA_INT, JAVA_DOUBLE, JAVA_LONG, JAVA_LONG, JAVA_DOUBLE,
                ADDRESS, JAVA_LONG));
        exchangeElite = dc("brkga_exchange_elite",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_LONG));
        shake = dc("brkga_shake",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT,
                ADDRESS, JAVA_LONG));
        reset = dc("brkga_reset", FunctionDescriptor.ofVoid(ADDRESS));
        injectChromosome = dc("brkga_inject_chromosome",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT,
                JAVA_INT, ADDRESS, JAVA_LONG));

        getBestFitness = dc("brkga_get_best_fitness",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
        getBestChromosome = dc("brkga_get_best_chromosome",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG));
        getBestChromosomeExtra = dc("brkga_get_best_chromosome_extra",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG));
        getChromosome = dc("brkga_get_chromosome",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS,
                JAVA_INT, ADDRESS, JAVA_LONG));
        getChromosomeExtra = dc("brkga_get_chromosome_extra",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS,
                JAVA_LONG, ADDRESS, JAVA_LONG));
        extraCapacity = dc("brkga_extra_capacity", FunctionDescriptor.of(JAVA_INT));
        getFitness = dc("brkga_get_fitness",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS,
                ADDRESS, JAVA_LONG));
        getAlgoBrkgaParams = dc("brkga_get_algo_brkga_params",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));
        getOptimizationSense = dc("brkga_get_optimization_sense",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));
        getChromosomeSize = dc("brkga_get_chromosome_size",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));
        getEliteSize = dc("brkga_get_elite_size",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));
        getNumMutants = dc("brkga_get_num_mutants",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));
        getMaxThreads = dc("brkga_get_max_threads",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));
        getPopulationSize = dc("brkga_get_population_size",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        setBiasCustomFunction = dc("brkga_set_bias_custom_function",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));
        configSetCustomDistance = dc("brkga_config_set_custom_distance",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        setShakingMethod = dc("brkga_set_shaking_method",
            FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));
        shakingNumPopulations = dc("brkga_shaking_num_populations",
            FunctionDescriptor.of(JAVA_INT, ADDRESS));
        shakingPopulationSize = dc("brkga_shaking_population_size",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
        shakingChromosomeSize = dc("brkga_shaking_chromosome_size",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
        shakingGetAllele = dc("brkga_shaking_get_allele",
            FunctionDescriptor.of(JAVA_DOUBLE, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
        shakingSetAllele = dc("brkga_shaking_set_allele",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_DOUBLE));
        shakingMark = dc("brkga_shaking_mark",
            FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT));
    }

    private MethodHandle dc(String name, FunctionDescriptor desc) {
        MemorySegment symbol = lookup.find(name).orElseThrow(
            () -> new RuntimeException("Symbol not found in bridge: " + name));
        return LINKER.downcallHandle(symbol, desc);
    }

    //----------------------------- Factories --------------------------------//

    /** Maximum number of objectives supported by the generic bridge library. */
    public static final int CAP = 20;

    /**
     * Loads the bridge library for the given number of objectives.
     *
     * <p>It prefers a dedicated tuple bridge ({@code libbrkga_bridge_<n>.so}) when
     * present; otherwise it falls back to the generic bridge
     * ({@code libbrkga_bridge_generic.so}) for any {@code numObjectives} up to
     * {@link #CAP}.</p>
     *
     * <p>A bridge library is located in two ways, in order:</p>
     * <ol>
     *   <li><b>Filesystem.</b> The directory given by the {@code brkga.bridge.dir}
     *       system property (default {@code native}). Set it to point at your own
     *       recompiled binaries (e.g. a different objective count or mating mode).</li>
     *   <li><b>Bundled resource.</b> If the filesystem lookup misses, the library
     *       is extracted from inside the JAR ({@code /native/<platform>/<file>}) to
     *       a temporary file. This is what lets the published artifact run with no
     *       C++ toolchain.</li>
     * </ol>
     *
     * <p>Set {@code brkga.bridge.force_generic=true} to always use the generic
     * bridge.</p>
     *
     * @param numObjectives the number of objective values the decoder produces ({@code >= 1}).
     * @return a binding to the appropriate bridge library.
     * @throws IllegalArgumentException if {@code numObjectives < 1}.
     * @throws RuntimeException if no suitable bridge library is available.
     */
    public static NativeBrkga forObjectives(int numObjectives) {
        if(numObjectives < 1)
            throw new IllegalArgumentException("numObjectives must be >= 1");
        boolean forceGeneric = Boolean.getBoolean("brkga.bridge.force_generic");
        if(!forceGeneric) {
            Path dedicated = resolveLib("libbrkga_bridge_" + numObjectives + ".so");
            if(dedicated != null)
                return new NativeBrkga(dedicated, numObjectives);
        }
        if(numObjectives <= CAP) {
            Path generic = resolveLib("libbrkga_bridge_generic.so");
            if(generic != null)
                return new NativeBrkga(generic, numObjectives);
        }
        throw new RuntimeException(
            "No bridge available for " + numObjectives + " objectives. "
            + (numObjectives > CAP
                ? "Exceeds the generic CAP (" + CAP + "); build a dedicated "
                  + "libbrkga_bridge_" + numObjectives + ".so (see "
                  + "JAVA_GUIDE.md#number-of-objectives)."
                : "Build the native libraries (make -C native), or run on a "
                  + "supported platform (Linux x86-64) so the bundled binaries apply."));
    }

    /** Loads the single-objective bridge ({@code forObjectives(1)}). */
    public static NativeBrkga single() { return forObjectives(1); }

    /** Loads the two-objective bridge ({@code forObjectives(2)}). */
    public static NativeBrkga multi() { return forObjectives(2); }

    /** Resource sub-path for the bundled native libraries (Linux x86-64 only). */
    private static final String NATIVE_PLATFORM = "linux-x86-64";

    /** Cache of resource libraries already extracted to a temp file this JVM. */
    private static final java.util.Map<String, Path> EXTRACTED =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Resolves a bridge library file name to a real filesystem path, or returns
     * {@code null} if it is available neither on the filesystem (under
     * {@code brkga.bridge.dir}, default {@code native}) nor as a bundled resource.
     */
    private static Path resolveLib(String fileName) {
        String dir = System.getProperty("brkga.bridge.dir", "native");
        Path onDisk = Path.of(dir, fileName);
        if(Files.exists(onDisk))
            return onDisk;
        return extractResource(fileName);
    }

    /**
     * Extracts a bundled native library ({@code /native/<platform>/<file>}) to a
     * temporary file (once per JVM), returning its path, or {@code null} if the
     * resource is not bundled (e.g. unsupported platform).
     */
    private static Path extractResource(String fileName) {
        return EXTRACTED.computeIfAbsent(fileName, fn -> {
            String resource = "/native/" + NATIVE_PLATFORM + "/" + fn;
            try(java.io.InputStream in = NativeBrkga.class.getResourceAsStream(resource)) {
                if(in == null)
                    return null;   // not bundled for this platform
                Path tmpDir = Files.createTempDirectory("brkga-native-");
                tmpDir.toFile().deleteOnExit();
                Path out = tmpDir.resolve(fn);
                Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                out.toFile().deleteOnExit();
                return out;
            }
            catch(java.io.IOException e) {
                throw new RuntimeException("Failed to extract bundled native library "
                    + resource, e);
            }
        });
    }

    //------------------------------- Upcalls --------------------------------//

    private static final FunctionDescriptor DECODE_DESCRIPTOR =
        FunctionDescriptor.ofVoid(ADDRESS, JAVA_LONG, JAVA_INT, ADDRESS, JAVA_LONG,
            ADDRESS, ADDRESS);
    private static final FunctionDescriptor INFO_DESCRIPTOR =
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS);

    public interface DecodeCallback {
        void decode(MemorySegment chromosome, long size, int rewrite,
                    MemorySegment extra, long extraSize,
                    MemorySegment userData, MemorySegment outFitness);
    }

    public interface InfoCallback {
        int call(MemorySegment status, MemorySegment userData);
    }

    public static MemorySegment makeDecodeStub(DecodeCallback callback) {
        try {
            MethodHandle handle = MethodHandles.lookup().bind(callback, "decode",
                MethodType.methodType(void.class, MemorySegment.class, long.class,
                    int.class, MemorySegment.class, long.class,
                    MemorySegment.class, MemorySegment.class));
            return LINKER.upcallStub(handle, DECODE_DESCRIPTOR, GLOBAL_ARENA);
        } catch(ReflectiveOperationException e) {
            throw new RuntimeException("Cannot build decode upcall stub", e);
        }
    }

    public static MemorySegment makeInfoStub(InfoCallback callback) {
        try {
            MethodHandle handle = MethodHandles.lookup().bind(callback, "call",
                MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class));
            return LINKER.upcallStub(handle, INFO_DESCRIPTOR, GLOBAL_ARENA);
        } catch(ReflectiveOperationException e) {
            throw new RuntimeException("Cannot build info upcall stub", e);
        }
    }

    //----------------------- CUSTOM-callback upcalls ------------------------//

    public interface BiasCallback { double call(int rank, MemorySegment ud); }
    public interface DistanceCallback {
        double call(MemorySegment v1, MemorySegment v2, long size, MemorySegment ud);
    }
    public interface AffectKeyCallback {
        int call(double key1, double key2, MemorySegment ud);
    }
    public interface AffectBlockCallback {
        int call(MemorySegment v1, MemorySegment v2, long block, MemorySegment ud);
    }
    public interface ShakingCallback {
        void call(double lower, double upper, MemorySegment populations,
                  MemorySegment shaken, MemorySegment ud);
    }

    public static MemorySegment makeBiasStub(BiasCallback cb) {
        return stub(cb, "call",
            MethodType.methodType(double.class, int.class, MemorySegment.class),
            FunctionDescriptor.of(JAVA_DOUBLE, JAVA_INT, ADDRESS));
    }

    public static MemorySegment makeDistanceStub(DistanceCallback cb) {
        return stub(cb, "call",
            MethodType.methodType(double.class, MemorySegment.class,
                MemorySegment.class, long.class, MemorySegment.class),
            FunctionDescriptor.of(JAVA_DOUBLE, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));
    }

    public static MemorySegment makeAffectKeyStub(AffectKeyCallback cb) {
        return stub(cb, "call",
            MethodType.methodType(int.class, double.class, double.class, MemorySegment.class),
            FunctionDescriptor.of(JAVA_INT, JAVA_DOUBLE, JAVA_DOUBLE, ADDRESS));
    }

    public static MemorySegment makeAffectBlockStub(AffectBlockCallback cb) {
        return stub(cb, "call",
            MethodType.methodType(int.class, MemorySegment.class,
                MemorySegment.class, long.class, MemorySegment.class),
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS));
    }

    public static MemorySegment makeShakingStub(ShakingCallback cb) {
        return stub(cb, "call",
            MethodType.methodType(void.class, double.class, double.class,
                MemorySegment.class, MemorySegment.class, MemorySegment.class),
            FunctionDescriptor.ofVoid(JAVA_DOUBLE, JAVA_DOUBLE, ADDRESS, ADDRESS, ADDRESS));
    }

    private static MemorySegment stub(Object target, String method,
                                      MethodType type, FunctionDescriptor desc) {
        try {
            MethodHandle handle = MethodHandles.lookup().bind(target, method, type);
            return LINKER.upcallStub(handle, desc, GLOBAL_ARENA);
        } catch(ReflectiveOperationException e) {
            throw new RuntimeException("Cannot build upcall stub for " + method, e);
        }
    }

    //--------------------------- Formatting helpers -------------------------//

    /**
     * Renders a {@link BrkgaParams} exactly as the C++ {@code operator<<} would,
     * delegating to the native formatter.
     *
     * @param params the parameters to format.
     * @return the multi-line text rendering used by the C++ examples.
     */
    public String format(BrkgaParams params) {
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment in = temp.allocate(BrkgaParams.LAYOUT);
            params.writeTo(in);
            MemorySegment buf = temp.allocate(8192);
            paramsToString.invoke(in, buf, 8192L);
            return cString(buf);
        } catch(Throwable t) { throw new RuntimeException("params_to_string failed", t); }
    }

    /**
     * Renders a {@link ControlParams} exactly as the C++ {@code operator<<} would,
     * delegating to the native formatter.
     *
     * @param params the control parameters to format.
     * @return the multi-line text rendering used by the C++ examples.
     */
    public String format(ControlParams params) {
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment in = temp.allocate(ControlParams.LAYOUT);
            params.writeTo(in);
            MemorySegment buf = temp.allocate(8192);
            controlParamsToString.invoke(in, buf, 8192L);
            return cString(buf);
        } catch(Throwable t) { throw new RuntimeException("control_params_to_string failed", t); }
    }

    static String cString(MemorySegment buffer) {
        long len = 0;
        while(len < buffer.byteSize()
              && buffer.get(java.lang.foreign.ValueLayout.JAVA_BYTE, len) != 0)
            ++len;
        byte[] bytes = new byte[(int) len];
        for(int i = 0; i < len; ++i)
            bytes[i] = buffer.get(java.lang.foreign.ValueLayout.JAVA_BYTE, i);
        return new String(bytes);
    }
}
