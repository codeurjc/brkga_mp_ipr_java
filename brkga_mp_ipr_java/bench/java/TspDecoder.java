/******************************************************************************
 * TspDecoder.java: minimal single-objective TSP decoder for the benchmark.
 *
 * The benchmark's OWN copy (package `bench`), kept algorithmically equivalent
 * to the C++ bench TSPDecoder (same O(n log n) sort + O(n) tour cost) so the
 * comparison isolates the Java/FFM decode-boundary overhead rather than
 * decoder-implementation differences. Independent of the Java examples.
 *****************************************************************************/

package bench;

import brkga.Chromosome;
import brkga.Decoder;

import java.util.Arrays;

public final class TspDecoder implements Decoder {

    public final TspInstance instance;

    public TspDecoder(TspInstance instance) {
        this.instance = instance;
    }

    @Override
    public double[] decode(Chromosome chromosome, boolean rewrite) {
        int size = chromosome.size();

        // Read each gene exactly once into a local cache, then sort the node
        // indices by the cached key. This mirrors the C++ baseline, which builds
        // a vector<pair<key,index>> and sorts it: both read O(n) genes and run
        // one O(n log n) sort, so the comparison isolates the FFM boundary cost
        // rather than a difference in how often the genes are accessed.
        double[] key = new double[size];
        Integer[] permutation = new Integer[size];
        for(int i = 0; i < size; ++i) {
            key[i] = chromosome.get(i);
            permutation[i] = i;
        }
        Arrays.sort(permutation, (a, b) -> Double.compare(key[a], key[b]));

        double cost = instance.distance(permutation[0], permutation[size - 1]);
        for(int i = 0; i < size - 1; ++i)
            cost += instance.distance(permutation[i], permutation[i + 1]);

        return new double[] { cost };
    }
}
