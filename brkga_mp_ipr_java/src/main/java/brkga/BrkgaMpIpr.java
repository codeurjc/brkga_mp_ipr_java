/******************************************************************************
 * BrkgaMpIpr.java: idiomatic Java wrapper of BRKGA::BRKGA_MP_IPR<Decoder>.
 *
 * Mirrors the public C++ API: constructor with BrkgaParams, callbacks
 * (observer / stopping criteria), warm start, run(), evolve(), pathRelink(),
 * exchangeElite/shake/reset/injectChromosome, and the getters.
 *
 * The decoder is a Decoder run in Java; the native algorithm calls it back
 * through an FFM upcall. Parameters are Java POJOs materialized natively.
 *
 * The CUSTOM callbacks are also exposed: setBiasCustomFunction, setShakingMethod,
 * and CUSTOM (Java-supplied) IPR distance functions.
 *****************************************************************************/

package brkga;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;

/**
 * Multi-Parent Biased Random-key Genetic Algorithm with Implicit Path Relinking
 * (BRKGA-MP-IPR).
 *
 * <p>This class is an idiomatic Java wrapper, built on the Java Foreign Function
 * &amp; Memory (FFM) API, over the C++ template class {@code BRKGA::BRKGA_MP_IPR<Decoder>}.
 * It mirrors the public C++ API: construction with {@link BrkgaParams}, callbacks
 * ({@link NewSolutionObserver} / {@link StoppingCriteria}), warm start, {@link #run},
 * {@link #evolve}, {@link #pathRelink}, {@link #exchangeElite}, {@link #shake},
 * {@link #reset}, {@link #injectChromosome}, and the getters.
 *
 * <p>In the BRKGA-MP-IPR, a population of chromosomes is kept divided between an
 * elite and a non-elite group. During mating, multiple parents are chosen from the
 * elite and the non-elite groups. They are sorted either in non-decreasing order
 * for minimization or non-increasing order for maximization problems. Given this
 * order, a {@link BiasFunction} is applied to the rank of each chromosome, producing
 * a weight for each one. Using a roulette method based on the weights, the
 * chromosomes are combined using a biased crossover.
 *
 * <p>This code also implements the island model, where multiple populations can be
 * evolved in parallel, and migration of individuals between the islands is performed
 * using the {@link #exchangeElite(int)} method.
 *
 * <p>The decoder is a {@link Decoder} run in Java and invoked by the native
 * algorithm through an FFM upcall. A decoder maps a chromosome (Java {@code double[]}
 * of genes in [0,1)) to a solution for the specific problem and returns the value(s)
 * to be used as fitness (a {@code double[]} of length 1 for single-objective, or N for
 * multi-objective problems). The {@code rewrite} flag indicates whether the decoder
 * should rewrite the chromosome in case it implements a local search and modifies the
 * solution decoded from the chromosome.
 *
 * <p><b>Warning:</b> Because this API can decode several chromosomes in parallel
 * (when {@code numThreads &gt; 1}), the user MUST guarantee that the decoder is
 * thread-safe. We recommend keeping writable variables per thread. If thread safety
 * cannot be held, use a single thread for the optimization.
 *
 * <p><b>Implicit Path Relinking:</b> This API also implements Implicit Path Relinking
 * leveraging the decoder capabilities. To perform path relinking, call
 * {@link #pathRelink}, indicating the type of path relinking (direct or
 * permutation-based, see {@link PathRelinking.Type}), the selection criteria (best
 * solution or random elite, see {@link PathRelinking.Selection}), and the distance
 * function (HAMMING, KENDALLTAU, or a CUSTOM {@link DistanceFunction} supplied at
 * construction). In the presence of multiple populations, path relinking is performed
 * between elite chromosomes from different populations in a circular fashion. The
 * algorithm tries to find a pair of base and guiding solutions at least a minimum
 * distance apart; if no such pair can be found in any population it declares failure,
 * which indicates that the populations are very homogeneous. The decoder is always
 * called with {@code rewrite = false} during path relinking (otherwise the path
 * between solutions would be lost), and is called once with {@code rewrite = true} on
 * the best chromosome found at the end.
 *
 * <p><b>Multi-start:</b> This API can also be used as a simple multi-start algorithm
 * without evolution. To do that, pass {@code evolution = false} in the constructor.
 * This makes the elite set have a single individual and the number of mutants
 * {@code n - 1}, where {@code n} is the size of the population, disabling the
 * evolutionary process completely.
 *
 * <p><b>Initial population:</b> The user may provide a set of initial solutions to
 * warm start the algorithm via {@link #setInitialPopulation(double[][])}. In general,
 * such solutions are created using other (fast) heuristics and help the convergence
 * of the algorithm.
 *
 * <p><b>About multi-threading:</b> Since the decoding process can be complex and
 * lengthy, it is recommended that the number of threads used does NOT exceed the
 * number of physical cores in the machine. This improves overall performance
 * drastically, avoiding cache misses and racing conditions. Note that the number of
 * threads is also tied to memory utilization and should be monitored carefully.
 *
 * <p>This API was based on the code by Rodrigo Franco Toso, Sep 15, 2011
 * (<a href="http://github.com/rfrancotoso/brkgaAPI">http://github.com/rfrancotoso/brkgaAPI</a>),
 * and on the C++ BRKGA-MP-IPR by Carlos Eduardo de Andrade.
 *
 * @see BrkgaParams
 * @see ControlParams
 * @see AlgorithmStatus
 */
public final class BrkgaMpIpr implements AutoCloseable {

    private static final long ERR_LEN = 1024;

    private final NativeBrkga lib;
    private final int numObjectives;
    private final int chromosomeSize;
    private final MemorySegment algo;
    private final MemorySegment cfg;   // native ConfigurationContext (params)

    @SuppressWarnings("unused")
    private final List<Object> keepAlive = new ArrayList<>();

