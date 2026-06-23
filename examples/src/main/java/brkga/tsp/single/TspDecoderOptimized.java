/******************************************************************************
 * TspDecoderOptimized.java: single-objective TSP decoder, optimized.
 *
 * Functionally identical to brkga.tsp.single.TspDecoder (same tour, same cost),
 * but tuned for the decode hot path. It is the additional, optimized variant —
 * the basic TspDecoder is the one used in the guide and examples. The two
 * optimizations, and why they help, are described in docs/JAVA_GUIDE.md
 * ("Writing a fast decoder"):
 *
 *   1. Per-thread reusable buffer (no per-decode allocation): a ThreadLocal
 *      long[] is reused across calls, so the costly read-write scratch is
 *      allocated once per thread instead of on every decode. This is the Java
 *      analogue of the C++ example's tsp_decoder_pre_allocating.
 *
 *   2. Primitive packed sort instead of a Comparator over boxed objects: each
 *      gene's IEEE-754 bit pattern (monotonic for the non-negative keys in
 *      [0,1)) is packed in the high bits and the node index in the low
 *      `indexBits` bits, so a single ascending Arrays.sort(long[]) orders by gene
 *      and breaks ties by index (exactly as std::sort over pairs would), with no
 *      boxing and no comparator callbacks.
 *
 * Thread-safe: reads the immutable instance and a per-thread buffer, so the
 * library may call it concurrently from several OpenMP threads.
 *****************************************************************************/

package brkga.tsp.single;

import brkga.Chromosome;
import brkga.Decoder;
import brkga.tsp.TspInstance;

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
