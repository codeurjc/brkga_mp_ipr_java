/******************************************************************************
 * EnumTest.java: black-box checks of the enum integer mappings that the bridge
 * relies on (documented values / declaration order).
 *****************************************************************************/

package brkga;

import brkga.PathRelinking;
import brkga.Sense;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnumTest {

    @Test
    void senseValues() {
        assertEquals(0, Sense.MINIMIZE.value);
        assertEquals(1, Sense.MAXIMIZE.value);
        assertEquals(Sense.MINIMIZE, Sense.fromValue(0));
        assertEquals(Sense.MAXIMIZE, Sense.fromValue(1));
    }

    @Test
    void pathRelinkingResultExplicitValues() {
        // Documented OR-combinable values.
        assertEquals(0, PathRelinking.PathRelinkingResult.TOO_HOMOGENEOUS.value);
        assertEquals(1, PathRelinking.PathRelinkingResult.NO_IMPROVEMENT.value);
        assertEquals(3, PathRelinking.PathRelinkingResult.ELITE_IMPROVEMENT.value);
        assertEquals(7, PathRelinking.PathRelinkingResult.BEST_IMPROVEMENT.value);
        assertEquals(PathRelinking.PathRelinkingResult.BEST_IMPROVEMENT,
                     PathRelinking.PathRelinkingResult.fromValue(7));
    }

    @Test
    void prTypeAndSelectionOrdinals() {
        assertEquals(0, PathRelinking.Type.DIRECT.value());
        assertEquals(1, PathRelinking.Type.PERMUTATION.value());
        assertEquals(0, PathRelinking.Selection.BESTSOLUTION.value());
        assertEquals(1, PathRelinking.Selection.RANDOMELITE.value());
        assertEquals(0, PathRelinking.DistanceFunctionType.HAMMING.value());
        assertEquals(1, PathRelinking.DistanceFunctionType.KENDALLTAU.value());
        assertEquals(2, PathRelinking.DistanceFunctionType.CUSTOM.value());
    }
}
