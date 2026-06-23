/******************************************************************************
 * EquivalenceTest.java: Java/C++ equivalence.
 *
 * Runs the SAME deterministic scenario (fixed seed, 1 thread, fixed iterations)
 * through the Java wrapper and through the native C++ reference
 * (native/tests_reference), and asserts identical results. Since the whole
 * BRKGA core is the same native code in both, this checks that the Java decoder
 * + FFM boundary reproduce the native optimization exactly.
 *****************************************************************************/

package brkga;

import brkga.AlgorithmStatus;
import brkga.BrkgaMpIpr;
import brkga.tsp.TspInstance;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EquivalenceTest {

    private static Map<String, String> runReference(String instance, int iters)
            throws Exception {
        Assumptions.assumeTrue(Files.exists(TestSupport.referenceBinary()),
            "native reference not built: " + TestSupport.referenceBinary());

        ProcessBuilder pb = new ProcessBuilder(
            TestSupport.referenceBinary().toString(),
            String.valueOf(TestSupport.SEED), TestSupport.SINGLE_CONFIG,
            String.valueOf(iters), instance, "1");
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        Map<String, String> out = new HashMap<>();
        try(BufferedReader r = new BufferedReader(
                new InputStreamReader(proc.getInputStream()))) {
            String line;
            while((line = r.readLine()) != null) {
                int eq = line.indexOf('=');
                if(eq > 0) out.put(line.substring(0, eq), line.substring(eq + 1));
            }
        }
        assertEquals(0, proc.waitFor(), "C++ reference exited with error");
        return out;
    }

    private void assertEquivalent(String instance, int iters) throws Exception {
        Map<String, String> cpp = runReference(instance, iters);

        Brkga brkga = Brkga.single();
        TspInstance tsp = new TspInstance(instance);
        try(BrkgaMpIpr algo = TestSupport.buildSingle(brkga, tsp, TestSupport.SEED, 1)) {
            algo.setStoppingCriteria(s -> s.currentIteration == iters);
            AlgorithmStatus st = algo.run(TestSupport.fixedIterationsControl());

            assertEquals(Double.parseDouble(cpp.get("best_fitness")),
                         st.bestFitness[0], 1e-6, "best_fitness");
            assertEquals(Integer.parseInt(cpp.get("current_iteration")),
                         st.currentIteration, "current_iteration");
            assertEquals(Integer.parseInt(cpp.get("last_update_iteration")),
                         st.lastUpdateIteration, "last_update_iteration");
            assertEquals(Integer.parseInt(cpp.get("largest_iteration_offset")),
                         st.largestIterationOffset, "largest_iteration_offset");
            assertEquals(Integer.parseInt(cpp.get("stalled_iterations")),
                         st.stalledIterations, "stalled_iterations");
            assertEquals(Integer.parseInt(cpp.get("elite_size")),
                         algo.getEliteSize(), "elite_size");
            assertEquals(Integer.parseInt(cpp.get("num_mutants")),
                         algo.getNumMutants(), "num_mutants");
            assertEquals(Integer.parseInt(cpp.get("chromosome_size")),
                         algo.getChromosomeSize(), "chromosome_size");
            assertEquals(Double.parseDouble(cpp.get("best_gene0")),
                         algo.getBestChromosome()[0], 1e-9, "best_gene0");
        }
    }

    @Test
    void burma14Equivalent() throws Exception {
        assertEquivalent(TestSupport.BURMA14, 50);
    }

    @Test
    void brazil58Equivalent() throws Exception {
        assertEquivalent(TestSupport.BRAZIL58, 30);
    }
}