    /**
     * Builds the algorithm and its data structures, with the evolutionary mechanism
     * enabled and without a custom IPR distance function.
     *
     * <p>This is a convenience constructor equivalent to calling the full constructor
     * with {@code evolution = true} and {@code customDistance = null}.
     *
     * @param brkga the {@link Brkga} handle (it carries the number of objectives and
     *              the loaded native bridge); this argument has no C++ equivalent.
     * @param decoder a reference to the decoder object. The algorithm uses this object
     *                directly for decoding; it MUST be thread-safe when
     *                {@code numThreads &gt; 1}.
     * @param sense the optimization sense (maximization or minimization).
     * @param seed the seed for the random number generator.
     * @param chromosomeSize number of genes in each chromosome.
     * @param params {@link BrkgaParams} the BRKGA and IPR parameters object loaded from
     *               a configuration file or manually created. All the data is copied.
     * @param numThreads number of threads used to perform parallel decoding.
     * @throws RuntimeException if some parameter or combination of parameters does not
     *                          fit, wrapping the native error message.
     */
    public BrkgaMpIpr(Brkga brkga, Decoder decoder, Sense sense, int seed,
                      int chromosomeSize, BrkgaParams params, int numThreads) {
        this(brkga, decoder, sense, seed, chromosomeSize, params, numThreads, true, null);
    }

    /**
     * Builds the algorithm and its data structures, controlling the evolutionary
     * mechanism, and without a custom IPR distance function.
     *
     * <p>This is a convenience constructor equivalent to calling the full constructor
     * with {@code customDistance = null}.
     *
     * @param brkga the {@link Brkga} handle (it carries the number of objectives and
     *              the loaded native bridge); this argument has no C++ equivalent.
     * @param decoder a reference to the decoder object. The algorithm uses this object
     *                directly for decoding; it MUST be thread-safe when
     *                {@code numThreads &gt; 1}.
     * @param sense the optimization sense (maximization or minimization).
     * @param seed the seed for the random number generator.
     * @param chromosomeSize number of genes in each chromosome.
     * @param params {@link BrkgaParams} the BRKGA and IPR parameters object. All the
     *               data is copied.
     * @param numThreads number of threads used to perform parallel decoding.
     * @param evolution if {@code false}, no evolution is performed but only chromosome
     *                  decoding; very useful to emulate a multi-start algorithm. Maps
     *                  to the C++ {@code evolutionary_mechanism_on} argument.
     * @throws RuntimeException if some parameter or combination of parameters does not
     *                          fit, wrapping the native error message.
     */
    public BrkgaMpIpr(Brkga brkga, Decoder decoder, Sense sense, int seed,
                      int chromosomeSize, BrkgaParams params, int numThreads,
                      boolean evolution) {
        this(brkga, decoder, sense, seed, chromosomeSize, params, numThreads, evolution, null);
    }

    /**
     * Builds the algorithm and its data structures, with a CUSTOM IPR distance
     * function.
     *
     * <p>The {@code customDistance} function is required when
     * {@code params.prDistanceFunctionType == CUSTOM} and the algorithm performs
     * implicit path relinking, either through {@link #run(ControlParams)} with a
     * positive IPR interval or through an explicit
     * {@link #pathRelink(PathRelinking.Type, PathRelinking.Selection, PathRelinking.DistanceFunctionType, int, double, long, long, double)
     * pathRelink(..., CUSTOM, ...)} call.
     *
     * @param brkga the {@link Brkga} handle (it carries the number of objectives and
     *              the loaded native bridge); this argument has no C++ equivalent.
     * @param decoder a reference to the decoder object. The algorithm uses this object
     *                directly for decoding; it MUST be thread-safe when
     *                {@code numThreads &gt; 1}.
     * @param sense the optimization sense (maximization or minimization).
     * @param seed the seed for the random number generator.
     * @param chromosomeSize number of genes in each chromosome.
     * @param params {@link BrkgaParams} the BRKGA and IPR parameters object. All the
     *               data is copied.
     * @param numThreads number of threads used to perform parallel decoding.
     * @param evolution if {@code false}, no evolution is performed but only chromosome
     *                  decoding; very useful to emulate a multi-start algorithm. Maps
     *                  to the C++ {@code evolutionary_mechanism_on} argument.
     * @param customDistance the custom {@link DistanceFunction} used by IPR when
     *                       {@code params.prDistanceFunctionType == CUSTOM}; may be
     *                       {@code null} when no custom distance is needed.
     * @throws RuntimeException if some parameter or combination of parameters does not
     *                          fit, wrapping the native error message.
     */
    public BrkgaMpIpr(Brkga brkga, Decoder decoder, Sense sense, int seed,
                      int chromosomeSize, BrkgaParams params, int numThreads,
                      boolean evolution, DistanceFunction customDistance) {
        NativeBrkga lib = brkga.lib;
        this.lib = lib;
        this.numObjectives = lib.numObjectives;
        this.chromosomeSize = chromosomeSize;

        NativeBrkga.DecodeCallback callback =
            (chromosome, size, rewrite, extra, extraSize, userData, outFitness) -> {
                MemorySegment chr = chromosome.reinterpret(size * Double.BYTES);
                Chromosome c = new Chromosome(chr, (int) size, extra, (int) extraSize);
                double[] fitness = decoder.decode(c, rewrite != 0);
                MemorySegment out = outFitness.reinterpret((long) numObjectives * Double.BYTES);
                for(int i = 0; i < numObjectives; ++i)
                    out.setAtIndex(JAVA_DOUBLE, i, fitness[i]);
            };
        MemorySegment decodeStub = NativeBrkga.makeDecodeStub(callback);
        keepAlive.add(callback);
        keepAlive.add(decodeStub);

        try(Arena temp = Arena.ofConfined()) {
            // Materialize the BrkgaParams in a native configuration context.
            this.cfg = (MemorySegment) lib.configCreate.invoke();
            MemorySegment bp = temp.allocate(BrkgaParams.LAYOUT);
            params.writeTo(bp);
            lib.configSetBrkgaParams.invoke(cfg, bp);

            // Register the CUSTOM IPR distance functor before construction.
            if(customDistance != null)
                registerCustomDistance(customDistance);

            MemorySegment errbuf = temp.allocate(ERR_LEN);
            MemorySegment created = (MemorySegment) lib.create.invoke(
                cfg, sense.value, seed, chromosomeSize, numThreads,
                evolution ? 1 : 0, numObjectives, decodeStub, MemorySegment.NULL,
                errbuf, ERR_LEN);
            if(created.address() == 0) {
                lib.freeConfiguration.invoke(cfg);
                throw new RuntimeException(NativeBrkga.cString(errbuf));
            }
            this.algo = created;
        }
        catch(RuntimeException e) { throw e; }
        catch(Throwable t) { throw new RuntimeException("brkga_create failed", t); }
    }

