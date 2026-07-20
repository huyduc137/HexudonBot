package org.example.grid;

import java.util.*;

/**
 * Pathfinder — Thuật toán tìm đường trên HexGrid.
 *
 * NÂNG CẤP PHASE 1: Thay thế PriorityQueue (Dijkstra O(E log V)) bằng DIAL'S ALGORITHM (Bucket Queue).
 * Vì trọng số bước đi chỉ thuộc tập rời rạc {1, 2, 3, 4} và xăng {1, 2}, chi phí quản lý hàng đợi
 * giảm xuống O(1) tuyệt đối, giúp chạy Multi-source SSSP cho hàng chục Agent chỉ trong tích tắc!
 */
public final class Pathfinder {

    private Pathfinder() {}

    public static class DijkstraResult {
        public final Map<Integer, Integer> distSteps = new HashMap<>();
        public final Map<Integer, Integer> distFuel = new HashMap<>();
        public final Map<Integer, int[]> prev = new HashMap<>(); // [parent, direction]
    }

    private static class Node {
        final int c1, c2, pos;
        Node(int c1, int c2, int pos) { this.c1 = c1; this.c2 = c2; this.pos = pos; }
    }

    /**
     * @param optimizeFuel false: tối thiểu số BƯỚC trước (dùng cho định tuyến tuần tra).
     *                     true : tối thiểu NHIÊN LIỆU trước (dùng khi tìm điểm hẹn Refueler).
     */
    @SuppressWarnings("unchecked")
    public static DijkstraResult dijkstraFrom(HexGrid grid, int start, int fuelLimit, boolean optimizeFuel) {
        final int INF = 1_000_000_000;
        DijkstraResult res = new DijkstraResult();
        res.distSteps.put(start, 0);
        res.distFuel.put(start, 0);

        // Nâng cấp Dial's Algorithm: Kích thước tối đa của map là 32x32 = 1024 ô.
        // Chi phí đường đi lớn nhất không vượt quá 1024 * 4 = 4096. Chọn MAX_COST = 6000 là an toàn tuyệt đối.
        int maxCostLimit = 6000;
        List<Node>[] buckets = new ArrayList[maxCostLimit];

        buckets[0] = new ArrayList<>();
        buckets[0].add(new Node(0, 0, start));

        int currentBucket = 0;
        int totalNodesInQueue = 1;

        while (totalNodesInQueue > 0) {
            // Lướt tới bucket tiếp theo có chứa node
            while (currentBucket < maxCostLimit && (buckets[currentBucket] == null || buckets[currentBucket].isEmpty())) {
                currentBucket++;
            }
            if (currentBucket >= maxCostLimit) break; // Đã duyệt hết các đường đi khả thi

            // Lấy ra node tốt nhất trong bucket (tie-break theo tiêu chí phụ c2)
            List<Node> bucket = buckets[currentBucket];
            int bestIdx = 0;
            if (bucket.size() > 1) {
                for (int i = 1; i < bucket.size(); i++) {
                    if (bucket.get(i).c2 < bucket.get(bestIdx).c2) {
                        bestIdx = i;
                    }
                }
            }
            Node curr = bucket.remove(bestIdx);
            totalNodesInQueue--;

            int pos = curr.pos;

            // Kiểm tra node lỗi thời (lazy deletion)
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
                if (nf > fuelLimit) continue; // Ràng buộc cứng: Không được vượt quá giới hạn nhiên liệu

                boolean better = optimizeFuel
                        ? nf < res.distFuel.getOrDefault(nb.pos, INF) ||
                        (nf == res.distFuel.getOrDefault(nb.pos, INF) && ns < res.distSteps.getOrDefault(nb.pos, INF))
                        : ns < res.distSteps.getOrDefault(nb.pos, INF) ||
                        (ns == res.distSteps.getOrDefault(nb.pos, INF) && nf < res.distFuel.getOrDefault(nb.pos, INF));

                if (better) {
                    res.distSteps.put(nb.pos, ns);
                    res.distFuel.put(nb.pos, nf);
                    res.prev.put(nb.pos, new int[]{pos, nb.direction});

                    int newCost = optimizeFuel ? nf : ns;
                    if (newCost < maxCostLimit) {
                        if (buckets[newCost] == null) buckets[newCost] = new ArrayList<>();
                        buckets[newCost].add(optimizeFuel ? new Node(nf, ns, nb.pos) : new Node(ns, nf, nb.pos));
                        totalNodesInQueue++;
                    }
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