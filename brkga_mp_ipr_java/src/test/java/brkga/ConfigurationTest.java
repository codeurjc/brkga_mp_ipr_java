/******************************************************************************
 * ConfigurationTest.java: black-box tests of readConfiguration/writeConfiguration
 * against the documented config-file format (single_obj/config.conf).
 *****************************************************************************/

package brkga;

import brkga.BiasFunctionType;
import brkga.Brkga;
import brkga.BrkgaParams;
import brkga.ControlParams;
import brkga.PathRelinking;
import brkga.ShakingType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigurationTest {

    private static final double EPS = 1e-9;

    @Test
    void readsDocumentedValues() {
        Brkga brkga = Brkga.single();
        Brkga.Config cfg = brkga.readConfiguration(TestSupport.SINGLE_CONFIG);
        BrkgaParams p = cfg.brkgaParams();

        assertEquals(2000, p.populationSize);
        assertEquals(0.30, p.elitePercentage, EPS);
        assertEquals(0.15, p.mutantsPercentage, EPS);
        assertEquals(2, p.numEliteParents);
        assertEquals(3, p.totalParents);
        assertEquals(BiasFunctionType.LOGINVERSE, p.biasType);
        assertEquals(3, p.numIndependentPopulations);
        assertEquals(0, p.prNumberPairs);
        assertEquals(0.15, p.prMinimumDistance, EPS);
        assertEquals(PathRelinking.Type.DIRECT, p.prType);
        assertEquals(PathRelinking.Selection.BESTSOLUTION, p.prSelection);
        assertEquals(PathRelinking.DistanceFunctionType.KENDALLTAU,
                     p.prDistanceFunctionType);
        assertEquals(1.0, p.alphaBlockSize, EPS);
        assertEquals(1.0, p.prPercentage, EPS);
        assertEquals(1, p.numExchangeIndividuals);
        assertEquals(ShakingType.SWAP, p.shakingType);
        assertEquals(0.25, p.shakingIntensityLowerBound, EPS);
        assertEquals(0.75, p.shakingIntensityUpperBound, EPS);

        ControlParams c = cfg.controlParams();
        assertEquals(60, c.maximumRunningTime);
        assertEquals(100, c.exchangeInterval);
        assertEquals(200, c.iprInterval);
        assertEquals(300, c.shakeInterval);
        assertEquals(500, c.resetInterval);
        assertEquals(1000, c.stallOffset);
    }

    @Test
    void writeConfigurationRoundTrips() throws Exception {
        Brkga brkga = Brkga.single();
        Brkga.Config original = brkga.readConfiguration(TestSupport.SINGLE_CONFIG);

        Path tmp = Files.createTempFile("brkga_cfg", ".conf");
        try {
            brkga.writeConfiguration(tmp.toString(),
                original.brkgaParams(), original.controlParams());
            Brkga.Config rt = brkga.readConfiguration(tmp.toString());

            BrkgaParams a = original.brkgaParams(), b = rt.brkgaParams();
            assertEquals(a.populationSize, b.populationSize);
            assertEquals(a.elitePercentage, b.elitePercentage, EPS);
            assertEquals(a.biasType, b.biasType);
            assertEquals(a.numIndependentPopulations, b.numIndependentPopulations);
            assertEquals(a.prDistanceFunctionType, b.prDistanceFunctionType);
            assertEquals(a.shakingType, b.shakingType);

            ControlParams ca = original.controlParams(), cb = rt.controlParams();
            assertEquals(ca.maximumRunningTime, cb.maximumRunningTime);
            assertEquals(ca.iprInterval, cb.iprInterval);
            assertEquals(ca.stallOffset, cb.stallOffset);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
