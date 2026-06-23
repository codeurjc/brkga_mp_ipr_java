/******************************************************************************
 * GettersTest.java: white-box checks of the configuration getters derived from
 * the constructor (elite/mutant sizing, sense, threads, chromosome size).
 *****************************************************************************/

package brkga;

import brkga.BrkgaMpIpr;
import brkga.Sense;
import brkga.tsp.TspInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GettersTest {

    @Test
    void gettersReflectConstruction() throws Exception {
        Brkga brkga = Brkga.single();
        TspInstance instance = new TspInstance(TestSupport.BURMA14);
        try(BrkgaMpIpr algo = TestSupport.buildSingle(brkga, instance, TestSupport.SEED, 1)) {
            assertEquals(instance.numNodes, algo.getChromosomeSize());   // 14
            assertEquals(Sense.MINIMIZE, algo.getOptimizationSense());
            assertEquals(1, algo.getMaxThreads());
            // population_size 2000, elite 0.30 -> 600, mutants 0.15 -> 300.
            assertEquals(600, algo.getEliteSize());
            assertEquals(300, algo.getNumMutants());
            assertEquals(2000, algo.getBrkgaParams().populationSize);
        }
    }
}
