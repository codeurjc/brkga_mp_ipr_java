/******************************************************************************
 * LastRunBestChromosomeTest.java: getLastRunBestChromosome() returns the
 * historical best of a run, surviving shake/reset, unlike getBestChromosome().
 *****************************************************************************/

package brkga;

import brkga.tsp.TspInstance;
import brkga.tsp.single.TspDecoder;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LastRunBestChromosomeTest {

    /** Before any run has recorded a best, the getter must fail, not return garbage. */
    @Test
    void throwsBeforeAnyRun() throws Exception {
        Brkga brkga = Brkga.single();
        TspInstance instance = new TspInstance(TestSupport.BURMA14);
        try(BrkgaMpIpr algo = TestSupport.buildSingle(brkga, instance, TestSupport.SEED, 1)) {
            assertThrows(RuntimeException.class, algo::getLastRunBestChromosome);
        }
    }

    @Test
    void lastRunBestSurvivesShakeAndReset() throws Exception {
        Brkga brkga = Brkga.single();
        TspInstance instance = new TspInstance(TestSupport.BURMA14);
        try(BrkgaMpIpr algo = TestSupport.buildSingle(brkga, instance, TestSupport.SEED, 1)) {
            // Force shakes and resets during the run; stop by iteration count so the
            // run reliably ends after several perturbations have fired.
            ControlParams control = new ControlParams();
            control.maximumRunningTime = Long.MAX_VALUE;
            control.stallOffset = (int) 0xFFFFFFFFL;  // never stop by stall
            control.shakeInterval = 3;
            control.resetInterval = 8;
            algo.setStoppingCriteria(s -> s.currentIteration >= 200);

            AlgorithmStatus status = algo.run(control);

            // The run must actually have shaken and reset, or the test would not be
            // exercising the scenario this method exists for.
            assertTrue(status.numShakes > 0, "expected shaking to fire during the run");
            assertTrue(status.numResets > 0, "expected a reset to fire during the run");

            TspDecoder decoder = new TspDecoder(instance);
            double[] runBest = algo.getLastRunBestChromosome();

            // The last-run best chromosome must decode to the fitness run() reported...
            assertEquals(status.bestFitness[0], decode(decoder, runBest), 1e-9,
                "getLastRunBestChromosome must decode to the run's best fitness");
            // ...and can never be worse than the current-population best.
            assertTrue(decode(decoder, runBest) <= decode(decoder, algo.getBestChromosome()) + 1e-9,
                "historical best must be <= population best");

            // A reset() wipes the populations (getBestChromosome would change), but the
            // historical best is snapshotted in the run status, so it must survive
            // unchanged. This is the guarantee getLastRunBestChromosome exists to give.
            algo.reset();
            assertArrayEquals(runBest, algo.getLastRunBestChromosome(),
                "last-run best must survive an explicit reset()");
        }
    }

    /** Decodes a chromosome's genes with the TSP decoder, returning its single fitness. */
    private static double decode(TspDecoder decoder, double[] genes) {
        try(Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate((long) genes.length * Double.BYTES);
            for(int i = 0; i < genes.length; ++i) {
                seg.setAtIndex(ValueLayout.JAVA_DOUBLE, i, genes[i]);
            }
            return decoder.decode(new Chromosome(seg, genes.length), false)[0];
        }
    }
}
