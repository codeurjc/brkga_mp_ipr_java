/******************************************************************************
 * Benchmark.java: times the single-objective TSP optimization for a FIXED
 * number of iterations, to measure the Java/FFM decode-boundary overhead
 * against the all-native C++ baseline (bench_native, same iterations, threads,
 * instance and decoder algorithm).
 *
 * In this Java path the whole BRKGA core runs in the same native code as the
 * C++ baseline; only the decode crosses into Java. With an equivalent decoder,
 * the runtime difference is the decode-boundary overhead.
 *
 * Usage:  <seed> <config-file> <iterations> <tsp-instance-file>
 *
 * JVM properties:
 *   -Dbrkga.num_threads=N            decoding threads (default 1, clean timing)
 *   -Dbrkga.bench.warmup=true        run a short warmup first so the JIT compiles
 *   -Dbrkga.bench.decoder=basic|optimized   which decoder to time (default basic)
 *****************************************************************************/

package bench;

import brkga.AlgorithmStatus;
import brkga.Brkga;
import brkga.BrkgaMpIpr;
import brkga.BrkgaParams;
import brkga.ControlParams;
import brkga.Decoder;
import brkga.Sense;

public class Benchmark {

    public static void main(String[] argv) throws Exception {
        if(argv.length < 4) {
            System.err.println(
                "Usage: Benchmark <seed> <config-file> <iterations>"
                + " <tsp-instance-file>");
            System.exit(1);
        }

        final int seed = Integer.parseInt(argv[0]);
        final String configFile = argv[1];
        final long iterations = Long.parseLong(argv[2]);
        final String instanceFile = argv[3];
        final int numThreads = Integer.getInteger("brkga.num_threads", 1);
        final boolean warmup = Boolean.parseBoolean(
            System.getProperty("brkga.bench.warmup", "true"));
        final String decoderKind =
            System.getProperty("brkga.bench.decoder", "basic");

        TspInstance instance = new TspInstance(instanceFile);
        Brkga brkga = Brkga.single();

        if(warmup) {
            long warmIters = Math.min(iterations, 100);
            runOnce(brkga, instance, configFile, seed, numThreads, warmIters, decoderKind);
        }

        long t0 = System.nanoTime();
        AlgorithmStatus status =
            runOnce(brkga, instance, configFile, seed, numThreads, iterations, decoderKind);
        long t1 = System.nanoTime();

        double elapsedMs = (t1 - t0) / 1e6;
        System.out.printf(
            "JAVA   | decoder=%-9s | threads=%d | nodes=%d | iters=%d "
            + "| time=%.1f ms | best=%.0f%n",
            decoderKind, numThreads, instance.numNodes, iterations, elapsedMs,
            status.bestFitness[0]);
    }

    private static AlgorithmStatus runOnce(Brkga brkga, TspInstance instance,
            String configFile, int seed, int numThreads, long iterations,
            String decoderKind) {
        BrkgaParams brkgaParams = brkga.readConfiguration(configFile).brkgaParams();

        // Fixed-iterations setup (default control params).
        ControlParams control = new ControlParams();
        control.maximumRunningTime = Long.MAX_VALUE;
        control.stallOffset = (int) 0xFFFFFFFFL;

        Decoder decoder = decoderKind.equals("optimized")
            ? new TspDecoderOptimized(instance)
            : new TspDecoder(instance);
        try(BrkgaMpIpr a = new BrkgaMpIpr(brkga, decoder, Sense.MINIMIZE, seed,
                instance.numNodes, brkgaParams, numThreads)) {
            a.setQuiet(true);
            a.setStoppingCriteria(s -> s.currentIteration == iterations);
            return a.run(control);
        }
    }
}
