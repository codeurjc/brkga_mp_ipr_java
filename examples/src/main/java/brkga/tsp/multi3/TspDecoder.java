/******************************************************************************
 * TspDecoder.java: SYNTHETIC three-objective TSP decoder.
 *
 * There is no three-objective example in the original C++ suite; this decoder
 * exists only to exercise the N-objective machinery (the dedicated N=3 tuple
 * bridge and the generic CAP bridge) end-to-end.
 *
 * Objectives (all minimized, lexicographically): {cost, largestEdge,
 * smallestEdge}. Straightforward, readable implementation; for the optimization
 * techniques see brkga.tsp.single.TspDecoderOptimized and docs/JAVA_GUIDE.md.
 *****************************************************************************/

package brkga.tsp.multi3;

import brkga.Chromosome;
import brkga.Decoder;
import brkga.tsp.TspInstance;

import java.util.Arrays;

public final class TspDecoder implements Decoder {

    public final TspInstance instance;

    public TspDecoder(TspInstance instance) {
        this.instance = instance;
    }

    @Override
    public double[] decode(Chromosome chromosome, boolean rewrite) {
        int size = chromosome.size();

        Integer[] permutation = new Integer[size];
        for(int i = 0; i < size; ++i) permutation[i] = i;
        Arrays.sort(permutation,
            (a, b) -> Double.compare(chromosome.get(a), chromosome.get(b)));

        double closing = instance.distance(permutation[0], permutation[size - 1]);
        double cost = closing;
        double largestEdge = closing;
        double smallestEdge = closing;

        for(int i = 0; i < size - 1; ++i) {
            double dist = instance.distance(permutation[i], permutation[i + 1]);
            largestEdge = Math.max(largestEdge, dist);
            smallestEdge = Math.min(smallestEdge, dist);
            cost += dist;
        }

        return new double[] { cost, largestEdge, smallestEdge };
    }
}
