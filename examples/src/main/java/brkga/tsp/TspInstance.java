/******************************************************************************
 * TspInstance.java: Java port of tsp/tsp_instance.{hpp,cpp}.
 *
 * Represents an instance for the Traveling Salesman Problem. The constructor
 * loads an upper triangular distance matrix:
 *
 *     number of nodes (n)
 *     dist12 dist13 dist14 ... dist1n
 *     dist23 dist24 ... dist2(n - 1)
 *     ...
 *     dist(n-2)(n-1)
 *
 * This is a faithful, non-idiomatic transcription of the original C++ code.
 *****************************************************************************/

package brkga.tsp;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.BufferedReader;
import java.io.FileReader;

public class TspInstance {

    /// Number of nodes.
    public final int numNodes;

    /// Distances between the nodes.
    public final double[] distances;

    //-------------------------------[ Constructor ]--------------------------//

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

    //-------------------------------[ Distance ]-----------------------------//

    public double distance(int i, int j) {
        if(i > j) {
            int tmp = i; i = j; j = tmp;
        }
        return distances[(i * (numNodes - 1)) - ((i - 1) * i / 2) + (j - i - 1)];
    }
}