    //----------------------------- Callbacks --------------------------------//

    /**
     * Adds a callback called when the best solution is improved.
     *
     * <p>The observer receives an {@link AlgorithmStatus} and returns {@code true} to
     * keep the optimization running, or {@code false} to stop it immediately. You may
     * add as many observers as you want; they are called in the order they are added.
     *
     * @param observer the {@link NewSolutionObserver} callback to be invoked on each
     *                 improvement of the best solution.
     * @throws RuntimeException if the native call fails, wrapping the native error
     *                          message.
     */
    public void addNewSolutionObserver(NewSolutionObserver observer) {
        NativeBrkga.InfoCallback cb = (status, ud) ->
            observer.onNewSolution(readStatus(status)) ? 1 : 0;
        MemorySegment stub = NativeBrkga.makeInfoStub(cb);
        keepAlive.add(cb); keepAlive.add(stub);
        invoke(() -> lib.addNewSolutionObserver.invoke(algo, stub, MemorySegment.NULL),
               "addNewSolutionObserver");
    }

    /**
     * Sets a custom stopping criteria supplied by the user.
     *
     * <p>The algorithm always tests for the maximum running time and for the maximum
     * stalled iterations/generations given by {@link ControlParams} independently of
     * the stopping criteria function supplied here. This is especially important when
     * activating the implicit path relinking, which is very time consuming.
     *
     * <p><b>Warning:</b> If you are using IPR, we STRONGLY RECOMMEND setting a maximum
     * time since this is the core stopping criteria for IPR.
     *
     * @param criteria a {@link StoppingCriteria} callback that receives the current
     *                 {@link AlgorithmStatus} and returns {@code true} when the
     *                 algorithm must stop.
     * @throws RuntimeException if the native call fails, wrapping the native error
     *                          message.
     */
    public void setStoppingCriteria(StoppingCriteria criteria) {
        NativeBrkga.InfoCallback cb = (status, ud) ->
            criteria.shouldStop(readStatus(status)) ? 1 : 0;
        MemorySegment stub = NativeBrkga.makeInfoStub(cb);
        keepAlive.add(cb); keepAlive.add(stub);
        invoke(() -> lib.setStoppingCriteria.invoke(algo, stub, MemorySegment.NULL),
               "setStoppingCriteria");
    }

    /**
     * Enables or disables the native logging output.
     *
     * <p>This is a Java-specific method with no C++ equivalent. The underlying native
     * algorithm logs progress information to standard output; calling this method with
     * {@code quiet = true} silences that output.
     *
     * @param quiet {@code true} to silence the native logging, {@code false} to enable
     *              it.
     * @throws RuntimeException if the native call fails, wrapping the native error
     *                          message.
     */
    public void setQuiet(boolean quiet) {
        invoke(() -> lib.setQuiet.invoke(algo, quiet ? 1 : 0), "setQuiet");
    }

