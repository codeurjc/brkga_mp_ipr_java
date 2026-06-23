/******************************************************************************
 * TspDecoder.java: single-objective TSP decoder.
 *
 * Simple Traveling Salesman Problem decoder. It builds the permutation of nodes
 * induced by the chromosome (the nodes sorted by their gene/key) and returns the
 * cost of the resulting tour. This is the faithful Java counterpart of the C++
 * example decoder (examples/tsp/src/single_obj/decoders/tsp_decoder.cpp): a
 * straightforward, readable implementation.
 *
 * For a faster variant (and how the optimization is done) see
 * brkga.tsp.single.TspDecoderOptimized and docs/JAVA_GUIDE.md.
 *****************************************************************************/

package brkga.tsp.single;

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

        // The permutation of nodes induced by the chromosome.
        Integer[] permutation = new Integer[size];
        for(int i = 0; i < size; ++i) permutation[i] = i;
        Arrays.sort(permutation,
            (a, b) -> Double.compare(chromosome.get(a), chromosome.get(b)));

        // Compute the cost of the cycle from that permutation.
        double cost = instance.distance(permutation[0], permutation[size - 1]);
        for(int i = 0; i < size - 1; ++i)
            cost += instance.distance(permutation[i], permutation[i + 1]);

        return new double[] { cost };
    }
}
