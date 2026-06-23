/******************************************************************************
 * ExtraBlobTest.java: verifies the inline per-chromosome "extra" blob.
 *
 *  - The decoder can store auxiliary metrics in the chromosome (Chromosome.set-
 *    Extra) derived from the genes, alongside the fitness.
 *  - Those metrics travel with the chromosome and can be read back for the best
 *    solution (BrkgaMpIpr.getBestChromosomeExtra) WITHOUT decoding again, and
 *    are consistent with the genes in a single-population run (no exchange / no
 *    path relinking, the always-consistent case).
 *****************************************************************************/

package brkga;

import brkga.tsp.TspInstance;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtraBlobTest {

    /// A TSP decoder that, besides the tour cost (the fitness), stores two
    /// auxiliary metrics in the chromosome's extra blob:
    ///   extra[0] = tour cost (same value as the fitness),
    ///   extra[1] = largest edge in the tour.
    /// Both are deterministic functions of the genes and are written on EVERY
    /// call (the required contract), so the blob never goes stale.
    static final class MetricsDecoder implements Decoder {
        final TspInstance instance;

        MetricsDecoder(TspInstance instance) { this.instance = instance; }

        @Override
        public double[] decode(Chromosome c, boolean rewrite) {
            int n = c.size();
            Integer[] perm = new Integer[n];
            for(int i = 0; i < n; ++i) perm[i] = i;
            Arrays.sort(perm, (a, b) -> Double.compare(c.get(a), c.get(b)));

            double cost = instance.distance(perm[0], perm[n - 1]);
            double largestEdge = instance.distance(perm[0], perm[n - 1]);
            for(int i = 0; i < n - 1; ++i) {
                double d = instance.distance(perm[i], perm[i + 1]);
                cost += d;
                if(d > largestEdge) largestEdge = d;
            }

            if(c.extraSize() >= 2) {
                c.setExtra(0, cost);
                c.setExtra(1, largestEdge);
            }
            return new double[] { cost };
        }
    }

    @Test
    void extraCapacityIsPositive() throws Exception {
        Brkga brkga = Brkga.single();
        TspInstance instance = new TspInstance(TestSupport.BURMA14);
        BrkgaParams params = brkga.readConfiguration(TestSupport.SINGLE_CONFIG).brkgaParams();
        try(BrkgaMpIpr algo = new BrkgaMpIpr(brkga, new MetricsDecoder(instance),
                Sense.MINIMIZE, TestSupport.SEED, instance.numNodes, params, 1)) {
            assertTrue(algo.extraCapacity() >= 2,
                "the bridge must reserve at least a couple of extra doubles");
        }
    }

    @Test
    void bestChromosomeExtraIsConsistentAndRecoveredWithoutDecoding() throws Exception {
        Brkga brkga = Brkga.single();
        TspInstance instance = new TspInstance(TestSupport.BURMA14);
        MetricsDecoder decoder = new MetricsDecoder(instance);

        BrkgaParams params = brkga.readConfiguration(TestSupport.SINGLE_CONFIG).brkgaParams();
        // Single population, no path relinking -> the always-consistent case.
        params.numIndependentPopulations = 1;

        try(BrkgaMpIpr algo = new BrkgaMpIpr(brkga, decoder, Sense.MINIMIZE,
                TestSupport.SEED, instance.numNodes, params, 1)) {

            algo.evolve(20);

            double[] extra = algo.getBestChromosomeExtra();
            assertEquals(algo.extraCapacity(), extra.length);

            // extra[0] was stored as the tour cost -> must equal the fitness.
            assertEquals(algo.getBestFitness()[0], extra[0], 1e-9,
                "stored extra[0] must match the best fitness");

            // Recompute the metrics from the best chromosome's genes and compare:
            // the stored blob must reflect exactly those genes.
            double[] genes = algo.getBestChromosome();
            double[] recomputed = recompute(instance, genes);
            assertEquals(recomputed[0], extra[0], 1e-9, "cost mismatch");
            assertEquals(recomputed[1], extra[1], 1e-9, "largest-edge mismatch");

            // Position 0 of population 0 is the best chromosome: same blob.
            double[] extraAt0 = algo.getChromosomeExtra(0, 0);
            assertEquals(extra[0], extraAt0[0], 1e-12);
            assertEquals(extra[1], extraAt0[1], 1e-12);
        }
    }

    /// Recomputes {cost, largestEdge} from a gene array, independently of the
    /// decoder, to validate the stored blob.
    private static double[] recompute(TspInstance instance, double[] genes) {
        int n = genes.length;
        Integer[] perm = new Integer[n];
        for(int i = 0; i < n; ++i) perm[i] = i;
        Arrays.sort(perm, (a, b) -> Double.compare(genes[a], genes[b]));
        double cost = instance.distance(perm[0], perm[n - 1]);
        double largestEdge = cost;
        for(int i = 0; i < n - 1; ++i) {
            double d = instance.distance(perm[i], perm[i + 1]);
            cost += d;
            if(d > largestEdge) largestEdge = d;
        }
        return new double[] { cost, largestEdge };
    }
}
