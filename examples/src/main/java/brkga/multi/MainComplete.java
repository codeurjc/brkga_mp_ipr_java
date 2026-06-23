/******************************************************************************
 * MainComplete.java: Java port of
 *     examples/tsp/src/multi_obj/main_complete.cpp
 *
 * Multi-objective counterpart of brkga.single.MainComplete: two objectives
 * (cost and largest edge). Same options and structure.
 *
 * Usage:
 *   --config <f> --seed <n> --stop_rule <G|I> --stop_arg <n>
 *   --maxtime <s> --instance <f> [--threads <n>] [--no_evolution]
 *****************************************************************************/

package brkga.multi;

import brkga.AlgorithmStatus;
import brkga.Brkga;
import brkga.BrkgaMpIpr;
import brkga.BrkgaParams;
import brkga.ControlParams;
import brkga.Sense;
import brkga.tsp.GreedyTour;
import brkga.tsp.TspInstance;
import brkga.tsp.WarmStart;
import brkga.tsp.multi.TspDecoder;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainComplete {

    enum StopRule { GENERATIONS, IMPROVEMENT, UNKNOWN }

    private static final SimpleDateFormat CTIME =
        new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", Locale.US);

    static void log(String message) {
        System.out.println("\n[" + CTIME.format(new java.util.Date()) + "] " + message);
    }

    public static void main(String[] argv) {
        String configFile;
        int seed;
        StopRule stopRule;
        long stopArg;
        long maxTime;
        String instanceFile;
        int numThreads = 1;
        boolean performEvolution = true;

        try {
            Map<String, String> args = parseArgs(argv);

            configFile = require(args, "--config");
            seed = (int) Long.parseLong(require(args, "--seed"));
            stopRule = switch(Character.toUpperCase(require(args, "--stop_rule").charAt(0))) {
                case 'G' -> StopRule.GENERATIONS;
                case 'I' -> StopRule.IMPROVEMENT;
                default -> StopRule.UNKNOWN;
            };
            stopArg = Long.parseLong(require(args, "--stop_arg"));
            maxTime = Long.parseLong(require(args, "--maxtime"));
            instanceFile = require(args, "--instance");
            if(args.containsKey("--threads"))
                numThreads = (int) Long.parseLong(args.get("--threads"));
            performEvolution = !args.containsKey("--no_evolution");

            if(stopRule != StopRule.GENERATIONS && stopRule != StopRule.IMPROVEMENT)
                throw new IllegalArgumentException(
                    "Incorrect stop rule. Must be either (G)enerations or (I)terations");
            if(maxTime <= 0)
                throw new IllegalArgumentException(
                    "'maxtime' must be > 0. Given '" + maxTime + "s'");
            if(numThreads == 0 || numThreads > 64)
                throw new IllegalArgumentException(
                    "'num_threads' must be in [1, 64]. Given '" + numThreads + "'");
        }
        catch(Exception e) {
            System.err.println(
                "\n" + "*".repeat(40)
                + "\nERROR: " + e.getMessage()
                + ". Please use -h/--help for correct usage.\n"
                + "*".repeat(40));
            System.exit(1);
            return;
        }

        try {
            log("Experiment started");

            System.out.println(
                "> Instance: '" + instanceFile + "'"
                + "\n> Loading config file: '" + configFile + "'");

            Brkga brkga = Brkga.multi();
            Brkga.Config cfg = brkga.readConfiguration(configFile);
            BrkgaParams brkgaParams = cfg.brkgaParams();
            ControlParams controlParams = cfg.controlParams();

            controlParams.maximumRunningTime = maxTime;
            if(stopRule == StopRule.IMPROVEMENT)
                controlParams.stallOffset = (int) stopArg;

            System.out.println(
                "> Algorithm parameters:\n" + brkga.format(brkgaParams)
                + "> Control parameters:\n" + brkga.format(controlParams)
                + "\n> Seed: " + seed
                + "\n> Stop rule: "
                + (stopRule == StopRule.GENERATIONS ? "Generations" : "Improvement")
                + "\n> Stop argument: " + stopArg
                + "\n> Number of threads for decoding: " + numThreads
                + (!performEvolution
                    ? "\n> Simple multi-start: on (no evolutionary operators)" : ""));

            log("Reading TSP data");
            TspInstance instance = new TspInstance(instanceFile);
            System.out.println("Number of nodes: " + instance.numNodes);

            log("Generating initial tour");
            GreedyTour.Result initialSolution = GreedyTour.greedyTour(instance);
            System.out.println("Initial cost: " + initialSolution.cost());

            log("Building BRKGA");
            brkgaParams.populationSize =
                Math.min(brkgaParams.populationSize, 10 * instance.numNodes);

            final int chromosomeSize = instance.numNodes;
            System.out.println(
                "New population size: " + brkgaParams.populationSize + "\n"
                + "Chromosome size: " + chromosomeSize);

            TspDecoder decoder = new TspDecoder(instance);

            try(BrkgaMpIpr algorithm = new BrkgaMpIpr(
                    brkga, decoder, Sense.MINIMIZE, seed, chromosomeSize,
                    brkgaParams, numThreads, performEvolution)) {

                    algorithm.addNewSolutionObserver(status -> {
                        System.out.println(
                            "* " + status.currentIteration + " | "
                            + status.bestFitness[0] + " | "
                            + status.bestFitness[1] + " | "
                            + status.currentTime + "s");
                        return true;    // Don't stop the optimization.
                    });

                    if(stopRule == StopRule.GENERATIONS) {
                        final long arg = stopArg;
                        algorithm.setStoppingCriteria(
                            status -> status.currentIteration == arg);
                    }

                    log("Injecting initial solution");
                    double[] initialChromosome = WarmStart.buildInitialChromosome(
                        chromosomeSize, initialSolution.tour(), seed);
                    algorithm.setInitialPopulation(new double[][] { initialChromosome });

                    log("Optimizing...");
                    System.out.println("* Iteration | Cost | LargestEdge | CurrentTime");

                    AlgorithmStatus finalStatus = algorithm.run(controlParams);

                    log("End of optimization");
                    System.out.println("\n> Final status:" + finalStatus);

                    double[] bestChromosome = algorithm.getBestChromosome();

                    Integer[] order = new Integer[instance.numNodes];
                    for(int i = 0; i < instance.numNodes; ++i) order[i] = i;
                    java.util.Arrays.sort(order,
                        (a, b) -> Double.compare(bestChromosome[a], bestChromosome[b]));

                    StringBuilder tourLine = new StringBuilder();
                    for(int node : order) tourLine.append(node).append(" ");

                    System.out.println(
                        "\n% Best tour cost, Largest Edge: ("
                        + String.format(Locale.US, "%.0f", finalStatus.bestFitness[0]) + ", "
                        + String.format(Locale.US, "%.0f", finalStatus.bestFitness[1]) + ")"
                        + "\n% Best tour: " + tourLine);

                    String instanceName = baseName(instanceFile);

                    System.out.println(
                        "\n\nInstance,Seed,Cost,LargestEdge,NumNodes,TotalIterations,"
                        + "LastUpdateIteration,TotalTime,LastUpdateTime,"
                        + "LargestIterationOffset,StalledIterations,PRTime,PRCalls,"
                        + "PRNumHomogenities,PRNumPrImprovBest,PRNumImprovElite,"
                        + "NumExchanges,NumShakes,NumResets");

                    System.out.print(String.format(Locale.US,
                        "%s,%d,%.0f,%.0f,%d,%d,%d,%.2f,%.2f,%d,%d,%.2f,%d,%d,%d,%d,%d,%d,%d",
                        instanceName, seed, finalStatus.bestFitness[0],
                        finalStatus.bestFitness[1], instance.numNodes,
                        finalStatus.currentIteration, finalStatus.lastUpdateIteration,
                        finalStatus.currentTime, finalStatus.lastUpdateTime,
                        finalStatus.largestIterationOffset, finalStatus.stalledIterations,
                        finalStatus.pathRelinkTime, finalStatus.numPathRelinkCalls,
                        finalStatus.numHomogenities, finalStatus.numBestImprovements,
                        finalStatus.numEliteImprovements, finalStatus.numExchanges,
                        finalStatus.numShakes, finalStatus.numResets));
                    System.out.flush();
                }
        }
        catch(Exception e) {
            System.err.println(
                "\n" + "*".repeat(40)
                + "\nException Occurred: " + e.getMessage()
                + "\n" + "*".repeat(40));
            System.exit(1);
        }
    }

    //---------------------------- Helpers -----------------------------------//

    private static Map<String, String> parseArgs(String[] argv) {
        Map<String, String> args = new HashMap<>();
        for(int i = 0; i < argv.length; ++i) {
            String a = argv[i];
            if("--no_evolution".equals(a) || "-h".equals(a) || "--help".equals(a)) {
                args.put(a, "true");
            }
            else if(a.startsWith("--") && i + 1 < argv.length) {
                args.put(a, argv[++i]);
            }
        }
        return args;
    }

    private static String require(Map<String, String> args, String key) {
        String v = args.get(key);
        if(v == null)
            throw new IllegalArgumentException("Missing required option " + key);
        return v;
    }

    private static String baseName(String path) {
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }
}
