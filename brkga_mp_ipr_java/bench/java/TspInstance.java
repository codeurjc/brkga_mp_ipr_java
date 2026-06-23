/******************************************************************************
 * TspInstance.java: minimal, self-contained TSP instance for the benchmark.
 *
 * The benchmark's OWN copy (package `bench`), independent of the Java examples
 * project, so it can be adapted without affecting them. Loads an upper
 * triangular distance matrix (same format as the library's TSP instances):
 *
 *     number of nodes (n)
 *     dist12 dist13 ... dist1n
 *     dist23 ... dist2(n-1)
 *     ...
 *****************************************************************************/

package bench;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.StreamTokenizer;

public class TspInstance {

    public final int numNodes;
    public final double[] distances;

    public TspInstance(String filename) throws IOException {
        try(BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            StreamTokenizer in = new StreamTokenizer(reader);
            if(in.nextToken() == StreamTokenizer.TT_EOF)
                throw new IOException("Error reading the instance file");
            this.numNodes = (int) in.nval;

            this.distances = new double[(numNodes * (numNodes - 1)) / 2];
            for(int i = 0; i < distances.length; ++i) {
                if(in.nextToken() == StreamTokenizer.TT_EOF)
                    throw new IOException("Error reading the instance file");
                distances[i] = in.nval;
            }
        }
    }

    public double distance(int i, int j) {
        if(i > j) { int t = i; i = j; j = t; }
        return distances[(i * (numNodes - 1)) - ((i - 1) * i / 2) + (j - i - 1)];
    }
}