    /**
     * Sets individuals as the initial population to work as warm-starters.
     *
     * <p>Such individuals can be obtained from solutions of external procedures such
     * as fast heuristics, other metaheuristics, or even relaxations from a mixed
     * integer programming model that models the problem. As many individuals as
     * possible are assigned across all populations; extra individuals are disregarded.
     *
     * @param chromosomes a set of individuals encoded as chromosomes, where each
     *                    chromosome is a {@code double[]} of genes in [0,1).
     * @throws RuntimeException if the native call fails, wrapping the native error
     *                          message.
     */
    public void setInitialPopulation(double[][] chromosomes) {
        int num = chromosomes.length;
        int size = num > 0 ? chromosomes[0].length : 0;
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment data = temp.allocate((long) num * size * Double.BYTES);
            for(int k = 0; k < num; ++k)
                for(int i = 0; i < size; ++i)
                    data.setAtIndex(JAVA_DOUBLE, (long) k * size + i, chromosomes[k][i]);
            lib.setInitialPopulation.invoke(algo, data, (long) num, (long) size);
        }
        catch(Throwable t) { throw new RuntimeException("setInitialPopulation failed", t); }
    }

    //--------------------------- CUSTOM callbacks ---------------------------//

    /**
     * Sets a custom bias function used to build the selection probabilities.
     *
     * <p>This mirrors the C++ {@code setBiasCustomFunction(func)} (which sets the bias
     * type to {@code CUSTOM}). The function must be a positive non-increasing function,
     * i.e. {@code f: N+ -> R+} such that {@code f(i) >= 0} and {@code f(i) >= f(i+1)}
     * for {@code i} in {@code [1, ..., total_parents]}. For example, returning
     * {@code 1.0 / (x * x)} sets an inverse quadratic function.
     *
     * @param bias a {@link BiasFunction} that maps a (one-based) rank to its bias
     *             weight; must be a positive non-increasing function.
     * @throws RuntimeException if the function is not a non-decreasing positive
     *                          function, wrapping the native error message.
     */
    public void setBiasCustomFunction(BiasFunction bias) {
        NativeBrkga.BiasCallback cb = (rank, ud) -> bias.rankBias(rank);
        MemorySegment stub = NativeBrkga.makeBiasStub(cb);
        keepAlive.add(cb); keepAlive.add(stub);
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment errbuf = temp.allocate(ERR_LEN);
            int rc = (int) lib.setBiasCustomFunction.invoke(algo, stub,
                MemorySegment.NULL, errbuf, ERR_LEN);
            if(rc != 0) throw new RuntimeException(NativeBrkga.cString(errbuf));
        }
        catch(RuntimeException e) { throw e; }
        catch(Throwable t) { throw new RuntimeException("setBiasCustomFunction failed", t); }
    }

    /**
     * Sets a custom shaking procedure.
     *
     * <p>This mirrors the C++ {@code setShakingMethod(func)}. The supplied
     * {@link ShakingMethod} receives the lower and upper bounds for allele values and
     * the set of populations, and may alter chromosomes (for example, to implement a
     * standard mutation), reporting which (population, chromosome) pairs were changed.
     * For more details, see {@code BrkgaParams.customShaking}.
     *
     * @param method the {@link ShakingMethod} callback implementing the shaking
     *               procedure.
     * @throws RuntimeException if the native call fails, wrapping the native error
     *                          message.
     */
    public void setShakingMethod(ShakingMethod method) {
        NativeBrkga.ShakingCallback cb = (lower, upper, pops, shaken, ud) ->
            method.shake(lower, upper, new ShakingPopulations(lib, pops, shaken));
        MemorySegment stub = NativeBrkga.makeShakingStub(cb);
        keepAlive.add(cb); keepAlive.add(stub);
        invoke(() -> lib.setShakingMethod.invoke(algo, stub, MemorySegment.NULL),
               "setShakingMethod");
    }

    /// Registers the CUSTOM IPR distance functor on the configuration (internal).
    private void registerCustomDistance(DistanceFunction distance) throws Throwable {
        NativeBrkga.DistanceCallback d = (v1, v2, size, ud) ->
            distance.distance(new Chromosome(v1.reinterpret(size * Double.BYTES), (int) size),
                              new Chromosome(v2.reinterpret(size * Double.BYTES), (int) size));
        NativeBrkga.AffectKeyCallback ak = (k1, k2, ud) ->
            distance.affectSolution(k1, k2) ? 1 : 0;
        NativeBrkga.AffectBlockCallback ab = (v1, v2, block, ud) ->
            distance.affectSolutionBlock(
                new Chromosome(v1.reinterpret(block * Double.BYTES), (int) block),
                new Chromosome(v2.reinterpret(block * Double.BYTES), (int) block),
                (int) block) ? 1 : 0;
        MemorySegment dStub = NativeBrkga.makeDistanceStub(d);
        MemorySegment akStub = NativeBrkga.makeAffectKeyStub(ak);
        MemorySegment abStub = NativeBrkga.makeAffectBlockStub(ab);
        keepAlive.add(d); keepAlive.add(ak); keepAlive.add(ab);
        keepAlive.add(dStub); keepAlive.add(akStub); keepAlive.add(abStub);
        lib.configSetCustomDistance.invoke(cfg, dStub, akStub, abStub, MemorySegment.NULL);
    }

    //----------------------------- Execution --------------------------------//

    /**
     * Runs the full framework, performing evolution, path relinking, exchanges,
     * shakes, and resets according to the parameters.
     *
     * <p>This method uses all facilities of the BRKGA-MP-IPR library, providing a
     * comprehensive single-entry point. The main loop evolves one generation per
     * iteration and calls the other procedures based on the number of stalled
     * iterations (the number of iterations without improvement in the best solution)
     * and the user thresholds in {@link ControlParams}. The order in which
     * {@link #pathRelink}, {@link #exchangeElite}, {@link #shake}, and {@link #reset}
     * are called depends on those thresholds.
     *
     * <p>For path relinking, the block size is computed as
     * {@code ceil(alpha * sqrt(p))}, where {@code alpha} is
     * {@code BrkgaParams.alphaBlockSize} and {@code p} is
     * {@code BrkgaParams.populationSize}. If the size is larger than the chromosome
     * size, it is set to half of the chromosome size.
     *
     * <p>Unlike the C++ API, this Java wrapper does not take an output stream argument:
     * native logging goes to standard output and can be silenced with
     * {@link #setQuiet(boolean)}.
     *
     * <p><b>Note:</b> The algorithm always tests against the maximum running time and
     * the maximum stalled iterations/generations given by {@link ControlParams},
     * independently of any user-supplied stopping criteria. This is especially
     * important when activating implicit path relinking, which is very time consuming.
     * If you are using IPR, we STRONGLY RECOMMEND setting a maximum time since this is
     * the core stopping criteria for IPR.
     *
     * <p><b>Warning:</b> The decoding is done in parallel using threads, and the user
     * MUST guarantee that the decoder is thread-safe. If such property cannot be held,
     * use a single thread for the optimization.
     *
     * @param control the {@link ControlParams} that control the algorithm flow, such
     *                as when to call exchanges, shakes, and IPR.
     * @return the last {@link AlgorithmStatus} before the stopping criteria are met.
     * @throws RuntimeException wrapping the native error message in the following
     *                          cases: IPR is active (positive IPR interval) but the
     *                          distance function is not set; shaking is active and set
     *                          to {@code CUSTOM} but no custom shaking procedure was
     *                          supplied; or shaking is active but the intensity bounds
     *                          are out of range (must be in {@code (0.0, 1.0]} with
     *                          lower bound &lt;= upper bound).
     */
    public AlgorithmStatus run(ControlParams control) {
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment cp = temp.allocate(ControlParams.LAYOUT);
            control.writeTo(cp);
            lib.configSetControlParams.invoke(cfg, cp);

            MemorySegment out = temp.allocate(AlgorithmStatus.LAYOUT);
            MemorySegment errbuf = temp.allocate(ERR_LEN);
            int rc = (int) lib.run.invoke(algo, cfg, out, errbuf, ERR_LEN);
            if(rc != 0) throw new RuntimeException(NativeBrkga.cString(errbuf));
            return AlgorithmStatus.readFrom(out);
        }
        catch(RuntimeException e) { throw e; }
        catch(Throwable t) { throw new RuntimeException("run failed", t); }
    }

    /**
     * Evolves the current populations following the guidelines of Multi-parent BRKGAs.
     *
     * <p><b>Warning:</b> The decoding is done in parallel using threads, and the user
     * MUST guarantee that the decoder is thread-safe. If such property cannot be held,
     * use a single thread for the optimization.
     *
     * @param generations number of generations to be evolved. Must be larger than
     *                    zero.
     * @throws RuntimeException if the number of generations is zero, wrapping the
     *                          native error message.
     */
    public void evolve(int generations) {
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment errbuf = temp.allocate(ERR_LEN);
            int rc = (int) lib.evolve.invoke(algo, generations, errbuf, ERR_LEN);
            if(rc != 0) throw new RuntimeException(NativeBrkga.cString(errbuf));
        }
        catch(RuntimeException e) { throw e; }
        catch(Throwable t) { throw new RuntimeException("evolve failed", t); }
    }

    /**
     * Evolves the current populations by a single generation.
     *
     * <p>Convenience method equivalent to {@link #evolve(int) evolve(1)}.
     *
     * <p><b>Warning:</b> The decoding is done in parallel using threads, and the user
     * MUST guarantee that the decoder is thread-safe.
     *
     * @throws RuntimeException if the native call fails, wrapping the native error
     *                          message.
     */
    public void evolve() { evolve(1); }

    //--------------------------- Path relinking -----------------------------//

    /**
     * Performs path relinking between elite solutions that are at least a given
     * minimum distance apart. The local/loaded parameters are ignored in favor of the
     * supplied ones.
     *
     * <p>In the presence of multiple populations, path relinking is performed between
     * elite chromosomes from different populations, in a circular fashion. For three
     * populations, the framework performs three path relinkings: between populations 1
     * and 2, between 2 and 3, and between 3 and 1. With a single population, both base
     * and guiding individuals are sampled from the elite set of that population.
     *
     * <p>The algorithm tries to find a pair of base and guiding solutions with a
     * minimum distance given by the distance function. If this is not possible, a new
     * pair is sampled (without replacement) and tested against the distance. If no such
     * pair can be found for the given populations, the algorithm skips to the next pair
     * of populations; if no pairs are found at all, it declares failure, indicating
     * very homogeneous populations.
     *
     * <p>If the found solution is the best so far, IPR replaces the worst solution by
     * it. Otherwise, IPR computes the distance between the found solution and all other
     * solutions in the elite set, and replaces the worst solution by it only if the
     * found solution is at least {@code minimumDistance} from all of them.
     *
     * <p>The decoder is always called with {@code rewrite = false} during path
     * relinking (otherwise the path between solutions would be lost), and once with
     * {@code rewrite = true} on the best chromosome found at the end. This method is a
     * multi-thread implementation that builds a list of candidates and decodes them in
     * parallel; note that {@code O(chromosomeSize^2 / blockSize)} additional memory is
     * needed to build the candidates, which can be costly for very large chromosomes.
     *
     * <p>The built-in distance is selected via {@code distType}
     * ({@link PathRelinking.DistanceFunctionType#HAMMING} or
     * {@link PathRelinking.DistanceFunctionType#KENDALLTAU}); when
     * {@code distType == CUSTOM}, the CUSTOM {@link DistanceFunction} supplied at
     * construction is used.
     *
     * <p><b>Warning:</b> As in {@link #evolve(int)}, the decoding is done in parallel
     * using threads, and the user MUST guarantee that the decoder is thread-safe. If
     * such property cannot be held, use a single thread for the optimization.
     *
     * @param prType type of path relinking to be performed. See
     *               {@link PathRelinking.Type}.
     * @param prSelection which individuals to use for path relinking. See
     *                    {@link PathRelinking.Selection}.
     * @param distType the built-in distance function to use, or
     *                 {@link PathRelinking.DistanceFunctionType#CUSTOM} to use the
     *                 custom {@link DistanceFunction} supplied at construction.
     * @param numberPairs number of chromosome pairs to be tested. If 0, all pairs are
     *                    tested.
     * @param minimumDistance minimum distance between two chromosomes, computed by the
     *                        distance function.
     * @param blockSize number of alleles to be exchanged at once in each iteration. If
     *                  one, the traditional path relinking is performed.
     * @param maxTimeSeconds aborts path relinking when this many seconds elapse. If 0,
     *                       no time limit is imposed.
     * @param percentage defines the size, in percentage, of the path to build. Must be
     *                   in {@code (0, 1]}; 1.0 means 100%.
     * @return a {@link PathRelinking.PathRelinkingResult} depending on the relink
     *         status.
     * @throws RuntimeException if the percentage or size of the path is not in
     *                          {@code (0, 1]}, or if the native call otherwise fails,
     *                          wrapping the native error message.
     */
    public PathRelinking.PathRelinkingResult pathRelink(
            PathRelinking.Type prType, PathRelinking.Selection prSelection,
            PathRelinking.DistanceFunctionType distType, int numberPairs,
            double minimumDistance, long blockSize, long maxTimeSeconds,
            double percentage) {
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment errbuf = temp.allocate(ERR_LEN);
            int rc = (int) lib.pathRelink.invoke(algo, prType.value(),
                prSelection.value(), distType.value(), numberPairs, minimumDistance,
                blockSize, maxTimeSeconds, percentage, errbuf, ERR_LEN);
            if(rc < 0) throw new RuntimeException(NativeBrkga.cString(errbuf));
            return PathRelinking.PathRelinkingResult.fromValue(rc);
        }
        catch(RuntimeException e) { throw e; }
        catch(Throwable t) { throw new RuntimeException("pathRelink failed", t); }
    }

    //--------------------- Population manipulation ---------------------------//

    /**
     * Exchanges elite solutions between the populations.
     *
     * <p>Given a population, the {@code numImmigrants} best solutions are copied to the
     * neighboring populations, replacing their worst solutions. If there is only one
     * population, nothing is done.
     *
     * @param numImmigrants number of elite chromosomes to select from each population.
     * @throws RuntimeException if the number of immigrants is less than one or is
     *                          larger than or equal to
     *                          {@code ceil(populationSize / numIndependentPopulations) - 1},
     *                          wrapping the native error message.
     */
    public void exchangeElite(int numImmigrants) {
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment errbuf = temp.allocate(ERR_LEN);
            int rc = (int) lib.exchangeElite.invoke(algo, numImmigrants, errbuf, ERR_LEN);
            if(rc != 0) throw new RuntimeException(NativeBrkga.cString(errbuf));
        }
        catch(RuntimeException e) { throw e; }
        catch(Throwable t) { throw new RuntimeException("exchangeElite failed", t); }
    }

    /**
     * Performs a shaking in the chosen population.
     *
     * @param intensity the intensity of the shaking.
     * @param type the shaking move type, either {@code CHANGE} or {@code SWAP}.
     * @param populationIndex the index of the population to be shaken. If
     *                        {@code populationIndex >= numIndependentPopulations}, all
     *                        populations are shaken.
     * @throws RuntimeException if the native call fails, wrapping the native error
     *                          message.
     */
    public void shake(int intensity, ShakingType type, int populationIndex) {
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment errbuf = temp.allocate(ERR_LEN);
            int rc = (int) lib.shake.invoke(algo, intensity, type.value(),
                populationIndex, errbuf, ERR_LEN);
            if(rc != 0) throw new RuntimeException(NativeBrkga.cString(errbuf));
        }
        catch(RuntimeException e) { throw e; }
        catch(Throwable t) { throw new RuntimeException("shake failed", t); }
    }

    /**
     * Resets all populations with brand new keys.
     *
     * <p>All warm-start solutions provided through
     * {@link #setInitialPopulation(double[][])} are discarded. You may use
     * {@link #injectChromosome(double[], int, int)} to insert those solutions again.
     *
     * @throws RuntimeException if the native call fails, wrapping the native error
     *                          message.
     */
    public void reset() {
        invoke(() -> lib.reset.invoke(algo), "reset");
    }

    /**
     * Injects (replaces) a chromosome of a given population into a given position.
     *
     * <p>The new chromosome replaces the old one, and the decoder is triggered to
     * compute the new fitness. Once done, the population is re-sorted according to the
     * chromosomes' fitness.
     *
     * @param chromosome the chromosome to be injected, as a {@code double[]} of genes
     *                   in [0,1); its length must equal the chromosome size.
     * @param populationIndex the population index.
     * @param position the chromosome position.
     * @throws RuntimeException if {@code populationIndex} is larger than the number of
     *                          populations, or {@code position} is larger than the
     *                          population size, or the chromosome length does not match
     *                          the chromosome size, wrapping the native error message.
     */
    public void injectChromosome(double[] chromosome, int populationIndex, int position) {
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment chr = temp.allocate((long) chromosome.length * Double.BYTES);
            for(int i = 0; i < chromosome.length; ++i)
                chr.setAtIndex(JAVA_DOUBLE, i, chromosome[i]);
            MemorySegment errbuf = temp.allocate(ERR_LEN);
            int rc = (int) lib.injectChromosome.invoke(algo, chr, chromosome.length,
                populationIndex, position, errbuf, ERR_LEN);
            if(rc != 0) throw new RuntimeException(NativeBrkga.cString(errbuf));
        }
        catch(RuntimeException e) { throw e; }
        catch(Throwable t) { throw new RuntimeException("injectChromosome failed", t); }
    }

    //------------------------------- Getters --------------------------------//

    /**
     * Returns the best fitness among all current populations.
     *
     * <p><b>Warning:</b> This method does NOT return the overall best fitness, but the
     * best one within the current population. If {@link #shake(int, ShakingType, int)}
     * or {@link #reset()} is called, the best fitness may be lost in the populations.
     * However, if you are using {@link #run(ControlParams)}, the best fitness is
     * returned by that method; otherwise you must keep track of the best fitness
     * yourself.
     *
     * @return the best fitness as a {@code double[]} (length 1 for single-objective, N
     *         for multi-objective problems).
     * @throws RuntimeException if the native call fails, wrapping the native error
     *                          message.
     */
    public double[] getBestFitness() {
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment out = temp.allocate((long) numObjectives * Double.BYTES);
            lib.getBestFitness.invoke(algo, out);
            return out.toArray(JAVA_DOUBLE);
        }
        catch(Throwable t) { throw new RuntimeException("getBestFitness failed", t); }
    }

    /**
     * Returns the chromosome with the best fitness among all current populations.
     *
     * <p><b>Warning:</b> This method does NOT return the overall best solution, but the
     * best one within the current population. If {@link #shake(int, ShakingType, int)}
     * or {@link #reset()} is called, the overall best may be lost from the populations,
     * so after a {@link #run(ControlParams)} whose control parameters enable shaking or
     * resetting this can return a solution worse than the run's best. To recover the
     * best solution of the last run in that case, use {@link #getLastRunBestChromosome()},
     * whose fitness matches the {@link AlgorithmStatus#bestFitness} returned by
     * {@code run}.
     *
     * @return the best chromosome as a {@code double[]} of genes in [0,1).
     * @throws RuntimeException with message {@code "No best chromosome available"} when
     *                          there is no best chromosome yet, or wrapping the native
     *                          error message if the native call otherwise fails.
     */
    public double[] getBestChromosome() {
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment out = temp.allocate((long) chromosomeSize * Double.BYTES);
            int rc = (int) lib.getBestChromosome.invoke(algo, out, (long) chromosomeSize);
            if(rc != 0) throw new RuntimeException("No best chromosome available");
            return out.toArray(JAVA_DOUBLE);
        }
        catch(RuntimeException e) { throw e; }
        catch(Throwable t) { throw new RuntimeException("getBestChromosome failed", t); }
    }

    /**
     * Returns the best chromosome recorded during the last {@link #run(ControlParams)},
     * the overall incumbent of that run.
     *
     * <p>Unlike {@link #getBestChromosome()}, this is not the current-population best:
     * {@code run} snapshots the incumbent on every improvement, so this survives any
     * {@link #shake(int, ShakingType, int)} or {@link #reset()} triggered during the
     * run. Its fitness equals the {@link AlgorithmStatus#bestFitness} that {@code run}
     * returned. This is the method to use to rebuild the solution a run converged to.
     *
     * @return the last run's best chromosome as a {@code double[]} of genes in [0,1).
     * @throws RuntimeException with message
     *                          {@code "No best chromosome available from the last run"}
     *                          when no run has recorded a best yet (no {@code run} has
     *                          completed at least one improving iteration), or wrapping
     *                          the native error message if the native call otherwise fails.
     */
    public double[] getLastRunBestChromosome() {
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment out = temp.allocate((long) chromosomeSize * Double.BYTES);
            int rc = (int) lib.getLastStatusBestChromosome.invoke(algo, out, (long) chromosomeSize);
            if(rc != 0) throw new RuntimeException("No best chromosome available from the last run");
            return out.toArray(JAVA_DOUBLE);
        }
        catch(RuntimeException e) { throw e; }
        catch(Throwable t) { throw new RuntimeException("getLastRunBestChromosome failed", t); }
    }

    /**
     * Returns a chromosome of the given population.
     *
     * @param populationIndex the population index.
     * @param position the chromosome position, ordered by fitness. The best chromosome
     *                 is located at position 0.
     * @return the requested chromosome as a {@code double[]} of genes in [0,1).
     * @throws RuntimeException if {@code populationIndex} is larger than the number of
     *                          populations, or {@code position} is larger than the
     *                          population size, wrapping the native error message.
     */
    public double[] getChromosome(int populationIndex, int position) {
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment out = temp.allocate((long) chromosomeSize * Double.BYTES);
            MemorySegment errbuf = temp.allocate(ERR_LEN);
            int rc = (int) lib.getChromosome.invoke(algo, populationIndex, position,
                out, chromosomeSize, errbuf, ERR_LEN);
            if(rc != 0) throw new RuntimeException(NativeBrkga.cString(errbuf));
            return out.toArray(JAVA_DOUBLE);
        }
        catch(RuntimeException e) { throw e; }
        catch(Throwable t) { throw new RuntimeException("getChromosome failed", t); }
    }

    /**
     * Returns the capacity of the per-chromosome <em>extra</em> blob: how many
     * auxiliary {@code double} values each chromosome can carry alongside its
     * genes (see {@link Chromosome#extraSize()}). It is a compile-time constant of
     * the native library, uniform across all bridge binaries.
     *
     * @return the extra-blob capacity (number of doubles).
     */
    public int extraCapacity() {
        try { return (int) lib.extraCapacity.invoke(); }
        catch(Throwable t) { throw new RuntimeException("extraCapacity failed", t); }
    }

    /**
     * Returns the inline <em>extra</em> blob of the best chromosome among the
     * current populations — the auxiliary values the {@link Decoder} stored via
     * {@link Chromosome#setExtra(int, double)} when it last decoded that
     * chromosome, recovered <em>without</em> decoding again.
     *
     * <p><b>Consistency / staleness:</b> the returned values reflect the
     * decoder's output for the best chromosome's genes, provided the decoder
     * always fills the whole blob from the genes. They can be stale if the best
     * chromosome's genes were last written by {@link #exchangeElite(int)} or by
     * the incorporation step of path relinking (both copy genes without
     * re-decoding). In a single-population run without path relinking the blob is
     * always consistent. When in doubt, recompute by decoding
     * {@link #getBestChromosome()}. See the user guide for the full rules.</p>
     *
     * @return the best chromosome's extra blob, length {@link #extraCapacity()}.
     * @throws RuntimeException if there is no best chromosome yet.
     */
    public double[] getBestChromosomeExtra() {
        int cap = extraCapacity();
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment out = temp.allocate((long) cap * Double.BYTES);
            int rc = (int) lib.getBestChromosomeExtra.invoke(algo, out, (long) cap);
            if(rc != 0) throw new RuntimeException("No best chromosome available");
            return out.toArray(JAVA_DOUBLE);
        }
        catch(RuntimeException e) { throw e; }
        catch(Throwable t) { throw new RuntimeException("getBestChromosomeExtra failed", t); }
    }

    /**
     * Returns the inline <em>extra</em> blob of a chromosome of the given
     * population (same indexing as {@link #getChromosome(int, int)}). Same
     * staleness caveats as {@link #getBestChromosomeExtra()}.
     *
     * @param populationIndex the population index.
     * @param position the chromosome position, ordered by fitness (0 is the best).
     * @return the chromosome's extra blob, length {@link #extraCapacity()}.
     * @throws RuntimeException if the indices are out of range.
     */
    public double[] getChromosomeExtra(int populationIndex, int position) {
        int cap = extraCapacity();
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment out = temp.allocate((long) cap * Double.BYTES);
            MemorySegment errbuf = temp.allocate(ERR_LEN);
            int rc = (int) lib.getChromosomeExtra.invoke(algo, populationIndex, position,
                out, (long) cap, errbuf, ERR_LEN);
            if(rc != 0) throw new RuntimeException(NativeBrkga.cString(errbuf));
            return out.toArray(JAVA_DOUBLE);
        }
        catch(RuntimeException e) { throw e; }
        catch(Throwable t) { throw new RuntimeException("getChromosomeExtra failed", t); }
    }

    /**
     * Returns the fitness of a chromosome of the given population.
     *
     * @param populationIndex the population index.
     * @param position the chromosome position, ordered by fitness. The best chromosome
     *                 is located at position 0.
     * @return the fitness as a {@code double[]} (length 1 for single-objective, N for
     *         multi-objective problems).
     * @throws RuntimeException if {@code populationIndex} is larger than the number of
     *                          populations, or {@code position} is larger than the
     *                          population size, wrapping the native error message.
     */
    public double[] getFitness(int populationIndex, int position) {
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment out = temp.allocate((long) numObjectives * Double.BYTES);
            MemorySegment errbuf = temp.allocate(ERR_LEN);
            int rc = (int) lib.getFitness.invoke(algo, populationIndex, position, out,
                errbuf, ERR_LEN);
            if(rc != 0) throw new RuntimeException(NativeBrkga.cString(errbuf));
            return out.toArray(JAVA_DOUBLE);
        }
        catch(RuntimeException e) { throw e; }
        catch(Throwable t) { throw new RuntimeException("getFitness failed", t); }
    }

    /**
     * Returns a view of a current {@link Population}.
     *
     * @param populationIndex the population index.
     * @return the requested {@link Population}.
     * @throws RuntimeException if the index is larger than the number of populations,
     *                          wrapping the native error message.
     */
    public Population getCurrentPopulation(int populationIndex) {
        return new Population(this, populationIndex);
    }

    /**
     * Returns a view of the first current {@link Population}.
     *
     * <p>Convenience method equivalent to {@link #getCurrentPopulation(int)
     * getCurrentPopulation(0)}.
     *
     * @return the first {@link Population}.
     */
    public Population getCurrentPopulation() { return getCurrentPopulation(0); }

    /**
     * Returns the number of chromosomes in the given population.
     *
     * @param populationIndex the population index.
     * @return the population size.
     * @throws RuntimeException if the native call fails, wrapping the native error
     *                          message.
     */
    int getPopulationSize(int populationIndex) {
        try { return (int) lib.getPopulationSize.invoke(algo, populationIndex); }
        catch(Throwable t) { throw new RuntimeException("getPopulationSize failed", t); }
    }

    /**
     * Returns a copy of the {@link BrkgaParams} currently in use by the algorithm.
     *
     * @return the current {@link BrkgaParams}.
     * @throws RuntimeException if the native call fails, wrapping the native error
     *                          message.
     */
    public BrkgaParams getBrkgaParams() {
        try(Arena temp = Arena.ofConfined()) {
            MemorySegment out = temp.allocate(BrkgaParams.LAYOUT);
            lib.getAlgoBrkgaParams.invoke(algo, out);
            return BrkgaParams.readFrom(out);
        }
        catch(Throwable t) { throw new RuntimeException("getBrkgaParams failed", t); }
    }

    /**
     * Returns the optimization {@link Sense} (maximization or minimization).
     *
     * @return the optimization {@link Sense}.
     * @throws RuntimeException if the native call fails, wrapping the native error
     *                          message.
     */
    public Sense getOptimizationSense() {
        try { return Sense.fromValue((int) lib.getOptimizationSense.invoke(algo)); }
        catch(Throwable t) { throw new RuntimeException("getOptimizationSense failed", t); }
    }

    /**
     * Returns the number of genes in each chromosome.
     *
     * @return the chromosome size.
     * @throws RuntimeException if the native call fails, wrapping the native error
     *                          message.
     */
    public int getChromosomeSize() {
        try { return (int) lib.getChromosomeSize.invoke(algo); }
        catch(Throwable t) { throw new RuntimeException("getChromosomeSize failed", t); }
    }

    /**
     * Returns the number of elite individuals per population.
     *
     * @return the elite size.
     * @throws RuntimeException if the native call fails, wrapping the native error
     *                          message.
     */
    public int getEliteSize() {
        try { return (int) lib.getEliteSize.invoke(algo); }
        catch(Throwable t) { throw new RuntimeException("getEliteSize failed", t); }
    }

    /**
     * Returns the number of mutants introduced per population at each generation.
     *
     * @return the number of mutants.
     * @throws RuntimeException if the native call fails, wrapping the native error
     *                          message.
     */
    public int getNumMutants() {
        try { return (int) lib.getNumMutants.invoke(algo); }
        catch(Throwable t) { throw new RuntimeException("getNumMutants failed", t); }
    }

    /**
     * Returns the maximum number of threads used to perform parallel decoding.
     *
     * @return the maximum number of threads.
     * @throws RuntimeException if the native call fails, wrapping the native error
     *                          message.
     */
    public int getMaxThreads() {
        try { return (int) lib.getMaxThreads.invoke(algo); }
        catch(Throwable t) { throw new RuntimeException("getMaxThreads failed", t); }
    }

    /**
     * Releases the native resources associated with this algorithm instance.
     *
     * <p>Destroys the underlying native algorithm and frees its configuration context.
     * After this call, the instance must not be used again. This method is invoked
     * automatically when the instance is used as a try-with-resources, since this class
     * implements {@link AutoCloseable}.
     *
     * @throws RuntimeException if releasing the native resources fails, wrapping the
     *                          native error.
     */
    @Override
    public void close() {
        try {
            lib.destroy.invoke(algo);
            lib.freeConfiguration.invoke(cfg);
        }
        catch(Throwable t) { throw new RuntimeException("close failed", t); }
    }

    //---------------------------- Helpers -----------------------------------//

    private static AlgorithmStatus readStatus(MemorySegment status) {
        return AlgorithmStatus.readFrom(
            status.reinterpret(AlgorithmStatus.LAYOUT.byteSize()));
    }

    private interface VoidCall { void run() throws Throwable; }
    private static void invoke(VoidCall call, String name) {
        try { call.run(); }
        catch(Throwable t) { throw new RuntimeException(name + " failed", t); }
    }
}
