/******************************************************************************
 * MainMinimal.java: Java port of single_obj/main_minimal.cpp (POJO API).
 *
 * Usage: <seed> <config-file> <maximum-running-time> <tsp-instance-file>
 *****************************************************************************/

package brkga.single;

import brkga.AlgorithmStatus;
import brkga.Brkga;
import brkga.BrkgaMpIpr;
import brkga.Sense;
import brkga.tsp.TspInstance;
import brkga.tsp.single.TspDecoder;

public class MainMinimal {

    public static void main(String[] argv) {
        if(argv.length < 4) {
            System.err.println(
                "Usage: MainMinimal <seed> <config-file> <maximum-running-time>"
                + " <tsp-instance-file>");
            System.exit(1);
        }

        try {
            final int seed = Integer.parseInt(argv[0]);
            final String configFile = argv[1];
            final String instanceFile = argv[3];
            final int numThreads = Integer.getInteger("brkga.num_threads", 4);

            System.out.println("Reading data...");
            TspInstance instance = new TspInstance(instanceFile);

            System.out.println("Reading parameters...");
            Brkga brkga = Brkga.single();
            Brkga.Config cfg = brkga.readConfiguration(configFile);

            // Overwrite the maximum time from the config file.
            cfg.controlParams().maximumRunningTime = Long.parseLong(argv[2]);

            System.out.println("Building BRKGA data and initializing...");
            TspDecoder decoder = new TspDecoder(instance);

            try(BrkgaMpIpr algorithm = new BrkgaMpIpr(
                    brkga, decoder, Sense.MINIMIZE, seed,
                    instance.numNodes, cfg.brkgaParams(), numThreads)) {

                System.out.println("Running for " + argv[2] + " seconds...");
                AlgorithmStatus finalStatus = algorithm.run(cfg.controlParams());

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
