# Guide / Tutorial — BRKGA-MP-IPR for Java

> **About this document.** This guide is a **faithful Java port of the official
> BRKGA-MP-IPR C++ guide**, kept as close as possible to the original; the only
> changes are the conversion of the C++ code and API to Java. The original guide
> lives at <https://ceandrade.github.io/brkga_mp_ipr_cpp/>. Whenever the C++
> library and its guide are updated, this document is updated with the same
> changes.
>
> This guide covers **how to use the algorithm**. For concerns specific to the
> Java edition that matter to users — installation, platform, building the native
> library and selecting the number of objectives, native-access flags, the
> bridge-library location, high-level performance overhead — see
> [JAVA_GUIDE.md](JAVA_GUIDE.md). For the internals of the wrapper, see
> [WRAPPER.md](WRAPPER.md).

This tutorial is based on the single-objective usage of BRKGA. However, almost
all information also applies to the multi-objective mode with minimal changes, as
explained in [Using BRKGA-MP-IPR on multi-objective mode](#using-brkga-mp-ipr-on-multi-objective-mode).

If you desire to understand the depths of the multi-parenting and the implicit
path relinking, read Andrade et al. (2021), *BRKGA-MP-IPR*.

The **reference documentation** (classes, methods, parameters) is provided as
JavaDoc in the source under [`src/main/java/brkga/`](../src/main/java/brkga).

## Contents

- [Installation](#installation)
- [TL;DR — Single objective](#tldr--single-objective)
- [TL;DR — Multi objective](#tldr--multi-objective)
- [Getting started](#getting-started)
- [First things first](#first-things-first)
- [Building the BRKGA-MP-IPR algorithm object](#building-the-brkga-mp-ipr-algorithm-object)
- [It's optimization time](#its-optimization-time)
- [Options before optimization starts](#options-before-optimization-starts)
- [DIY: building an optimization loop for fine control](#diy-building-an-optimization-loop-for-fine-control)
- [Implicit Path Relink](#implicit-path-relink)
- [Shaking, resetting and migration](#shaking-resetting-and-migration)
- [Simulating the standard BRKGA](#simulating-the-standard-brkga)
- [Reading and writing parameters](#reading-and-writing-parameters)
- [Using BRKGA-MP-IPR on multi-objective mode](#using-brkga-mp-ipr-on-multi-objective-mode)
- [(Probably Valuable) Tips](#probably-valuable-tips)
- [References](#references)

---

## Installation

Add the library as a Maven dependency — it ships with the precompiled native
binaries, so **no C++ compiler is needed**:

```xml
<dependency>
    <groupId>io.github.codeurjc</groupId>
    <artifactId>brkga-mp-ipr-java</artifactId>
    <version>0.1.0</version>
</dependency>
```

To follow this guide hands-on, **download the examples**
(`brkga_mp_ipr_java_examples.zip` from the
[GitHub releases](https://github.com/codeurjc/brkga_mp_ipr_java/releases), or use
the `examples/` directory of the repository) and run one:

```bash
$ ./run.sh brkga.single.MainMinimal 27000001 \
    data/config/single.conf 10 data/instances/burma14.dat
```

Full installation, run and platform details are in
[JAVA_GUIDE.md](JAVA_GUIDE.md#installing-the-library-maven). Once it runs, let's
see the essential details on how to use BRKGA-MP-IPR.

---

## TL;DR — Single objective

The best way to keep it short is to look at the examples under
[`src/main/java/brkga/single/`](../../examples/src/main/java/brkga/single). Let's start
solving the traditional single-objective
[Traveling Salesman Problem (TSP)](https://en.wikipedia.org/wiki/Travelling_salesman_problem).

In C++ we first had to make sure `fitness_t` had the right single-objective type
(a `double`). In Java the number of objectives is chosen at run time by creating
the appropriate handle: `Brkga.single()` (one objective), `Brkga.multi()` (two),
or `Brkga.forObjectives(n)`.

Let's take a look at the main call,
[`brkga.single.MainMinimal`](../../examples/src/main/java/brkga/single/MainMinimal.java)
(a trimmed copy):

```java
public static void main(String[] argv) {
    if (argv.length < 4) {
        System.err.println("Usage: MainMinimal <seed> <config-file>"
            + " <maximum-running-time> <tsp-instance-file>");
        System.exit(1);
    }

    try {
        // Read command-line arguments and the instance.
        final int seed = Integer.parseInt(argv[0]);
        final String configFile = argv[1];
        final String instanceFile = argv[3];
        final int numThreads = 4;

        System.out.println("Reading data...");
        TspInstance instance = new TspInstance(instanceFile);

        // Read algorithm parameters.
        System.out.println("Reading parameters...");
        Brkga brkga = Brkga.single();
        Brkga.Config cfg = brkga.readConfiguration(configFile);

        // Overwrite the maximum time (seconds) from the config file.
        cfg.controlParams().maximumRunningTime = Long.parseLong(argv[2]);

        // Build the BRKGA data structure.
        System.out.println("Building BRKGA data and initializing...");
        TspDecoder decoder = new TspDecoder(instance);

        try (BrkgaMpIpr algorithm = new BrkgaMpIpr(
                brkga, decoder, Sense.MINIMIZE, seed,
                instance.numNodes, cfg.brkgaParams(), numThreads)) {

            // Find good solutions / evolve.
            System.out.println("Running for " + argv[2] + " seconds...");
            AlgorithmStatus finalStatus = algorithm.run(cfg.controlParams());

            System.out.println("\nAlgorithm status: " + finalStatus
                + "\n\nBest cost: " + finalStatus.bestFitness[0]);
        }
    } catch (Exception e) {
        System.err.println("\n" + "*".repeat(40)
            + "\nException Occurred: " + e.getMessage()
            + "\n" + "*".repeat(40));
        System.exit(1);
    }
}
```

You can identify the following basic steps:

1. Create a data structure to hold your input data. This object is passed to the
   decoder (example:
   [`brkga.tsp.TspInstance`](../../examples/src/main/java/brkga/tsp/TspInstance.java)).
2. Select the number of objectives by creating the proper handle
   (`Brkga.single()` here).
3. Implement a decoder object (a
   [`Decoder`](../src/main/java/brkga/Decoder.java)). This translates a chromosome
   (array of numbers in the interval [0, 1)) to a solution for your problem, and
   returns the solution value/cost to be used as fitness by BRKGA (example:
   [`brkga.tsp.single.TspDecoder`](../../examples/src/main/java/brkga/tsp/single/TspDecoder.java)).
4. Load the instance and other relevant data.
5. Read the algorithm parameters using `brkga.readConfiguration(...)`; or create
   [`BrkgaParams`](../src/main/java/brkga/BrkgaParams.java) and
   [`ControlParams`](../src/main/java/brkga/ControlParams.java) objects by hand.
6. Create a [`BrkgaMpIpr`](../src/main/java/brkga/BrkgaMpIpr.java) algorithm
   object.
7. Call [`run()`](../src/main/java/brkga/BrkgaMpIpr.java) to optimize.
8. Check the resulting
   [`AlgorithmStatus`](../src/main/java/brkga/AlgorithmStatus.java) for
   optimization information.

[`MainMinimal`](../../examples/src/main/java/brkga/single/MainMinimal.java) provides a very
minimal example to understand the necessary steps. However,
[`MainComplete`](../../examples/src/main/java/brkga/single/MainComplete.java) provides a
full-featured code, handy for scientific use, such as experimentation and paper
writing. This code allows fine-grained control of the optimization, shows several
features of BRKGA-MP-IPR such as the resets, chromosome injection, and others. It
also logs all optimization steps, *creating outputs easy to be parsed.* **You
should use this code for serious business and experimentation.**

These are the basic steps, but I do recommend the reading of this guide.

---

## TL;DR — Multi objective

> **Warning:** Remember, BRKGA-MP-IPR multi-objective mode produces
> **lexicographically dominated solutions** but **no non-dominated solutions**
> (Pareto frontier).

To use BRKGA-MP-IPR in multi-objective mode, in C++ we first had to set
`fitness_t` according to the number of objectives (a `std::tuple<double, double>`
for two objectives). In Java we just create the handle for the desired number of
objectives, and the decoder returns that many values. In the
[repo example](../../examples/src/main/java/brkga/multi) we consider the TSP with two
objectives: minimize the total tour length, and the size of the largest edge in
the tour.

```java
Brkga brkga = Brkga.multi();        // two objectives
// ... the remaining code is almost identical to the single-objective one ...
System.out.println("Best cost:    " + finalStatus.bestFitness[0]
                 + "\nLargest edge: " + finalStatus.bestFitness[1]);
```

The only differences are in computing the largest edge in the decoder, and
printing such information in the main call. All the steps described in the
[previous section](#tldr--single-objective) are also used here.

---

## Getting started

BRKGA-MP-IPR is pretty simple, and you must provide one required *decoder* object
to translate chromosomes to solutions. In general, such decoder uses the problem
information to map a vector of real numbers in the interval [0, 1) to a (valid)
solution. In some cases, even though a valid solution cannot be found, library
users apply penalization factors and push the BRKGA to find valid solutions.

Before you go further, please take a look at the example packages. The classical
[Traveling Salesman Problem (TSP)](https://en.wikipedia.org/wiki/Travelling_salesman_problem)
is used throughout. In the TSP we have a set of cities and the distances between
them; one must find a minimum-cost tour visiting each city once (a Hamiltonian
cycle of minimum cost). The module is organized as follows:

- [`brkga.tsp`](../../examples/src/main/java/brkga/tsp): code to load and build the TSP data
  ([`TspInstance`](../../examples/src/main/java/brkga/tsp/TspInstance.java)), a simple greedy
  heuristic ([`GreedyTour`](../../examples/src/main/java/brkga/tsp/GreedyTour.java)), and the
  warm-start encoder ([`WarmStart`](../../examples/src/main/java/brkga/tsp/WarmStart.java)).
- [`brkga.tsp.single`](../../examples/src/main/java/brkga/tsp/single) /
  [`brkga.tsp.multi`](../../examples/src/main/java/brkga/tsp/multi) /
  [`brkga.tsp.multi3`](../../examples/src/main/java/brkga/tsp/multi3): the TSP decoders.
- [`brkga.single`](../../examples/src/main/java/brkga/single) /
  [`brkga.multi`](../../examples/src/main/java/brkga/multi) /
  [`brkga.multi3`](../../examples/src/main/java/brkga/multi3): the runnable main programs.
  **You should start here!** Please take a look at `brkga.single.MainMinimal`
  before continuing this tutorial.

The `MainMinimal` programs are minimal code useful to understand and test the
framework. The `MainComplete` programs are full-featured, handy for scientific
use: they allow fine-grained control of the optimization, show several features
(path-relinking calls, resets, chromosome injection, and others), and log all
optimization steps. There is also
[`MainMaximumIterations`](../../examples/src/main/java/brkga/single/MainMaximumIterations.java),
which runs for a fixed number of iterations.

How to build and run the examples is described in
[JAVA_GUIDE.md](JAVA_GUIDE.md#building-and-running). When you call an example
without arguments, it shows the usage:

```text
$ ./run.sh brkga.single.MainMinimal
Usage: MainMinimal <seed> <config-file> <maximum-running-time> <tsp-instance-file>
```

The `MainComplete` programs accept docopt-style named options (`--config`,
`--seed`, `--stop_rule <G|I>`, `--stop_arg`, `--maxtime`, `--instance`,
`[--threads]`, `[--no_evolution]`).

A possible output when calling `MainMinimal` is shown below. The final status
block is produced verbatim by `AlgorithmStatus.toString()`:

```text
Reading data...
Reading parameters...
Building BRKGA data and initializing...
Running for 30s...
...
Algorithm status:
best_fitness: 27895
current_iteration: 1635
last_update_iteration: 635
current_time: 28.1722s
last_update_time: 11.6496s
largest_iteration_offset: 159
stalled_iterations: 1000
path_relink_time: 0.076028s
num_path_relink_calls: 4
num_homogenities: 0
num_best_improvements: 0
num_elite_improvements: 1
num_exchanges: 11
num_shakes: 3
num_resets: 1

Best cost: 27895
```

`MainComplete` produces a more verbose output and ends with a CSV one-liner, so
you can extract the last line (e.g. with `tail -n1`) and add it to a CSV table for
your favorite statistics tools.

I hope by now you got your system set up and running. Let's see the essential
details on how to use BRKGA-MP-IPR.

---

## First things first

### The decoder function

The core of the BRKGA algorithm is the definition of a decoder function/object.
The decoder maps the chromosomes (vectors of real numbers in the interval [0, 1))
to solutions of the problem. In some sense, a decoder is similar to a
[kernel function from Support Vector Machines](https://en.wikipedia.org/wiki/Kernel_method):
both functions are used to project solutions/distances in different spaces.

In Java, the decoder implements the
[`Decoder`](../src/main/java/brkga/Decoder.java) interface:

```java
@FunctionalInterface
public interface Decoder {
    double[] decode(Chromosome chromosome, boolean rewrite);
}
```

The decoder **must** implement a `decode()` method that receives a `Chromosome`
and a `boolean`, and returns the fitness. But before going further, let's talk
about the chromosome.

### The chromosome or vector of doubles

Note that all along the BRKGA discussion, the chromosome is represented as a
vector of real numbers in the interval [0, 1). In C++ this is a
`std::vector<double>`. In Java it is represented by the
[`Chromosome`](../src/main/java/brkga/Chromosome.java) class, a clean view of the
genes:

```java
int    size();                       // number of genes
double get(int index);               // gene at index, in [0, 1)
void   set(int index, double value); // rewrite a gene (see "rewrite" below)
double[] toArray();                  // copy the genes out

int    extraSize();                  // capacity of the inline "extra" blob
double getExtra(int index);          // read an extra value
void   setExtra(int index, double v); // store an extra value (see below)
```

Reading a gene is just `chromosome.get(i)`. If you need to keep extra
per-solution metrics during decoding — e.g. makespan and total completion time
for a scheduling problem — you can store them in the chromosome's *extra blob*;
see [Keeping extra data in the chromosome](#keeping-extra-data-in-the-chromosome)
below.

### Keeping extra data in the chromosome

In C++ the chromosome is a "generic" data structure: because `Chromosome` is just
an alias for `std::vector<double>`, you may redefine it (by inheriting from
`std::vector<double>`) to **keep more information inside each chromosome** for
further analysis — solution metrics other than the main fitness value. The
classic example is a scheduling problem where, besides the fitness, you want to
keep the makespan and the total completion time of each solution:

```cpp
// C++ original: extend the chromosome with extra fields.
class Chromosome: public std::vector<double> {
public:
    double makespan;
    double total_completion_time;
};
```

In the Java edition the `Chromosome` class is `final` (it is a zero-copy view
over native memory, so it cannot be subclassed), but it offers the **same
capability** through an inline *extra blob*: a small, fixed-capacity array of
`double`s that travels with each chromosome, separate from the genes and ignored
by every genetic operator. You write it from the decoder and read it back later —
the Java counterpart of the C++ extra fields:

```java
public double[] decode(Chromosome chromosome, boolean rewrite) {
    // ... decode the genes into a schedule ...
    double makespan = /* ... */;
    double totalCompletionTime = /* ... */;

    chromosome.setExtra(0, makespan);              // store metrics in the
    chromosome.setExtra(1, totalCompletionTime);   //   chromosome itself

    return new double[] { makespan };              // the fitness
}
```

Later, you can recover those metrics for the best solution **without decoding
again**:

```java
double[] extra = algo.getBestChromosomeExtra();
double makespan            = extra[0];
double totalCompletionTime = extra[1];
```

`chromosome.extraSize()` (and `algo.extraCapacity()`) report how many `double`s
the blob holds. The capacity is a property of the native library; how to size it
is covered in [JAVA_GUIDE.md](JAVA_GUIDE.md#keeping-extra-data-in-the-chromosome).

> **Alternatives.** If you only need the metrics transiently *during* a decode,
> a plain local variable or a per-thread structure is simpler. If the metrics
> should actually influence selection, encode them as additional objectives in
> the returned fitness instead.

#### When is the extra blob consistent, and when is it stale?

Think of the blob exactly like the fitness: **an output your decoder must
recompute from the genes on every call.** If you follow that rule, the blob is
consistent with the genes everywhere the algorithm decodes a chromosome — which
is everywhere you can normally observe one:

- **Consistent (safe to read):** chromosomes produced by mating, mutation, or
  (re)initialization are always decoded, so their blob matches their genes. Elite
  chromosomes carried over to the next generation keep their genes *and* their
  blob (the copy carries both), so they stay consistent. In a single-population
  run **without** path relinking, `getBestChromosomeExtra()` is therefore always
  consistent.
- **Possibly stale (do not rely on it):** two operations copy genes into a
  chromosome **without decoding it again** — `exchangeElite(...)` (elite
  migration between populations) and the *incorporation* step of path relinking.
  A chromosome last written by either keeps the blob of whatever previously
  occupied that slot, until it is decoded again in a later generation. So if your
  run uses multiple populations with elite exchange, or path relinking, the best
  chromosome might carry a stale blob.

This matches the C++ pattern exactly: there too the library copies genes (via
iterators) in those two operations and never the extra fields. When you need a
guaranteed-correct value in those cases, simply decode `getBestChromosome()` once
to recompute the metrics.

Two more rules of thumb:

- **Write the *whole* blob on every decode.** If you write it only on some paths
  (e.g. only for feasible solutions), a chromosome decoded on a skipped path will
  carry leftover values from a previously decoded chromosome — silently wrong.
- **Do not read an extra value you have not written this call.** Inside the
  decoder, the blob initially holds the previous occupant's data, not anything
  meaningful for the current genes.

### Back to the decoder

Again, **the decoder is the heart of a BRKGA.** An easy way to keep the API clean
is to define a decoder that has a reference to the input data. This is a TSP
decoder:

```java
import brkga.Chromosome;
import brkga.Decoder;
import brkga.tsp.TspInstance;

import java.util.Arrays;

public final class TspDecoder implements Decoder {

    public final TspInstance instance;

    public TspDecoder(TspInstance instance) {
        this.instance = instance;
    }

    @Override
    public double[] decode(Chromosome chromosome, boolean rewrite) {
        int size = chromosome.size();

        // The permutation of nodes induced by the chromosome.
        Integer[] permutation = new Integer[size];
        for(int i = 0; i < size; ++i) permutation[i] = i;
        Arrays.sort(permutation,
            (a, b) -> Double.compare(chromosome.get(a), chromosome.get(b)));

        // Compute the cost of the cycle from that permutation.
        double cost = instance.distance(permutation[0], permutation[size - 1]);
        for(int i = 0; i < size - 1; ++i)
            cost += instance.distance(permutation[i], permutation[i + 1]);

        return new double[] { cost };
    }
}
```

Note that `TspDecoder` gets a reference to `TspInstance`, which holds the input
data. Therefore, `TspDecoder` has direct access to the data for optimization.
This approach also benefits cache efficiency, mainly when multiple threads are
used for decoding, i.e. several threads can use the same read-only data already in
the cache, which speeds up the optimization.

> This is exactly the repository's
> [`brkga.tsp.single.TspDecoder`](../../examples/src/main/java/brkga/tsp/single/TspDecoder.java),
> the decoder the examples use. An additional, optimized variant with identical
> results —
> [`TspDecoderOptimized`](../../examples/src/main/java/brkga/tsp/single/TspDecoderOptimized.java) —
> is also provided; how it is made faster is described in
> [JAVA_GUIDE.md](JAVA_GUIDE.md#writing-a-fast-decoder).

The decode method also has a `rewrite` argument that indicates whether the decoder
should rewrite the chromosome, in case local search / local improvements are
performed during the decoder process. This flag is critical if you intend to use
the Implicit Path Relink (details in [Implicit Path Relink](#implicit-path-relink)).
Even though you do not rewrite the chromosome in your decoder, you must provide
such a signature for API compatibility.

The decoder must return the fitness used to **rank** the chromosomes (a `double[]`
of length 1 for single-objective, or N for an N-objective problem). In general
fitness is the cost/value of the solution, but you may want to use it to penalize
solutions that violate the problem constraints, for example.

In our TSP example, we have a very simple decoder that generates a permutation of
nodes, and computes the cost of the cycle from that permutation (note that we
don't use the flag `rewrite` in this example).

With the instance data and the decoder ready, we can build the BRKGA data
structures and perform the optimization.

> **Warning:** When using multiple threads, **you must guarantee that the decoder
> is thread-safe.** You may want to create all read-write data structures on each
> call or create a separate storage space for each thread (e.g. with a
> `ThreadLocal`). Section [Multi-thread decoding](#multi-thread-decoding) brings
> some tips.

> **Warning:** **The decoder must be a function,** i.e. given a chromosome, it
> must output the same solution/fitness in any call. In other words, the decoder
> must be a deterministic (or, at most, pseudo-random) procedure. BRKGA cannot
> learn well with non-deterministic decoders, and they impair reproducibility.

However, there are several situations where we must toss a coin to break a tie. In
this case, we must guarantee that such a coin always results in the same sequence
of values for a given chromosome. The most used strategy is to create an
(n+1)-sized chromosome such that one allele (in general, the first or the last) is
used as a seed for a random number generator (RNG). In this way the chromosome
also carries the information for breaking ties, and we can reproduce the solution:

```java
long rngSeed = Double.doubleToRawLongBits(chromosome.get(size));
java.util.Random rng = new java.util.Random(rngSeed);
```

---

## Building the BRKGA-MP-IPR algorithm object

[`BrkgaMpIpr`](../src/main/java/brkga/BrkgaMpIpr.java) is the main object that
implements all BRKGA-MP-IPR algorithms such as evolution, path relink, and other
auxiliary procedures.

The first step is to obtain a [`Brkga`](../src/main/java/brkga/Brkga.java) handle
for the desired number of objectives, and then call the algorithm constructor:

```java
Brkga brkga = Brkga.single();   // or Brkga.multi() / Brkga.forObjectives(n)

new BrkgaMpIpr(
    Brkga brkga,                // the handle (carries the number of objectives)
    Decoder decoder,            // the decoder
    Sense sense,                // MINIMIZE or MAXIMIZE
    int seed,                   // seed for the random number generator
    int chromosomeSize,         // number of genes in each chromosome
    BrkgaParams params,         // BRKGA and IPR parameters
    int numThreads              // threads for parallel decoding
);
// Overloads: ..., boolean evolution
//            ..., boolean evolution, DistanceFunction customDistance
```

The `decoder` must implement the `decode()` method as discussed before. You must
also indicate whether you are minimizing or maximizing through
[`Sense`](../src/main/java/brkga/Sense.java).

A good `seed` must be provided for the (pseudo) random number generator.
BRKGA-MP-IPR uses the Mersenne Twister engine.

The `chromosomeSize` must also be given. It indicates the length of each
chromosome in the population. In general, this size depends on the instance and
how the decoder works (in the TSP, the number of nodes, `instance.numNodes`).

`numThreads` defines how many threads the algorithm should use for decoding and
some other operations. As said before, **you must guarantee that the decoder is
thread-safe** when using two or more threads.

Another common argument is `evolution`, enabled by default. When disabled, no
evolution is performed: the algorithm only decodes the chromosomes and ranks
them. This flag helps implement simple multi-start algorithms.

The optional `customDistance` is a CUSTOM IPR
[`DistanceFunction`](../src/main/java/brkga/DistanceFunction.java) (see
[Implicit Path Relink](#implicit-path-relink)).

All BRKGA and Path Relink hyper-parameters are stored in a
[`BrkgaParams`](../src/main/java/brkga/BrkgaParams.java) object. There is also a
companion [`ControlParams`](../src/main/java/brkga/ControlParams.java) object that
stores extra control parameters used outside BRKGA-MP-IPR to control several
aspects of the optimization. Both can be read from a plain text configuration file
(see [`config.conf`](../../examples/data/config/single.conf)):

```text
# BRKGA and IPR parameters
population_size 2000
elite_percentage 0.30
mutants_percentage 0.15
num_elite_parents 2
total_parents 3
bias_type LOGINVERSE
num_independent_populations 3
pr_number_pairs 0
pr_minimum_distance 0.15
pr_type DIRECT
pr_selection BESTSOLUTION
pr_distance_function_type KENDALLTAU
alpha_block_size 1.0
pr_percentage 1.0
num_exchange_individuals 1
shaking_type SWAP
shaking_intensity_lower_bound 0.25
shaking_intensity_upper_bound 0.75

# Control parameters
maximum_running_time 60
exchange_interval 100
ipr_interval 200
shake_interval 300
reset_interval 500
stall_offset 100
```

The file uses snake_case keys; the Java POJO fields use camelCase
(`populationSize`, `maximumRunningTime`, …). To read this file, use
`brkga.readConfiguration(file)`, which returns a `Brkga.Config` exposing
`brkgaParams()` and `controlParams()`. When reading, blank lines and lines
starting with `#` are ignored. `ControlParams` contains extra control parameters
and is not mandatory to BRKGA-MP-IPR itself.

```java
final int seed = Integer.parseInt(argv[0]);
final String configFile = argv[1];
final String instanceFile = argv[3];
final int numThreads = 4;

TspInstance instance = new TspInstance(instanceFile);

Brkga brkga = Brkga.single();
Brkga.Config cfg = brkga.readConfiguration(configFile);

// Overwrite the maximum time from the config file with the command-line one.
cfg.controlParams().maximumRunningTime = Long.parseLong(argv[2]);

TspDecoder decoder = new TspDecoder(instance);

BrkgaMpIpr algorithm = new BrkgaMpIpr(
    brkga, decoder, Sense.MINIMIZE, seed,
    instance.numNodes, cfg.brkgaParams(), numThreads);
```

Now we have a `BrkgaMpIpr` object which will be used to call all other functions
during the optimization. Note that we can build several `BrkgaMpIpr` objects using
different parameters, decoders, or instance data. These can be evolved
independently. Each one holds a self-contained BRKGA state including populations,
fitness information, and a state of the random number generator.

> `BrkgaMpIpr` implements `AutoCloseable`. Use it within a try-with-resources
> block so its resources are released.

---

## It's optimization time

Until version 2.0, the user was responsible for creating the main optimization
loop. While this strategy gives fine control over the algorithm's flow, it
generates cumbersome code. In version 3.0, all these details are abstracted into a
single method, [`run()`](../src/main/java/brkga/BrkgaMpIpr.java), containing the
complete optimization loop, which may use all the features provided by this
library:

```java
AlgorithmStatus finalStatus = algorithm.run(cfg.controlParams());
```

`run()` takes a [`ControlParams`](../src/main/java/brkga/ControlParams.java)
object which contains several control parameters governing how the main loop
behaves. It is through these control parameters that the user can control the
maximum optimization time and when features like IPR, shaking, etc. are called.
(In C++, `run()` also takes an output stream to log information; in Java the
progress log goes to standard output and can be silenced with
`algorithm.setQuiet(true)`.)

Once done, `run()` returns an
[`AlgorithmStatus`](../src/main/java/brkga/AlgorithmStatus.java) object that brings
all the details about the optimization itself, such as the number of iterations,
running time, number of calls for each method, and others. Most importantly,
`AlgorithmStatus` also brings the fitness (`bestFitness`, a `double[]`) and the
chromosome representing the best solution found during the optimization (note that
it is not necessarily the best chromosome in the current population, because that
may be fully reset and lose the best solution).

The main loop, when all thresholds are equal, behaves like this:

```text
while (!must_stop) {
    evolve();                         // One generation.
    if (best solution improvement) { Save best solution; Call observer callbacks; }

    if (!must_stop && ipr_interval      > 0 && stalled_iterations % ipr_interval      == 0) pathRelink();
    if (!must_stop && exchange_interval > 0 && stalled_iterations % exchange_interval == 0) exchangeElite();
    if (!must_stop && shake_interval    > 0 && stalled_iterations % shake_interval    == 0) shake();
    if (!must_stop && reset_interval    > 0 && stalled_iterations % reset_interval    == 0) reset();
}
```

Therefore, the order in which `pathRelink()`, `exchangeElite()`, `shake()`, and
`reset()` are called depends on the thresholds defined in `ControlParams`.

For path relinking, the block size is computed by ⌈ α × √p ⌉ where α is
`alphaBlockSize` and p is `populationSize`. If the size is larger than the
chromosome size, the size is set to half of the chromosome size. For more details,
refer to [Implicit Path Relink](#implicit-path-relink).

> **Note:** The algorithm always tests against the maximum running time and the
> maximum stalled iterations/generations given by `ControlParams`, independently
> of the stopping-criteria function supplied by the user. This is especially
> important when activating the implicit path relinking, which is **very
> time-consuming**. If you are using IPR, we **STRONGLY RECOMMEND SETTING A
> MAXIMUM TIME** since this is the core stopping criterion on IPR.

> **Warning:** The decoding is done in parallel using threads, and the user **must
> guarantee that the decoder is THREAD-SAFE.** If such a property cannot be held,
> we suggest using a single thread for optimization.

---

## Options before optimization starts

While we can call `run()` right away, some options can enhance the pipeline before
running the optimization. We can:

- Set solution-observer callbacks that are called when the best solution is
  updated;
- Set a custom stopping-criteria function other than solely time and stalled
  iterations;
- Set a custom shake procedure instead of using the canonical options;
- Set a custom bias function for chromosome ranking;
- Provide warm-start solutions to the algorithm to improve general solution
  quality and convergence.

### Setting solution observers / callbacks

`run()` provides a callback mechanism activated when the best solution found so
far during the optimization is improved. This is done with
[`addNewSolutionObserver`](../src/main/java/brkga/BrkgaMpIpr.java), which takes a
[`NewSolutionObserver`](../src/main/java/brkga/NewSolutionObserver.java):

```java
algorithm.addNewSolutionObserver(status -> {
    System.out.println("> Iter: " + status.currentIteration
        + " | solution: " + status.bestFitness[0]
        + " | time: " + status.currentTime + "s");
    return true;   // return true to keep running, false to stop immediately
});
```

The callback receives the current
[`AlgorithmStatus`](../src/main/java/brkga/AlgorithmStatus.java), which provides
the current optimization status — the primary way to track the algorithm's
convergence — and returns a `boolean`. In this Java API, returning **`true` keeps
the optimization running** and `false` aborts it immediately (this matches the
actual behavior of the C++ `run()` loop). This is useful when one wants only to
obtain a solution with a particular value and stop to save time.

You can add as many observers as you want; they will be called in the order they
are added.

One interesting usage of such callbacks is to perform an (expensive) local search
from the best solution when this cannot be done during the decoder. Once done, you
can inject the improved chromosome back into the population with
[`injectChromosome`](#injecting-solutions--chromosome-into-the-population).

### Defining custom stopping criteria

By default, the algorithm always tests for **the maximum running time** and for
**the maximum stalled iterations/generations** given by `ControlParams`. However,
the user may want to evaluate additional criteria. For example, in a minimization
problem, we may want to stop within a distance from a lower bound, or when a given
number of iterations is reached. Use
[`setStoppingCriteria`](../src/main/java/brkga/BrkgaMpIpr.java) (a
[`StoppingCriteria`](../src/main/java/brkga/StoppingCriteria.java)), which returns
`true` when the optimization must **stop**:

```java
double lowerBound = computeLowerBound();
int maxIterations = 100;

algorithm.setStoppingCriteria(status ->
    status.bestFitness[0] <= lowerBound * 1.1     // 10% from the lower bound
    || status.currentIteration == maxIterations);
```

> **Warning:** If you are using implicit path relinking (IPR), which is **very
> time-consuming**, we **STRONGLY RECOMMEND SETTING A MAXIMUM TIME**.

If you really mean to have no maximum time and/or maximum stalled iterations set:

```java
cfg.controlParams().maximumRunningTime = Long.MAX_VALUE;       // no time limit
cfg.controlParams().stallOffset = (int) 0xFFFFFFFFL;           // no stall limit
```

### Providing custom shake procedure

BRKGA-MP-IPR supplies two canonical shaking procedures (see
[Shaking](#shaking-resetting-and-migration)), effective in most cases. For
situations that call for a custom procedure, use
[`setShakingMethod`](../src/main/java/brkga/BrkgaMpIpr.java), a
[`ShakingMethod`](../src/main/java/brkga/ShakingMethod.java). The populations are
accessed through the mutable view
[`ShakingPopulations`](../src/main/java/brkga/ShakingPopulations.java). We have:

- `lowerBound` and `upperBound` are the shaking intensity bounds (usually defining
  a range from which the intensity is sampled);
- the `ShakingPopulations` view exposes `numPopulations()`, `populationSize(p)`,
  `chromosomeSize(p)`, `getAllele(p, c, a)`, `setAllele(p, c, a, v)`, and
  `markShaken(p, c)` (the list of `<population, chromosome>` pairs that were shaken
  and must be re-decoded).

The example below implements the standard mutation of vanilla genetic algorithms.
Note that the random number generator is kept outside, to generate different
sequences on each call:

```java
java.util.Random rng = new java.util.Random(2700001);

algorithm.setShakingMethod((lowerBound, upperBound, pops) -> {
    for (int p = 0; p < pops.numPopulations(); ++p) {
        int popSize = pops.populationSize(p);
        int chrSize = pops.chromosomeSize(p);
        for (int c = 0; c < popSize; ++c) {
            boolean changed = false;
            for (int i = 0; i < chrSize; ++i) {
                if (rng.nextBoolean()) {
                    double value = lowerBound + (upperBound - lowerBound) * rng.nextDouble();
                    pops.setAllele(p, c, i, value);
                    changed = true;
                }
            }
            if (changed) pops.markShaken(p, c);   // mark to be re-decoded
        }
    }
});
```

> **Note:** If nothing is marked, **all** chromosomes of all populations are
> re-decoded. This may be slow; even if that is your intention, it is faster to
> populate the marked set.

> **Warning:** This procedure can be **very intrusive** since it must manipulate
> the population. The user must keep BRKGA invariants such as chromosome size and
> population size.

### Setting custom bias function

The bias function controls how alleles are chosen from the (multi) parents during
mating. While BRKGA-MP-IPR provides an extensive set of functions through
[`BiasFunctionType`](../src/main/java/brkga/BiasFunctionType.java), one may want a
custom function. Use
[`setBiasCustomFunction`](../src/main/java/brkga/BrkgaMpIpr.java) (a
[`BiasFunction`](../src/main/java/brkga/BiasFunction.java)), supplying a **positive
non-increasing function**:

```java
algorithm.setBiasCustomFunction(r -> 1.0 / (r * r));   // inverse quadratic
```

> **Warning:** The bias function must be a **positive non-increasing function**,
> i.e. f(i) ≥ 0 and f(i) ≥ f(i+1) for i ∈ [1, …, totalParents]. The API tests the
> function and throws if it is not positive and non-increasing.

### Injecting warm-start solutions

One good strategy is to bootstrap the main algorithm with good solutions from fast
heuristics. Since BRKGA-MP-IPR does not know the problem structure, you must
*encode* the warm-start solution as chromosomes (vectors in [0, 1)) — the inverse
process of your decoder. This is a piece of
[`MainComplete`](../../examples/src/main/java/brkga/single/MainComplete.java) (see
[`brkga.tsp.WarmStart`](../../examples/src/main/java/brkga/tsp/WarmStart.java)):

```java
GreedyTour.Result initialSolution = GreedyTour.greedyTour(instance);

// Encode the tour as a chromosome: sorted random keys assigned in tour order.
double[] initialChromosome = WarmStart.buildInitialChromosome(
    chromosomeSize, initialSolution.tour(), seed);

algorithm.setInitialPopulation(new double[][] { initialChromosome });
```

We create a vector of sorted random keys and assign the i-th smallest key to node
`tour[i]`, so the decoder (which sorts the genes) reconstructs the same
permutation. Finally,
[`setInitialPopulation`](../src/main/java/brkga/BrkgaMpIpr.java) assigns the
incumbents to the initial population. It takes a `double[][]`, so you can have as
many warm-start solutions as you like, limited to the population size.

---

## DIY: building an optimization loop for fine control

While version 3.0 greatly enhances how to utilize all BRKGA-MP-IPR features
transparently, one may want to change the algorithm's flow (e.g. to develop a
hyperheuristic). All public interfaces from version 2.0 are kept.

### Evolving the population

The evolution of each generation is performed by
[`evolve()`](../src/main/java/brkga/BrkgaMpIpr.java), which takes the number of
generations to evolve:

```java
algorithm.evolve(numGenerations);   // evolve all populations for numGenerations
algorithm.evolve();                 // a single generation
```

`run()` calls `evolve()` for one generation per iteration of the main loop. In a
custom setting, one may evolve several generations per iteration if it makes sense.

> **Note:** `evolve()` does not check the stopping criteria and only stops when
> the given iterations are done. Be careful when evolving multiple generations at
> once with a costly decoder.

> **Warning:** Again, the decoding of each chromosome is done in parallel if
> multi-threading is enabled. **We must guarantee that the decoder is
> THREAD-SAFE.**

### Accessing solutions/chromosomes

Most commonly, we want the best chromosome of the current population after some
iterations:

```java
double[] bestFitness    = algorithm.getBestFitness();      // value/fitness of the best
double[] bestChromosome = algorithm.getBestChromosome();   // a copy of the best chromosome
```

You may also want a specific chromosome and its fitness for a given population:

```java
double[] thirdBestChr     = algorithm.getChromosome(1, 2); // 3rd best of the 2nd population
double[] thirdBestFitness = algorithm.getFitness(1, 2);
```

> **Note:** Indices are zero-based. The first population index is 0, the best
> chromosome is at position 0, and so forth.

> **Warning:** All the get methods return information from the **current
> population**, not the best solution found overall. After a `shake()` or
> `reset()`, the best solution may be lost. Only `run()` keeps the best solution
> overall (in the returned `AlgorithmStatus`).

You can also obtain a read-only view of a whole population with
[`getCurrentPopulation`](../src/main/java/brkga/Population.java):

```java
Population pop = algorithm.getCurrentPopulation(0);
int n = pop.size();
double[] best = pop.getChromosome(0);
double[] bestFit = pop.getBestFitness();
```

### Injecting solutions / chromosome into the population

Suppose you get a chromosome and apply a quick local search on it. It may be
useful to reinsert the new solution for the next evolutionary cycles, using
[`injectChromosome`](../src/main/java/brkga/BrkgaMpIpr.java). The new chromosome
is put in a specific position of a given population, replacing the old one; the
decoder is triggered to compute the new fitness, and the population is re-sorted:

```java
double[] keys = new double[instance.numNodes];
// ... fill keys with values in [0, 1) ...
// Replace the worst (last position) of population 0:
algorithm.injectChromosome(keys, 0, brkgaParams.populationSize - 1);
```

---

## Implicit Path Relink

The Implicit Path Relinking (IPR) is a nice addition to the standard BRKGA
framework, and it provides an excellent way to create hybrid heuristics and push
the optimization further. The good thing about IPR is that you do not need to
worry about the path-relink implementation, which can be long and tedious if done
by hand. In Java the two C++ overloads are unified into a single method
[`pathRelink`](../src/main/java/brkga/BrkgaMpIpr.java):

```java
PathRelinking.PathRelinkingResult pathRelink(
    PathRelinking.Type prType,                       // DIRECT or PERMUTATION
    PathRelinking.Selection prSelection,             // BESTSOLUTION or RANDOMELITE
    PathRelinking.DistanceFunctionType distType,     // HAMMING, KENDALLTAU, or CUSTOM
    int numberPairs,                                 // pairs to test (0 = all)
    double minimumDistance,                          // minimum distance between chromosomes
    long blockSize,                                  // alleles exchanged at once
    long maxTimeSeconds,                             // 0 = no limit
    double percentage);                              // path size, (0, 1]
```

The first argument defines the type of implicit path relink
([`PathRelinking.Type`](../src/main/java/brkga/PathRelinking.java)). `DIRECT`
exchanges the keys of two chromosomes directly, usually more suitable for
threshold representations (where key values are discretized, e.g. *"if x < 0.5,
then 0, otherwise 1"*). `PERMUTATION` switches the order of a key according to its
position in the other chromosome, usually more suitable for permutation
representations (e.g. the TSP), where the chromosome induces an order.

`PathRelinking.Selection` defines how the algorithm picks the chromosomes for
relinking. `BESTSOLUTION` selects, in order, the best solution of each population;
`RANDOMELITE` chooses uniformly random solutions from the elite sets.

The next argument selects the distance function used to determine if two
chromosomes are far enough apart, and whether changing a (block of) alleles
changes the solution. The built-ins are the (modified)
[Hamming distance](https://en.wikipedia.org/wiki/Hamming_distance) (`HAMMING`, for
threshold representations) and the
[Kendall Tau distance](https://en.wikipedia.org/wiki/Kendall_tau_distance)
(`KENDALLTAU`, for permutation representations); or `CUSTOM` to supply your own.

For `CUSTOM`, implement
[`DistanceFunction`](../src/main/java/brkga/DistanceFunction.java) and pass it to
the `BrkgaMpIpr` constructor:

```java
public interface DistanceFunction {
    double distance(Chromosome v1, Chromosome v2);
    boolean affectSolution(double key1, double key2);
    boolean affectSolutionBlock(Chromosome v1, Chromosome v2, int blockSize);
}
```

If the value returned by `distance()` is ≥ `minimumDistance`, the algorithm
performs the path relink between the two chromosomes; otherwise it looks for
another pair (it tries `numberPairs` pairs before giving up). In the presence of
multiple populations, path relinking is performed between elite chromosomes from
different populations, in a circular fashion (3 populations → 1↔2, 2↔3, 3↔1).

To avoid changes that do not lead to new solutions, the distance functor
implements `affectSolution()`. The single-key version checks whether exchanging
`key1` for `key2` could change the solution. The block version checks `blockSize`
keys at once (each `Chromosome` argument is a view holding exactly `blockSize`
genes). As a simple example, a ternary-threshold Hamming distance:

```java
import brkga.Chromosome;
import brkga.DistanceFunction;

public final class TernaryHammingDistance implements DistanceFunction {
    private double value(double key) {
        return key < 0.33 ? 0.0 : (key < 0.66 ? 1.0 : 2.0);
    }
    @Override public double distance(Chromosome v1, Chromosome v2) {
        double dist = 0.0;
        for (int i = 0; i < v1.size(); ++i)
            dist += Math.abs(value(v1.get(i)) - value(v2.get(i)));
        return dist;
    }
    @Override public boolean affectSolution(double key1, double key2) {
        return Math.abs(value(key1) - value(key2)) > 0.0;
    }
    @Override public boolean affectSolutionBlock(Chromosome v1, Chromosome v2, int blockSize) {
        for (int i = 0; i < blockSize; ++i)
            if (Math.abs(value(v1.get(i)) - value(v2.get(i))) > 0.0) return true;
        return false;
    }
}
```

> **Note:** `affectSolution()` is crucial to IPR performance, since it helps avoid
> exploring regions already surveyed, and it can incorporate problem knowledge.

> **Note:** The current permutation path relink does not use `affectSolution()`,
> but the interface requires it. You can return a constant `true` in that case.

`blockSize` defines the number of keys exchanged at once during the direct path
relink. It is critical for performance. A good choice is
`blockSize = alphaBlockSize × √(chromosome size)` (`alphaBlockSize` from the
config file). The last two parameters are stopping criteria: the algorithm stops
when `maxTimeSeconds` is reached or `percentage` of the path is built.

> **Warning:** **IPR is a very time-intensive process. You must set the stopping
> criteria accordingly.**

A call using the loaded parameters (permutation-based, Kendall-Tau):

```java
PathRelinking.PathRelinkingResult result = algorithm.pathRelink(
    brkgaParams.prType,
    brkgaParams.prSelection,
    PathRelinking.DistanceFunctionType.KENDALLTAU,
    brkgaParams.prNumberPairs,
    brkgaParams.prMinimumDistance,
    1,                          // blockSize (irrelevant for permutation)
    maxTimeRemainingSeconds,    // remaining optimization time
    brkgaParams.prPercentage);
```

[`pathRelink`](../src/main/java/brkga/BrkgaMpIpr.java) returns a
[`PathRelinking.PathRelinkingResult`](../src/main/java/brkga/PathRelinking.java).
Four situations may happen:

- `TOO_HOMOGENEOUS`: the chromosomes among the populations are too homogeneous and
  the path relink will not generate improved solutions. This is linked to the
  chosen distance function and minimum distance.
- `NO_IMPROVEMENT`: path relink was done but no improved solution was found.
- `ELITE_IMPROVEMENT`: an improved solution among the elite set was found, but the
  best solution was not improved.
- `BEST_IMPROVEMENT`: the best solution was improved.

If the found solution is the best so far, IPR replaces the worst solution by it.
Otherwise, IPR computes the distance between the found solution and all others in
the elite set, and replaces the worst solution only if the found solution is at
least `minimumDistance` from all of them.

### Important notes about IPR

IPR always calls `decode()` with `rewrite = false`. The reason is that if the
decoder rewrites the chromosome, the path between solutions is lost and inadvertent
results may come up. At the end of the path relinking, the method calls the
decoder with `rewrite = true` on the best chromosome found, to guarantee it
reflects the best solution.

> **Warning:** Make sure your decoder does not rewrite the chromosome when called
> with `rewrite = false`.

Conversely, when BRKGA calls your decoder with `rewrite = true` (during evolution,
injection and initialization), the decoder may write back into the chromosome with
`Chromosome.set(i, value)` — for example to store a locally improved solution. The
change is kept by the algorithm.

---

## Shaking, resetting and migration

### Shaking

Sometimes BRKGA gets stuck, converging to local optima for several iterations.
When that happens, it is a good idea to perturb the population with
[`shake`](../src/main/java/brkga/BrkgaMpIpr.java):

```java
void shake(int intensity, ShakingType shakingType, int populationIndex);
```

`intensity` measures how many times the perturbation is applied to the elite set
of the given `populationIndex` (if `populationIndex >=
numIndependentPopulations`, all populations are shaken). There are two canonical
[`ShakingType`](../src/main/java/brkga/ShakingType.java)s: `CHANGE` performs direct
modifications of keys/alleles (recommended for direct or threshold
representations); `SWAP` exchanges keys/alleles, inducing new permutations. For
instance, to shake all populations with 10 swap moves:

```java
algorithm.shake(10, ShakingType.SWAP, Integer.MAX_VALUE);
```

If the provided shakings are not appropriate for your problem, you can provide a
custom procedure (see
[Providing custom shake procedure](#providing-custom-shake-procedure)).

### Resetting

Sometimes even shaking does not help escape from local optima, so you need a
drastic measure: restart the whole population from scratch with
[`reset`](../src/main/java/brkga/BrkgaMpIpr.java):

```java
algorithm.reset();
```

> **Note:** When using `reset()`, all warm-start solutions provided by
> `setInitialPopulation()` are discarded. You may use `injectChromosome()` to
> insert those solutions again.

> **Warning:** Again, the decoding is done in parallel if multi-threading is
> enabled, so **the decoder must be THREAD-SAFE.**

### Multi-population and migration

The multi-population or *island model* evolves parallel and independent
populations and, once in a while, exchanges individuals among them. To use
multiple populations, set `brkgaParams.numIndependentPopulations` to 2 or more.
The immigration process is implemented by
[`exchangeElite`](../src/main/java/brkga/BrkgaMpIpr.java):

```java
algorithm.exchangeElite(3);   // exchange the 3 best individuals from each population
```

`exchangeElite()` copies `num_immigrants` individuals from one population to
another, replacing the worst ones from the recipient. Migration is done for all
pairs of populations. If there is only one population, nothing is done.

---

## Simulating the standard BRKGA

Sometimes it is a good idea to test how the standard BRKGA performs. First,
guarantee that during crossover the algorithm chooses only one elite and one
non-elite individual, by setting `numEliteParents = 1` and `totalParents = 2`.
Then, set up a bias function that ranks the elite and non-elite individuals
according to the original BRKGA bias parameter ρ (rho):

```java
brkgaParams.numEliteParents = 1;
brkgaParams.totalParents = 2;

final double rho = 0.75;   // the original vanilla-BRKGA rho
algorithm.setBiasCustomFunction(x -> x == 1 ? rho : 1.0 - rho);
```

If the chromosome index is 1 (elite individual), it gets a 0.75
rank/probability; if the index is 2 (non-elite), it gets 0.25.

> **Warning:** Index 1 is the elite individual (not 0), and index 2 is the
> non-elite (not 1). Internally, BRKGA always passes `r > 0` to the bias function
> to avoid division-by-zero.

---

## Reading and writing parameters

Although you can build the parameters by hand, the easiest way is to read them
from a configuration file with
[`Brkga.readConfiguration`](../src/main/java/brkga/Brkga.java):

```java
Brkga.Config cfg = brkga.readConfiguration("tuned.conf");
BrkgaParams brkgaParams = cfg.brkgaParams();
ControlParams controlParams = cfg.controlParams();
```

The configuration file is plain text containing pairs of parameter name and value.
Case does not matter; blank lines and lines starting with `#` are ignored; the
order of parameters does not matter. The same file is readable by the C++, Julia
and Python editions.

To save parameters defined at run time, use
[`Brkga.writeConfiguration`](../src/main/java/brkga/Brkga.java):

```java
brkga.writeConfiguration("test.conf", brkgaParams, controlParams);
```

> **Note:** `writeConfiguration` **overwrites** the given file.

---

## Using BRKGA-MP-IPR on multi-objective mode

BRKGA-MP-IPR also deals with multiple objectives in a **lexicographical** (or
priority) dominance order. Differing from the classical non-dominance order
(Pareto frontiers), the lexicographical order defines a *strict preference order*
among the objective functions:

> Let A = (f₁, …, fₙ) and A' = (f'₁, …, f'ₙ). A is lexicographically smaller than
> A' (A < A') if and only if f₁ < f'₁, or f₁ = f'₁ and f₂ < f'₂, or …, f₁ = f'₁,
> …, fₙ₋₁ = f'ₙ₋₁ and fₙ < f'ₙ.

For instance, with three minimizing objectives:

| Solution | f₁ | f₂ | f₃ |
|---|---|---|---|
| A | 50 | 30 | 30 |
| B | 30 | 55 | 40 |
| C | 30 | 20 | 50 |
| D | 30 | 20 | 25 |

Solution B is better than A because f₁(B) < f₁(A), even though A has better f₂ and
f₃. C is better than B because, with f₁ equal, f₂(C) < f₂(B). D has the best value
for all objectives. Therefore D < C < B < A.

> **Warning:** If you really want an algorithm that produces a **non-dominated set
> of solutions (Pareto frontier)**, this is **not** the right algorithm. Take a
> look at [NSGA-II](https://doi.org/10.1109/4235.996017) and the
> [MOEA Framework](https://en.wikipedia.org/wiki/MOEA_Framework).

In C++ you must redefine `fitness_t` (e.g. `std::tuple<double, double>`) and
recompile. In Java you only create the handle for the desired number of
objectives, at run time:

```java
Brkga brkga = Brkga.multi();           // 2 objectives
Brkga brkga = Brkga.forObjectives(3);  // 3 objectives
```

The decoder returns that many values, and `AlgorithmStatus.bestFitness` has the
corresponding length. Excerpt from the two-objective decoder
([`brkga.tsp.multi.TspDecoder`](../../examples/src/main/java/brkga/tsp/multi/TspDecoder.java)),
minimizing total cost and the largest edge:

```java
return new double[] { cost, largestEdge };
```

How the Java edition supports any number of objectives (and how to build a
dedicated, faster binary for a fixed N) is described in
[JAVA_GUIDE.md](JAVA_GUIDE.md#number-of-objectives).

---

## (Probably Valuable) Tips

### Algorithm warmup

In Java, the JVM's JIT compiles the hot path after a few executions. For clean
timing measurements, a short warmup helps (see the decode-overhead benchmark
under [`bench/`](../bench), whose `Benchmark` performs an optional warmup).
Pre-allocating the data structures you know in advance speeds up the decoding
process.

### Complex decoders and timing

Some problems require complex decoders, or decoders that contain time-consuming
local searches. The decoding is usually the most time-expensive component of a
BRKGA, and it may skew stopping criteria based on running time. If your decoder is
time-consuming, test the time at the very beginning of the decoder: if the current
time exceeds the maximum allowed, simply return `+∞` or `-∞` (according to your
optimization sense) to make the solution **invalid**; the framework takes care of
the rest. Testing the clock uses CPU cycles, so be careful where you test it.

### Multi-thread decoding

BRKGA can decode (evaluate) solutions in parallel, which makes the use of
multi-threading relatively straightforward. The number of threads is the
`numThreads` constructor argument.

> **The decoder must be THREAD-SAFE.** Each thread must have its own read/write
> data structures and may share read-only data. The simplest way is to create
> those structures inside the decoder, but that slows things down; the recommended
> way is to **pre-allocate a buffer per thread** (e.g. with a `ThreadLocal`, as
> the optimized
> [`brkga.tsp.single.TspDecoder`](../../examples/src/main/java/brkga/tsp/single/TspDecoder.java)
> does).

> **Warning:** Multi-threading consumes many resources and has diminishing
> returns. Use **at most the number of physical cores** (maybe −1). Complex
> decoders benefit more from multi-threading than simple, fast ones.

For how to configure the thread count and other Java-edition runtime details, see
[JAVA_GUIDE.md](JAVA_GUIDE.md).

### Multi-thread mating

The mating (crossover) can also be performed in parallel, which speeds up the
algorithm substantially for large chromosomes and populations. In the Java
edition this is a build-time setting of the native library, with implications for
reproducibility; see
[JAVA_GUIDE.md](JAVA_GUIDE.md#reproducibility-and-threads).

---

## References

- C. E. Andrade, R. F. Toso, J. F. Gonçalves, M. G. C. Resende. *The Multi-Parent
  Biased Random-Key Genetic Algorithm with Implicit Path Relinking and its
  real-world applications.* European Journal of Operational Research,
  289(1):17–30, 2021.
- Original C++ guide and documentation:
  <https://ceandrade.github.io/brkga_mp_ipr_cpp/>.
- [JAVA_GUIDE.md](JAVA_GUIDE.md) — Java-edition concerns (setup, platform,
  objectives, performance). [WRAPPER.md](WRAPPER.md) — wrapper internals.
