/******************************************************************************
 * RewriteTest.java: verifies decoder rewrite=true support.
 *
 *  - When the decoder is called with rewrite=true and writes back into the
 *    chromosome (via Chromosome.set), the change must be visible in the stored
 *    population chromosome (the write reaches the live native buffer).
 *  - During implicit path relinking the decoder must be called with
 *    rewrite=false (so the path between solutions is preserved).
 *****************************************************************************/

package brkga;

import brkga.tsp.TspInstance;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewriteTest {

    /// A TSP decoder that, on rewrite=true, writes a sentinel into gene 0
    /// (a write-back probe). It also counts how often each mode is requested.
    static final class RewriteDecoder implements Decoder {
        final TspInstance instance;
        final double sentinel;
        int rewriteTrue = 0;
        int rewriteFalse = 0;

        RewriteDecoder(TspInstance instance, double sentinel) {
            this.instance = instance;
            this.sentinel = sentinel;
        }

        @Override
        public double[] decode(Chromosome c, boolean rewrite) {
            if(rewrite) ++rewriteTrue; else ++rewriteFalse;

            int n = c.size();
            Integer[] perm = new Integer[n];
            for(int i = 0; i < n; ++i) perm[i] = i;
            Arrays.sort(perm, (a, b) -> Double.compare(c.get(a), c.get(b)));

            double cost = instance.distance(perm[0], perm[n - 1]);
            for(int i = 0; i < n - 1; ++i)
                cost += instance.distance(perm[i], perm[i + 1]);

            if(rewrite) c.set(0, sentinel);   // write-back probe
            return new double[] { cost };
        }
    }

    @Test
    void rewriteWritesBackToChromosome() throws Exception {
        Brkga brkga = Brkga.single();
        TspInstance instance = new TspInstance(TestSupport.BURMA14);
        final double sentinel = 0.4242424242;
        RewriteDecoder decoder = new RewriteDecoder(instance, sentinel);

        BrkgaParams params = brkga.readConfiguration(TestSupport.SINGLE_CONFIG).brkgaParams();
        try(BrkgaMpIpr algo = new BrkgaMpIpr(brkga, decoder, Sense.MINIMIZE,
                TestSupport.SEED, instance.numNodes, params, 1)) {

            algo.evolve(1);   // decodes the population with rewrite=true

            assertTrue(decoder.rewriteTrue > 0,
                "decode must have been called with rewrite=true");

            // Every stored chromosome must reflect the written-back sentinel.
            int popSize = algo.getCurrentPopulation(0).size();
            for(int pos : new int[] { 0, 1, popSize / 2, popSize - 1 }) {
                assertEquals(sentinel, algo.getChromosome(0, pos)[0], 1e-12,
                    "gene 0 must reflect the rewrite at position " + pos);
            }
        }
    }

    @Test
    void pathRelinkDecodesWithRewriteFalse() throws Exception {
        Brkga brkga = Brkga.single();
        TspInstance instance = new TspInstance(TestSupport.BRAZIL58);
        RewriteDecoder decoder = new RewriteDecoder(instance, 0.4242);

        BrkgaParams params = brkga.readConfiguration(TestSupport.SINGLE_CONFIG).brkgaParams();
        params.prDistanceFunctionType = PathRelinking.DistanceFunctionType.HAMMING;
        try(BrkgaMpIpr algo = new BrkgaMpIpr(brkga, decoder, Sense.MINIMIZE,
                TestSupport.SEED, instance.numNodes, params, 1)) {

            algo.evolve(5);
            int rewriteFalseBefore = decoder.rewriteFalse;

            algo.pathRelink(PathRelinking.Type.DIRECT,
                PathRelinking.Selection.BESTSOLUTION,
                PathRelinking.DistanceFunctionType.HAMMING, 3, 0.0, 1, 10, 1.0);

            assertTrue(decoder.rewriteFalse > rewriteFalseBefore,
                "path relinking must decode candidates with rewrite=false");
        }
    }
}
