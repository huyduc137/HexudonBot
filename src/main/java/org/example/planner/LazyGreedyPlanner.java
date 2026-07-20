package org.example.planner;

import org.example.grid.HexGrid;
import org.example.grid.Pathfinder;
import org.example.util.JsonUtil;
import org.json.JSONObject;

import java.util.*;

/**
 * LazyGreedyPlanner — Chiến lược An toàn / Fallback (ĐÃ NÂNG CẤP KHẮC PHỤC LỖI ĐẦU HÀNG).
 *
 * Nâng cấp vượt bậc so với baseline cũ:
 * 1. XOÁ BỎ LỆNH ĐẦU HÀNG: Không còn khối `if (currentDay > 0) wait;`. Bot chạy liên tục từ Ngày 0 đến Ngày cuối cùng!
 * 2. TÍCH HỢP JSON UTIL: Dùng JsonUtil để lấy tọa độ an toàn, không lo sai lệch schema.
 * 3. HÀNH VI TUẦN TRA (Patrol): Mỗi ngày, mỗi xe Tuần tra tự tìm quán Udon GẦN NHẤT còn hàng (stocks > 0)
 *    và chưa có ai trong đội đăng ký để lao tới. Đi hết số bước hoặc hết xăng thì dừng lại chờ.
 * 4. HÀNH VI TIẾP XĂNG (Refueler): Ở chế độ Greedy đơn giản, xe tiếp xăng đứng yên làm trạm cố định tại vùng
 *    trung tâm (nơi đã được K-Means đưa tới từ trước trận), chờ các xe Tuần tra ghé qua.
 */
public class LazyGreedyPlanner implements DayPlanner {

    @Override
    public Map<String, List<Integer>> plan(
            HexGrid grid,
            List<JSONObject> patrolAgents,
            List<JSONObject> refuelAgents,
            List<JSONObject> spots,
            int daySteps,
            Set<Integer> brandsSeen,
            int currentDay,
            String strategy) {

        Map<String, List<Integer>> allActions = new HashMap<>();

        // BƯỚC 1: Lập bản đồ các Quán Udon khả thi (Còn hàng và không nằm trong ao)
        Map<Integer, JSONObject> availableSpots = new HashMap<>();
        for (JSONObject s : spots) {
            int stocks = s.optInt("stocks", 1);
            if (stocks > 0) { // Chỉ quan tâm quán còn Udon
                availableSpots.put(s.getInt("pos"), s);
            }
        }

        // Tập hợp các điểm đến đã được phân công cho xe khác trong ngày hôm nay (tránh 2 xe cùng tranh 1 quán)
        Set<Integer> assignedSpotsToday = new HashSet<>();

        // BƯỚC 2: Lập kế hoạch cho từng Xe Tuần Tra (Patrol)
        for (JSONObject ag : patrolAgents) {
            String aid = ag.getString("agent_id");
            // Dùng JsonUtil từ Phase 0 để lấy tọa độ chuẩn
            int pos = JsonUtil.getAgentPos(ag);
            int fuel = ag.optInt("fuel", 0);

            // Chạy Dijkstra tìm đường ngắn nhất (theo bước đi thực tế đã cập nhật tắc đường ở Phase 1)
            Pathfinder.DijkstraResult dr = Pathfinder.dijkstraFrom(grid, pos, fuel, false);

            Integer bestSpotPos = null;
            int bestDist = Integer.MAX_VALUE;

            // Tìm quán Udon gần nhất chưa ai đăng ký
            for (Integer spos : availableSpots.keySet()) {
                if (assignedSpotsToday.contains(spos)) continue;
                if (dr.distSteps.containsKey(spos) && dr.distSteps.get(spos) < bestDist) {
                    bestDist = dr.distSteps.get(spos);
                    bestSpotPos = spos;
                }
            }

            List<Integer> actions = new ArrayList<>();
            int remainingSteps = daySteps;
            int currentPos = pos;
            int currentFuel = fuel;

            // Nếu tìm thấy điểm đến khả thi trong tầm xăng -> Từng bước tiến tới
            if (bestSpotPos != null) {
                assignedSpotsToday.add(bestSpotPos);
                List<Integer> pathDirs = Pathfinder.reconstructPath(dr.prev, pos, bestSpotPos);

                for (int d : pathDirs) {
                    int stepCost = grid.travelSteps(currentPos);
                    int fuelCost = grid.fuelCost(currentPos);

                    // Kiểm tra ràng buộc cứng: Đủ bước đi VÀ Đủ nhiên liệu
                    if (remainingSteps < stepCost || currentFuel < fuelCost) {
                        break; // Hết bước hoặc hết xăng giữa đường -> Dừng lại
                    }

                    actions.add(d);
                    remainingSteps -= stepCost;
                    currentFuel -= fuelCost;

                    // Cập nhật tọa độ hiện tại theo hướng di chuyển d
                    for (HexGrid.Neighbor nb : grid.neighbors(currentPos)) {
                        if (nb.direction == d) {
                            currentPos = nb.pos;
                            break;
                        }
                    }
                }
            }

            // BƯỚC CHUẨN HÓA STEP BUDGET: Nếu không di chuyển nữa, bắt buộc CHỜ (-remainingSteps) cho khớp ngày
            if (remainingSteps > 0) {
                actions.add(-remainingSteps);
            }

            allActions.put(aid, actions);
        }

        // BƯỚC 3: Lập kế hoạch cho Xe Tiếp Xăng (Refueler)
        // Trong chiến lược Greedy fallback, Refueler đứng yên tại điểm chiến lược để làm trạm cố định
        for (JSONObject ag : refuelAgents) {
            String aid = ag.getString("agent_id");
            allActions.put(aid, Collections.singletonList(-daySteps));
        }

        return allActions;
    }
}