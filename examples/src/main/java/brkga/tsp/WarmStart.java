/******************************************************************************
 * WarmStart.java: builds the initial chromosome that encodes a given tour,
 * mirroring the warm-start injection in main_complete.cpp.
 *
 * The C++ code draws `chromosome_size` random keys with std::mt19937 +
 * generate_canonical, sorts them, and assigns the i-th smallest key to node
 * tour[i]. The decoder sorts genes ascending and reads the node order, so the
 * encoded permutation equals `tour`.
 *
 * Only the *relative order* of the keys matters for the encoded solution, so a
 * Java RNG is used here instead of replicating std::mt19937. The injected tour
 * is therefore identical to the C++ one; only the exact gene values differ
 * (which may slightly affect later crossover).
 *****************************************************************************/

package brkga.tsp;

import java.util.Arrays;
import java.util.Random;

public final class WarmStart {

    private WarmStart() {}

    public static double[] buildInitialChromosome(int chromosomeSize, int[] tour,
                                                   long seed) {
        Random rng = new Random(seed);
        double[] keys = new double[chromosomeSize];
        for(int i = 0; i < chromosomeSize; ++i)
            keys[i] = rng.nextDouble();
        Arrays.sort(keys);

        double[] chromosome = new double[chromosomeSize];
        for(int i = 0; i < keys.length; ++i)
            chromosome[tour[i]] = keys[i];
        return chromosome;
    }
}
