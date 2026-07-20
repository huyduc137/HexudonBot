package org.example.engine;

import org.example.grid.HexGrid;
import org.example.grid.Pathfinder;
import org.json.JSONObject;

import java.util.*;

/**
 * AgentRoleAssigner — Thực thi PHASE 0: PRE-MATCH SETUP & ROLE ALLOCATION.
 *
 * Nâng cấp vượt bậc so với baseline:
 * 1. Phân tích tài nguyên: Đánh giá fuelLimits so với khoảng cách thực tế giữa các Spot.
 * 2. Xác định K (số Refueler): K = 0 nếu nhiên liệu dồi dào/ít xe; K = 1 nếu N từ 3-5; K = 2 nếu N >= 6 và bản đồ rộng.
 * 3. Phân cụm K-Means trên không gian Spot: Tìm ra K trọng tâm (centroids) của các khu vực bán Udon.
 * 4. Gán vai trò bằng SSSP (Dijkstra): Chọn Agent xuất phát có đường đi ngắn nhất đến các trọng tâm làm Refueler.
 */
public final class AgentRoleAssigner {

    private AgentRoleAssigner() {}

    /**
     * Hàm chính tự động quyết định vai trò cho từng xe trước khi trận đấu bắt đầu.
     */
    public static List<JSONObject> autoAssign(int nAgents, int fuelLimits, List<Integer> dayStepsList,
                                              List<Integer> agentsStarts, HexGrid grid, List<JSONObject> spots) {
        List<JSONObject> assignments = new ArrayList<>();
        if (nAgents <= 0) return assignments;

        int totalSteps = dayStepsList.stream().mapToInt(Integer::intValue).sum();
        int maxDaySteps = dayStepsList.stream().mapToInt(Integer::intValue).max().orElse(100);

        // BƯỚC 1: Phân tích nhu cầu tiếp nhiên liệu (Resource Analysis)
        // Trung bình xe tốn ~0.7 xăng/bước. Nếu bình xăng nhỏ hơn lượng tiêu thụ ước tính cả trận -> BẮT BUỘC có Refueler!
        int estimatedFuelNeeded = (int) (totalSteps * 0.65);
        boolean needRefuel = (nAgents >= 3) && (fuelLimits < estimatedFuelNeeded);

        // (TÙY CHỌN AN TOÀN): Nếu bạn muốn MAP NÀO CŨNG LUÔN CÓ ÍT NHẤT 1 REFUELER để tập luyện,
        // bạn có thể đổi dòng trên thành: boolean needRefuel = (nAgents >= 3);

        int kRefuelers = 0;
        if (needRefuel) {
            if (nAgents >= 6 && spots.size() >= 10 && grid.width * grid.height >= 256) {
                kRefuelers = 2; // Bản đồ rộng, nhiều xe, nhiều spot -> Chia 2 khu vực tiếp xăng
            } else {
                kRefuelers = 1; // Mặc định 1 xe tiếp xăng là đủ
            }
        }

        Set<Integer> refuelAgentIndices = new HashSet<>();

        // BƯỚC 2 & 3: Nếu cần Refueler, chạy K-Means trên tọa độ Spot để tìm tâm cụm
        if (kRefuelers > 0 && !spots.isEmpty() && !agentsStarts.isEmpty()) {
            List<int[]> spotCoords = new ArrayList<>();
            for (JSONObject spot : spots) {
                int pos = spot.getInt("pos");
                spotCoords.add(new int[]{pos / grid.width, pos % grid.width, pos});
            }

            // Chạy K-Means tìm ra kRefuelers ô trung tâm (pos)
            List<Integer> targetCentroids = runKMeansOnSpots(spotCoords, kRefuelers, grid);

            // BƯỚC 4: Tìm Agent gần các tâm cụm nhất bằng Dijkstra SSSP
            for (int centroidPos : targetCentroids) {
                int bestAgentIdx = -1;
                int minSteps = Integer.MAX_VALUE;

                for (int i = 0; i < agentsStarts.size(); i++) {
                    if (refuelAgentIndices.contains(i)) continue; // Xe này đã là refueler rồi

                    int startPos = agentsStarts.get(i);
                    // Dùng Pathfinder tính khoảng cách thật theo địa hình (không phải khoảng cách chim bay)
                    Pathfinder.DijkstraResult dr = Pathfinder.dijkstraFrom(grid, startPos, 9999, false);
                    int steps = dr.distSteps.getOrDefault(centroidPos, Integer.MAX_VALUE);

                    if (steps < minSteps) {
                        minSteps = steps;
                        bestAgentIdx = i;
                    }
                }

                if (bestAgentIdx != -1) {
                    refuelAgentIndices.add(bestAgentIdx);
                }
            }
        }

        // Đóng gói kết quả JSON trả về cho Client theo đúng định dạng Server yêu cầu
        for (int i = 0; i < nAgents; i++) {
            String aid = String.valueOf(i);
            String type = refuelAgentIndices.contains(i) ? "refuel" : "patrol";
            JSONObject obj = new JSONObject();
            obj.put("agent_id", aid);
            obj.put("type", type);
            assignments.add(obj);
        }

        return assignments;
    }

