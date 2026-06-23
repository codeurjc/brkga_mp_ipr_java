/******************************************************************************
 * OperationsTest.java: white-box checks of evolve / pathRelink / population
 * access / inject / shake / reset on the wrapper.
 *****************************************************************************/

package brkga;

import brkga.BrkgaMpIpr;
import brkga.PathRelinking;
import brkga.Population;
import brkga.ShakingType;
import brkga.tsp.TspInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationsTest {

    @Test
    void evolveImprovesOrKeepsBest() throws Exception {
        Brkga brkga = Brkga.single();
        TspInstance instance = new TspInstance(TestSupport.BURMA14);
        try(BrkgaMpIpr algo = TestSupport.buildSingle(brkga, instance, TestSupport.SEED, 1)) {
            algo.evolve(5);
            double after5 = algo.getBestFitness()[0];
            algo.evolve(45);
            double after50 = algo.getBestFitness()[0];
            // Elite is preserved across generations, so (minimization) the best
            // never gets worse.
            assertTrue(after50 <= after5,
                "best should be non-increasing: " + after5 + " -> " + after50);
        }
    }

    @Test
    void populationViewIsConsistent() throws Exception {
        Brkga brkga = Brkga.single();
        TspInstance instance = new TspInstance(TestSupport.BURMA14);
        try(BrkgaMpIpr algo = TestSupport.buildSingle(brkga, instance, TestSupport.SEED, 1)) {
            algo.evolve(3);
            Population pop = algo.getCurrentPopulation(0);
            assertEquals(2000, pop.size());
            assertEquals(instance.numNodes, pop.getChromosome(0).length);
            // Rank 0 is the best of the population; its fitness equals getFitness(0,0).
            assertEquals(algo.getFitness(0, 0)[0], pop.getBestFitness()[0]);
        }
    }

    @Test
    void pathRelinkReturnsValidResult() throws Exception {
        Brkga brkga = Brkga.single();
        TspInstance instance = new TspInstance(TestSupport.BRAZIL58);
        try(BrkgaMpIpr algo = TestSupport.buildSingle(brkga, instance, TestSupport.SEED, 1)) {
            algo.evolve(20);
            PathRelinking.PathRelinkingResult r = algo.pathRelink(
                PathRelinking.Type.DIRECT, PathRelinking.Selection.BESTSOLUTION,
                PathRelinking.DistanceFunctionType.KENDALLTAU,
                0, 0.15, 1, 0, 1.0);
            assertNotNull(r);
        }
    }

    @Test
    void injectAndAccessorsDoNotCorruptPopulation() throws Exception {
        Brkga brkga = Brkga.single();
        TspInstance instance = new TspInstance(TestSupport.BURMA14);
        try(BrkgaMpIpr algo = TestSupport.buildSingle(brkga, instance, TestSupport.SEED, 1)) {
            algo.evolve(3);
            double[] chr = new double[instance.numNodes];
            for(int i = 0; i < chr.length; ++i) chr[i] = (i + 0.5) / chr.length;
            algo.injectChromosome(chr, 0, algo.getCurrentPopulation(0).size() - 1);
            assertEquals(2000, algo.getCurrentPopulation(0).size());

            algo.exchangeElite(1);
            algo.shake(2, ShakingType.SWAP, 0);
            algo.reset();
            // Still usable after a reset.
            assertEquals(instance.numNodes, algo.getBestChromosome().length);
        }
    }
}
