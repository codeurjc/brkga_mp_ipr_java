/******************************************************************************
 * CustomCallbacksTest.java: tests for the CUSTOM callbacks.
 *
 *  - bias: a custom 1/r bias must behave exactly like the built-in LINEAR.
 *  - distance: a custom Hamming distance must behave exactly like the built-in
 *    HAMMING in an explicit pathRelink (and must actually be invoked).
 *  - shaking: a custom shaking method must be invoked and let the run finish.
 *****************************************************************************/

package brkga;

import brkga.AlgorithmStatus;
import brkga.BiasFunction;
import brkga.Brkga;
import brkga.BrkgaMpIpr;
import brkga.BrkgaParams;
import brkga.ControlParams;
import brkga.DistanceFunction;
import brkga.PathRelinking;
import brkga.Sense;
import brkga.tsp.TspInstance;
import brkga.tsp.single.TspDecoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomCallbacksTest {

    /// Custom Hamming distance, replicating BRKGA::HammingDistance (threshold 0.5).
    static final class HammingReplica implements DistanceFunction {
        final double threshold = 0.5;
        int calls = 0;
        @Override public double distance(Chromosome v1, Chromosome v2) {
            ++calls;
            int dist = 0;
            for(int i = 0; i < v1.size(); ++i)
                if((v1.get(i) < threshold) != (v2.get(i) < threshold)) ++dist;
            return dist;
        }
        @Override public boolean affectSolution(double k1, double k2) {
            ++calls;
            return (k1 < threshold) != (k2 < threshold);
        }
        @Override public boolean affectSolutionBlock(Chromosome v1, Chromosome v2, int n) {
            ++calls;
            for(int i = 0; i < n; ++i)
                if((v1.get(i) < threshold) != (v2.get(i) < threshold)) return true;
            return false;
        }
    }

    @Test
    void customBiasMatchesBuiltinLinear() throws Exception {
        Brkga brkga = Brkga.single();
        TspInstance instance = new TspInstance(TestSupport.BURMA14);

        // A: built-in LINEAR.
        BrkgaParams pa = brkga.readConfiguration(TestSupport.SINGLE_CONFIG).brkgaParams();
        pa.biasType = BiasFunctionType.LINEAR;
        AlgorithmStatus a;
        try(BrkgaMpIpr algo = new BrkgaMpIpr(brkga, new TspDecoder(instance),
                Sense.MINIMIZE, TestSupport.SEED, instance.numNodes, pa, 1)) {
            algo.setStoppingCriteria(s -> s.currentIteration == 60);
            a = algo.run(TestSupport.fixedIterationsControl());
        }

        // B: custom bias 1/r (== LINEAR).
        BrkgaParams pb = brkga.readConfiguration(TestSupport.SINGLE_CONFIG).brkgaParams();
        AlgorithmStatus b;
        try(BrkgaMpIpr algo = new BrkgaMpIpr(brkga, new TspDecoder(instance),
                Sense.MINIMIZE, TestSupport.SEED, instance.numNodes, pb, 1)) {
            BiasFunction bias = rank -> 1.0 / rank;
            algo.setBiasCustomFunction(bias);
            algo.setStoppingCriteria(s -> s.currentIteration == 60);
            b = algo.run(TestSupport.fixedIterationsControl());
        }

        assertArrayEquals(a.bestFitness, b.bestFitness, "custom 1/r should match LINEAR");
        assertEquals(a.currentIteration, b.currentIteration);
        assertEquals(a.lastUpdateIteration, b.lastUpdateIteration);
    }

    @Test
    void customDistanceMatchesBuiltinHamming() throws Exception {
        Brkga brkga = Brkga.single();
        TspInstance instance = new TspInstance(TestSupport.BRAZIL58);

        // A: built-in HAMMING.
        BrkgaParams pa = brkga.readConfiguration(TestSupport.SINGLE_CONFIG).brkgaParams();
        pa.prDistanceFunctionType = PathRelinking.DistanceFunctionType.HAMMING;
        double bestA;
        PathRelinking.PathRelinkingResult resA;
        try(BrkgaMpIpr algo = new BrkgaMpIpr(brkga, new TspDecoder(instance),
                Sense.MINIMIZE, TestSupport.SEED, instance.numNodes, pa, 1)) {
            algo.evolve(5);
            resA = algo.pathRelink(PathRelinking.Type.DIRECT,
                PathRelinking.Selection.BESTSOLUTION,
                PathRelinking.DistanceFunctionType.HAMMING, 3, 0.0, 1, 10, 1.0);
            bestA = algo.getBestFitness()[0];
        }

        // B: custom Hamming distance.
        BrkgaParams pb = brkga.readConfiguration(TestSupport.SINGLE_CONFIG).brkgaParams();
        pb.prDistanceFunctionType = PathRelinking.DistanceFunctionType.CUSTOM;
        HammingReplica custom = new HammingReplica();
        double bestB;
        PathRelinking.PathRelinkingResult resB;
        try(BrkgaMpIpr algo = new BrkgaMpIpr(brkga, new TspDecoder(instance),
                Sense.MINIMIZE, TestSupport.SEED, instance.numNodes, pb, 1, true, custom)) {
            algo.evolve(5);
            resB = algo.pathRelink(PathRelinking.Type.DIRECT,
                PathRelinking.Selection.BESTSOLUTION,
                PathRelinking.DistanceFunctionType.CUSTOM, 3, 0.0, 1, 10, 1.0);
            bestB = algo.getBestFitness()[0];
        }

        assertTrue(custom.calls > 0, "custom distance should be invoked during IPR");
        assertEquals(resA, resB, "path relinking result should match the built-in HAMMING");
        assertEquals(bestA, bestB, 1e-6, "best fitness should match the built-in HAMMING");
    }

    @Test
    void customShakingIsInvoked() throws Exception {
        Brkga brkga = Brkga.single();
        TspInstance instance = new TspInstance(TestSupport.BURMA14);

        BrkgaParams params = brkga.readConfiguration(TestSupport.SINGLE_CONFIG).brkgaParams();
        params.shakingType = ShakingType.CUSTOM;

        int[] invocations = {0};

        ControlParams control = new ControlParams();
        control.maximumRunningTime = Long.MAX_VALUE;
        control.stallOffset = (int) 0xFFFFFFFFL;
        control.shakeInterval = 10;   // shake after 10 stalled iterations

        try(BrkgaMpIpr algo = new BrkgaMpIpr(brkga, new TspDecoder(instance),
                Sense.MINIMIZE, TestSupport.SEED, instance.numNodes, params, 1)) {

            algo.setShakingMethod((lower, upper, pops) -> {
                ++invocations[0];
                // Perturb a few non-elite chromosomes and mark them re-decoded.
                for(int p = 0; p < pops.numPopulations(); ++p) {
                    int popSize = pops.populationSize(p);
                    int chrSize = pops.chromosomeSize(p);
                    for(int c = 1; c < Math.min(popSize, 4); ++c) {
                        double v = pops.getAllele(p, c, 0);
                        pops.setAllele(p, c, 0, v < 0.5 ? v + 0.5 : v - 0.5);
                        pops.markShaken(p, c);
                    }
                }
            });

            algo.setStoppingCriteria(s -> s.currentIteration == 300);
            AlgorithmStatus st = algo.run(control);

            assertTrue(invocations[0] > 0, "custom shaking should have been invoked");
            assertTrue(st.numShakes > 0, "the run should report shakes");
            assertEquals(instance.numNodes, algo.getBestChromosome().length);
        }
    }
}