    /**
     * Thuật toán K-Means gom cụm Spot trên lưới lục giác để tìm ra K trọng tâm (Centroids).
     */
    private static List<Integer> runKMeansOnSpots(List<int[]> spotCoords, int k, HexGrid grid) {
        if (spotCoords.size() <= k) {
            List<Integer> res = new ArrayList<>();
            for (int[] s : spotCoords) res.add(s[2]);
            return res;
        }

        // Khởi tạo centroid ban đầu (chọn đều từ danh sách spot)
        double[][] centroids = new double[k][2];
        for (int i = 0; i < k; i++) {
            int idx = i * spotCoords.size() / k;
            centroids[i][0] = spotCoords.get(idx)[0]; // row
            centroids[i][1] = spotCoords.get(idx)[1]; // col
        }

        int[] clusterAssign = new int[spotCoords.size()];
        boolean changed = true;
        int maxIter = 15;

        while (changed && maxIter-- > 0) {
            changed = false;
            // E-step: Gán spot vào centroid gần nhất (Euclidean trên tọa độ 2D)
            for (int i = 0; i < spotCoords.size(); i++) {
                int bestC = 0;
                double minDist = Double.MAX_VALUE;
                for (int c = 0; c < k; c++) {
                    double dr = spotCoords.get(i)[0] - centroids[c][0];
                    double dc = spotCoords.get(i)[1] - centroids[c][1];
                    double dist = dr * dr + dc * dc;
                    if (dist < minDist) {
                        minDist = dist;
                        bestC = c;
                    }
                }
                if (clusterAssign[i] != bestC) {
                    clusterAssign[i] = bestC;
                    changed = true;
                }
            }

            // M-step: Cập nhật lại tọa độ centroid
            double[][] newSums = new double[k][2];
            int[] counts = new int[k];
            for (int i = 0; i < spotCoords.size(); i++) {
                int c = clusterAssign[i];
                newSums[c][0] += spotCoords.get(i)[0];
                newSums[c][1] += spotCoords.get(i)[1];
                counts[c]++;
            }
            for (int c = 0; c < k; c++) {
                if (counts[c] > 0) {
                    centroids[c][0] = newSums[c][0] / counts[c];
                    centroids[c][1] = newSums[c][1] / counts[c];
                }
            }
        }

        // Ánh xạ tọa độ thực tế (row, col) của centroid về ô hợp lệ (pos) gần nhất trên HexGrid
        List<Integer> centroidPosList = new ArrayList<>();
        for (int c = 0; c < k; c++) {
            int bestPos = 0;
            double minDist = Double.MAX_VALUE;
            for (int r = 0; r < grid.height; r++) {
                for (int col = 0; col < grid.width; col++) {
                    int pos = r * grid.width + col;
                    if (grid.cells.get(pos) == HexGrid.POND) continue; // Bỏ qua ao hồ

                    double dr = r - centroids[c][0];
                    double dc = col - centroids[c][1];
                    double dist = dr * dr + dc * dc;
                    if (dist < minDist) {
                        minDist = dist;
                        bestPos = pos;
                    }
                }
            }
            centroidPosList.add(bestPos);
        }

        return centroidPosList;
    }
}