package org.example.grid;

import java.util.*;

/**
 * Pathfinder — thuật toán tìm đường trên {@link HexGrid}.
 *
 * Hiện tại: Dijkstra 2 tiêu chí (bước / nhiên liệu), độ phức tạp O(E log V).
 *
 * TODO (nâng cấp hiệu năng — xem PHASE 1 trong pipeline đã thảo luận):
 *   Vì trọng số mỗi ô chỉ nhận 4 giá trị rời rạc (1,2,3,4 bước), có thể thay
 *   PriorityQueue bằng DIAL'S ALGORITHM (bucket queue) để giảm hằng số thời gian,
 *   quan trọng khi phải chạy multi-source SSSP mỗi đầu ngày cho nhiều agent.
 */
public final class Pathfinder {

    private Pathfinder() {}

    public static class DijkstraResult {
        public final Map<Integer, Integer> distSteps = new HashMap<>();
        public final Map<Integer, Integer> distFuel = new HashMap<>();
        public final Map<Integer, int[]> prev = new HashMap<>(); // [parent, direction]
    }

    private static class Node implements Comparable<Node> {
        final int c1, c2, pos;
        Node(int c1, int c2, int pos) { this.c1 = c1; this.c2 = c2; this.pos = pos; }
        @Override public int compareTo(Node o) {
            if (this.c1 != o.c1) return Integer.compare(this.c1, o.c1);
            return Integer.compare(this.c2, o.c2);
        }
    }

    /**
     * @param optimizeFuel false: tối thiểu số BƯỚC trước (dùng cho định tuyến patrol thường).
     *                     true : tối thiểu NHIÊN LIỆU trước (dùng khi tìm điểm hẹn refueler gần nhất).
     */
    public static DijkstraResult dijkstraFrom(HexGrid grid, int start, int fuelLimit, boolean optimizeFuel) {
        final int INF = 1_000_000_000;
        DijkstraResult res = new DijkstraResult();
        res.distSteps.put(start, 0);
        res.distFuel.put(start, 0);

        PriorityQueue<Node> pq = new PriorityQueue<>();
        pq.add(new Node(0, 0, start));

        while (!pq.isEmpty()) {
            Node curr = pq.poll();
            int pos = curr.pos;

            if (optimizeFuel) {
                if (curr.c1 > res.distFuel.getOrDefault(pos, INF)) continue;
                if (curr.c1 == res.distFuel.getOrDefault(pos, INF) && curr.c2 > res.distSteps.getOrDefault(pos, INF)) continue;
            } else {
                if (curr.c1 > res.distSteps.getOrDefault(pos, INF)) continue;
                if (curr.c1 == res.distSteps.getOrDefault(pos, INF) && curr.c2 > res.distFuel.getOrDefault(pos, INF)) continue;
            }

            int stepCost = grid.travelSteps(pos);
            int fuelCost = grid.fuelCost(pos);

            for (HexGrid.Neighbor nb : grid.neighbors(pos)) {
                int ns = (optimizeFuel ? curr.c2 : curr.c1) + stepCost;
                int nf = (optimizeFuel ? curr.c1 : curr.c2) + fuelCost;
                if (nf > fuelLimit) continue;

                boolean better = optimizeFuel
                        ? nf < res.distFuel.getOrDefault(nb.pos, INF) ||
                          (nf == res.distFuel.getOrDefault(nb.pos, INF) && ns < res.distSteps.getOrDefault(nb.pos, INF))
                        : ns < res.distSteps.getOrDefault(nb.pos, INF) ||
                          (ns == res.distSteps.getOrDefault(nb.pos, INF) && nf < res.distFuel.getOrDefault(nb.pos, INF));

                if (better) {
                    res.distSteps.put(nb.pos, ns);
                    res.distFuel.put(nb.pos, nf);
                    res.prev.put(nb.pos, new int[]{pos, nb.direction});
                    pq.add(optimizeFuel ? new Node(nf, ns, nb.pos) : new Node(ns, nf, nb.pos));
                }
            }
        }
        return res;
    }

    public static List<Integer> reconstructPath(Map<Integer, int[]> prev, int start, int goal) {
        List<Integer> path = new ArrayList<>();
        int cur = goal;
        while (cur != start) {
            int[] info = prev.get(cur);
            if (info == null) break;
            path.add(info[1]);
            cur = info[0];
        }
        Collections.reverse(path);
        return path;
    }
}
