/******************************************************************************
 * DeterminismTest.java: white-box check that, with a fixed seed and a single
 * thread (MATING_SEQUENTIAL), two independent runs are identical.
 *****************************************************************************/

package brkga;

import brkga.AlgorithmStatus;
import brkga.BrkgaMpIpr;
import brkga.tsp.TspInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DeterminismTest {

    private AlgorithmStatus runFixed(Brkga brkga, TspInstance instance, int iters) {
        try(BrkgaMpIpr algo = TestSupport.buildSingle(brkga, instance, TestSupport.SEED, 1)) {
            algo.setStoppingCriteria(s -> s.currentIteration == iters);
            return algo.run(TestSupport.fixedIterationsControl());
        }
    }

    @Test
    void twoRunsAreIdentical() throws Exception {
        Brkga brkga = Brkga.single();
        TspInstance instance = new TspInstance(TestSupport.BURMA14);

        AlgorithmStatus a = runFixed(brkga, instance, 60);
        AlgorithmStatus b = runFixed(brkga, instance, 60);

        assertArrayEquals(a.bestFitness, b.bestFitness);
        assertEquals(a.currentIteration, b.currentIteration);
        assertEquals(a.lastUpdateIteration, b.lastUpdateIteration);
        assertEquals(a.largestIterationOffset, b.largestIterationOffset);
        assertEquals(a.stalledIterations, b.stalledIterations);
        assertEquals(60, a.currentIteration);
    }
}
