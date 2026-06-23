/******************************************************************************
 * TestSupport.java: shared paths and helpers for the equivalence/wrapper tests.
 *
 * Tests run from the module directory (java/); the C++ examples (config files,
 * instances) and the native binaries live at the paths below. The surefire
 * configuration sets --enable-native-access, -Dbrkga.bridge.dir and
 * -Dbrkga.num_threads=1.
 *****************************************************************************/

package brkga;

import brkga.BrkgaMpIpr;
import brkga.Brkga;
import brkga.ControlParams;
import brkga.Sense;
import brkga.tsp.TspInstance;
import brkga.tsp.single.TspDecoder;

import java.nio.file.Path;

final class TestSupport {

    private TestSupport() {}

    static final String EX = "../third_party/brkga_mp_ipr_cpp/examples/tsp";
    static final String SINGLE_CONFIG = EX + "/src/single_obj/config.conf";
    static final String MULTI_CONFIG = EX + "/src/multi_obj/config.conf";
    static final String BURMA14 = EX + "/instances/burma14.dat";
    static final String BRAZIL58 = EX + "/instances/brazil58.dat";
    static final int SEED = 27000001;

    static String nativeDir() {
        return System.getProperty("brkga.bridge.dir", "native");
    }

    static Path referenceBinary() {
        return Path.of(nativeDir(), "tests_reference");
    }

    /// Default ControlParams for a fixed-iterations deterministic run
    /// (mirrors MainMaximumIterations / tests_reference: no IPR/shake/exchange/
    /// reset, unbounded time and stall).
    static ControlParams fixedIterationsControl() {
        ControlParams c = new ControlParams();
        c.maximumRunningTime = Long.MAX_VALUE;
        c.stallOffset = (int) 0xFFFFFFFFL;
        return c;
    }

    /// Builds a single-objective algorithm for the given instance/seed/threads.
    static BrkgaMpIpr buildSingle(Brkga brkga, TspInstance instance,
                                  int seed, int numThreads) {
        Brkga.Config cfg = brkga.readConfiguration(SINGLE_CONFIG);
        TspDecoder decoder = new TspDecoder(instance);
        return new BrkgaMpIpr(brkga, decoder, Sense.MINIMIZE, seed,
            instance.numNodes, cfg.brkgaParams(), numThreads);
    }
}
