/******************************************************************************
 * GreedyTour.java: Java port of heuristics/greedy_tour.{hpp,cpp}.
 *
 * Builds a greedy nearest-neighbour tour starting from node 0, used as a warm
 * start for BRKGA. Faithful transcription of the original C++ code.
 *****************************************************************************/

package brkga.tsp;

import java.util.ArrayList;
import java.util.List;

public final class GreedyTour {

    private GreedyTour() {}

    /// Result of the greedy heuristic: the tour cost and the visiting order.
    public record Result(double cost, int[] tour) {}

    public static Result greedyTour(TspInstance instance) {
        int[] tour = new int[instance.numNodes];
        tour[0] = 0;

        // remainingNodes = [1, 2, ..., numNodes - 1]
        List<Integer> remainingNodes = new ArrayList<>(instance.numNodes - 1);
        for(int i = 1; i < instance.numNodes; ++i)
            remainingNodes.add(i);

        double cost = 0.0;
        int current = 0;
        int idx = 1;

        while(!remainingNodes.isEmpty()) {
            double bestDist = Double.MAX_VALUE;
            int nextIndex = 0;
            for(int it = 0; it < remainingNodes.size(); ++it) {
                double dist = instance.distance(current, remainingNodes.get(it));
                if(dist < bestDist) {
                    bestDist = dist;
                    nextIndex = it;
                }
            }
            cost += bestDist;
            int next = remainingNodes.get(nextIndex);
            tour[idx++] = next;
            current = next;
            remainingNodes.remove(nextIndex);
        }
        cost += instance.distance(tour[0], tour[tour.length - 1]);

        return new Result(cost, tour);
    }
}
