/******************************************************************************
 * TspDecoderOptimized.java: optimized single-objective TSP decoder.
 *
 * Same RESULT as bench.TspDecoder, but tuned for the hot path with the very same
 * technique as the C++ bench TSPDecoderOptimized, so the two stay equivalent:
 *
 *   1. A reusable per-thread buffer (ThreadLocal long[]), so no allocation per
 *      decode.
 *   2. A primitive packed sort: each gene's IEEE-754 bit pattern (monotonic for
 *      the non-negative keys in [0,1)) is packed in the high bits and the node
 *      index in the low `indexBits` bits, so a single ascending Arrays.sort(long[])
 *      orders by gene and breaks ties by index — no boxing, no comparator.
 *****************************************************************************/

package bench;

import brkga.Chromosome;
import brkga.Decoder;

import java.util.Arrays;

public final class TspDecoderOptimized implements Decoder {

    public final TspInstance instance;

    private final long indexMask;
    private final long keyMask;
    private final ThreadLocal<long[]> packedPerThread;

    public TspDecoderOptimized(TspInstance instance) {
        this.instance = instance;
        final int n = instance.numNodes;
        int indexBits = Math.max(1, 32 - Integer.numberOfLeadingZeros(Math.max(1, n - 1)));
        this.indexMask = (1L << indexBits) - 1L;
        this.keyMask = ~indexMask;
        this.packedPerThread = ThreadLocal.withInitial(() -> new long[n]);
    }

    @Override
    public double[] decode(Chromosome chromosome, boolean rewrite) {
        final int size = chromosome.size();
        final long[] packed = packedPerThread.get();

        for(int i = 0; i < size; ++i) {
            long bits = Double.doubleToRawLongBits(chromosome.get(i));
            packed[i] = (bits & keyMask) | i;
        }

        Arrays.sort(packed, 0, size);

        int first = (int) (packed[0] & indexMask);
        int last = (int) (packed[size - 1] & indexMask);
        double cost = instance.distance(first, last);

        for(int i = 0; i < size - 1; ++i)
            cost += instance.distance((int) (packed[i] & indexMask),
                                      (int) (packed[i + 1] & indexMask));

        return new double[] { cost };
    }
}
