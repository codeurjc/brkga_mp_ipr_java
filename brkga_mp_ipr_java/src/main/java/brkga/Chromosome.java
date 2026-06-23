/******************************************************************************
 * Chromosome.java: a clean, FFM-free view of a BRKGA chromosome.
 *
 * A chromosome is a vector of real-valued genes (alleles) in the interval
 * [0, 1). This class is the Java counterpart of the C++ `BRKGA::Chromosome`
 * (`std::vector<double>`). It is handed to the user's {@link Decoder} and
 * {@link DistanceFunction}; reads go directly to the underlying native memory
 * (zero-copy), so neither the decoder nor the distance function ever sees a
 * {@code MemorySegment} or any other Foreign Function & Memory type.
 *****************************************************************************/

package brkga;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;

/**
 * A clean, FFM-free view of a BRKGA chromosome: a vector of real-valued genes
 * (alleles) in the interval [0, 1). Java counterpart of the C++
 * {@code BRKGA::Chromosome}. Handed to the user's {@link Decoder} and
 * {@link DistanceFunction}; gene reads go directly to native memory (zero-copy),
 * so no Foreign Function &amp; Memory type is ever exposed.
 */
public final class Chromosome {

    private final MemorySegment genes;   // native backing storage (hidden)
    private final int size;

    private final MemorySegment extra;   // raw extra-blob storage (may be NULL)
    private final int extraSize;
    private MemorySegment extraView;     // lazily reinterpreted view of `extra`

    /// Package-private: chromosomes are created by the wrapper, not by users.
    /// Used where no extra blob is available (e.g. the distance function).
    Chromosome(MemorySegment genes, int size) {
        this(genes, size, MemorySegment.NULL, 0);
    }

    /// Package-private full constructor, including the inline extra blob.
    Chromosome(MemorySegment genes, int size, MemorySegment extra, int extraSize) {
        this.genes = genes;
        this.size = size;
        this.extra = extra;
        this.extraSize = extraSize;
    }

    /**
     * Returns the number of genes (alleles) in this chromosome.
     *
     * @return the chromosome size.
     */
    public int size() {
        return size;
    }

    /**
     * Returns the gene (allele) at the given index, a value in [0, 1).
     *
     * @param index the gene index, in {@code [0, size())}.
     * @return the gene value.
     */
    public double get(int index) {
        return genes.getAtIndex(JAVA_DOUBLE, index);
    }

    /**
     * Sets the gene (allele) at the given index. This is only meaningful for a
     * decoder invoked with {@code rewrite == true}: the change is written back
     * to the chromosome held by the algorithm.
     *
     * @param index the gene index, in {@code [0, size())}.
     * @param value the new gene value (should be in [0, 1)).
     */
    public void set(int index, double value) {
        genes.setAtIndex(JAVA_DOUBLE, index, value);
    }

    /**
     * Copies the genes into a fresh {@code double[]}.
     *
     * @return a new array with the {@code size()} gene values.
     */
    public double[] toArray() {
        double[] array = new double[size];
        for(int i = 0; i < size; ++i)
            array[i] = genes.getAtIndex(JAVA_DOUBLE, i);
        return array;
    }

    //------------------------------ Extra blob ------------------------------//

    /**
     * Returns the capacity of this chromosome's inline <em>extra</em> blob: the
     * number of auxiliary {@code double} values that travel with the chromosome,
     * separate from the genes.
     *
     * <p>The extra blob is the Java counterpart of extending the C++
     * {@code Chromosome} (a {@code std::vector<double>}) with extra fields to
     * keep per-solution metrics (e.g. makespan and total completion time of a
     * schedule). Use it from the {@link Decoder} to store data derived from the
     * genes so it can be read back later (e.g. for the best solution) without
     * decoding again.</p>
     *
     * <p><b>Consistency / staleness:</b> the blob is just like the fitness — an
     * output the decoder must (re)compute from the genes on <em>every</em> call.
     * It is reliable for any chromosome produced by mating, mutation or
     * (re)initialization (BRKGA always decodes those). It can be stale for a
     * chromosome whose genes were last written by {@code exchangeElite} or by the
     * incorporation step of path relinking, which copy genes without decoding.
     * See the user guide for the full rules.</p>
     *
     * @return the extra-blob capacity ({@code 0} if no blob is available, e.g.
     *         inside a custom distance function).
     */
    public int extraSize() {
        return extraSize;
    }

    /**
     * Returns the extra value at the given index (see {@link #extraSize()}).
     *
     * @param index the index, in {@code [0, extraSize())}.
     * @return the stored value (whatever the decoder last wrote there).
     * @throws IndexOutOfBoundsException if {@code index} is out of range.
     */
    public double getExtra(int index) {
        checkExtraIndex(index);
        return extraSegment().getAtIndex(JAVA_DOUBLE, index);
    }

    /**
     * Stores an auxiliary value in the extra blob at the given index. The value
     * travels with the chromosome and can be read back later via
     * {@link #getExtra(int)} or {@link BrkgaMpIpr#getBestChromosomeExtra()}.
     *
     * @param index the index, in {@code [0, extraSize())}.
     * @param value the value to store.
     * @throws IndexOutOfBoundsException if {@code index} is out of range.
     */
    public void setExtra(int index, double value) {
        checkExtraIndex(index);
        extraSegment().setAtIndex(JAVA_DOUBLE, index, value);
    }

    private void checkExtraIndex(int index) {
        if(index < 0 || index >= extraSize)
            throw new IndexOutOfBoundsException(
                "extra index " + index + " out of bounds for size " + extraSize);
    }

    /// Reinterprets the raw extra segment lazily, so decoders that never touch
    /// the blob pay nothing for it.
    private MemorySegment extraSegment() {
        if(extraView == null)
            extraView = extra.reinterpret((long) extraSize * Double.BYTES);
        return extraView;
    }
}
