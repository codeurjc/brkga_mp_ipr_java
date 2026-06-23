/******************************************************************************
 * TspDecoder.java: multi-objective TSP decoder.
 *
 * Returns two objectives, the tour cost and the largest edge (both minimized).
 * Faithful Java counterpart of the C++ example decoder
 * (examples/tsp/src/multi_obj/decoders/tsp_decoder.cpp): a straightforward,
 * readable implementation. For the optimization techniques, see
 * brkga.tsp.single.TspDecoderOptimized and docs/JAVA_GUIDE.md.
 *****************************************************************************/

package brkga.tsp.multi;

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

        // The closing edge (first-last) is added to the cost but, as in the
        // original C++ decoder, it is NOT considered for the largest edge.
        double cost = instance.distance(permutation[0], permutation[size - 1]);
        double largestEdge = Double.MIN_VALUE;

        for(int i = 0; i < size - 1; ++i) {
            double dist = instance.distance(permutation[i], permutation[i + 1]);
            largestEdge = Math.max(largestEdge, dist);
            cost += dist;
        }

        return new double[] { cost, largestEdge };
    }
}
