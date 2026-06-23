/******************************************************************************
 * MainMaximumIterations.java: Java port of
 * single_obj/main_maximum_iterations.cpp (POJO API).
 *
 * Runs for a fixed number of iterations via a custom stopping criteria; uses
 * default control params (IPR/shake/exchange/reset disabled).
 *
 * Usage: <seed> <config-file> <maximum-number-of-iterations> <tsp-instance-file>
 *****************************************************************************/

package brkga.single;

import brkga.AlgorithmStatus;
import brkga.Brkga;
import brkga.BrkgaMpIpr;
import brkga.ControlParams;
import brkga.Sense;
import brkga.tsp.TspInstance;
import brkga.tsp.single.TspDecoder;

public class MainMaximumIterations {

    public static void main(String[] argv) {
        if(argv.length < 4) {
            System.err.println(
                "Usage: MainMaximumIterations <seed> <config-file>"
                + " <maximum-number-of-iterations> <tsp-instance-file>");
            System.exit(1);
        }

        try {
            final int seed = Integer.parseInt(argv[0]);
            final String configFile = argv[1];
            final long maximumNumberOfIterations = Long.parseLong(argv[2]);
            final String instanceFile = argv[3];
            final int numThreads = Integer.getInteger("brkga.num_threads", 4);

            System.out.println("Reading data...");
            TspInstance instance = new TspInstance(instanceFile);

            System.out.println("Reading parameters...");
            Brkga brkga = Brkga.single();
            Brkga.Config cfg = brkga.readConfiguration(configFile);

            // Default control parameters (disable IPR/shake/exchange/reset),
            // overriding max time and stall to avoid early termination.
            ControlParams control = new ControlParams();
            control.maximumRunningTime = Long.MAX_VALUE;       // chrono::seconds::max()
            control.stallOffset = (int) 0xFFFFFFFFL;           // numeric_limits<unsigned>::max()

            System.out.println("Building BRKGA data and initializing...");
            TspDecoder decoder = new TspDecoder(instance);

            try(BrkgaMpIpr algorithm = new BrkgaMpIpr(
                    brkga, decoder, Sense.MINIMIZE, seed,
                    instance.numNodes, cfg.brkgaParams(), numThreads)) {

                algorithm.setStoppingCriteria(
                    status -> status.currentIteration == maximumNumberOfIterations);

                System.out.println("Running for " + Long.MAX_VALUE + "s...");
                AlgorithmStatus finalStatus = algorithm.run(control);

                System.out.println(
                    "\nAlgorithm status: " + finalStatus
                    + "\n\nBest cost: " + finalStatus.bestFitness[0]);
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
}
