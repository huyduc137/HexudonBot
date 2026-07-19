package org.example.planner;

import org.example.grid.HexGrid;
import org.example.grid.Pathfinder;
import org.json.JSONObject;

import java.util.*;

/**
 * LazyGreedyPlanner — chiến lược BASELINE hiện tại (đã chạy ổn định):
 *   - Ngày 1: mỗi patrol đi thẳng tới điểm đến CHƯA được gán và GẦN NHẤT theo số bước
 *     (Dijkstra ưu tiên bước), mỗi điểm chỉ gán cho tối đa 1 patrol trong ngày.
 *   - Từ ngày 2 trở đi: mọi agent đứng yên (chờ hết ngày) — đây là điểm CẦN THAY THẾ
 *     bằng {@link OrienteeringPlanner} để không lãng phí toàn bộ phần còn lại của trận.
 *   - Refueler: luôn đứng yên (chưa có phối hợp tiếp nhiên liệu chủ động).
 *
 * Giữ lại làm PHƯƠNG ÁN DỰ PHÒNG AN TOÀN (rollback target) khi chiến lược nâng cao
 * gặp lỗi hoặc không kịp thời gian phản hồi — xem HexudonEngine / GUI phần sandbox validate.
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

        if (currentDay > 0) {
            List<JSONObject> allAgents = new ArrayList<>(patrolAgents);
            allAgents.addAll(refuelAgents);
            for (JSONObject ag : allAgents) {
                allActions.put(ag.getString("agent_id"), Collections.singletonList(-daySteps));
            }
            return allActions;
        }

        Map<Integer, JSONObject> spotMap = new HashMap<>();
        for (JSONObject s : spots) spotMap.put(s.getInt("pos"), s);
        Set<Integer> assignedSpots = new HashSet<>();

        for (JSONObject ag : patrolAgents) {
            String aid = ag.getString("agent_id");
            int pos = ag.getInt("pos");
            int fuel = ag.getInt("fuel");

            Pathfinder.DijkstraResult dr = Pathfinder.dijkstraFrom(grid, pos, fuel, false);

            Integer bestSpot = null;
            int bestDist = Integer.MAX_VALUE;
            for (Integer spos : spotMap.keySet()) {
                if (assignedSpots.contains(spos)) continue;
                if (dr.distSteps.containsKey(spos) && dr.distSteps.get(spos) < bestDist) {
                    bestDist = dr.distSteps.get(spos);
                    bestSpot = spos;
                }
            }

            List<Integer> actions = new ArrayList<>();
            int remainingSteps = daySteps;

            if (bestSpot != null) {
                assignedSpots.add(bestSpot);
                List<Integer> pathDirs = Pathfinder.reconstructPath(dr.prev, pos, bestSpot);

                int curPos = pos;
                for (int d : pathDirs) {
                    int stepCost = grid.travelSteps(curPos);
                    if (remainingSteps < stepCost) break;
                    actions.add(d);
                    remainingSteps -= stepCost;
                    for (HexGrid.Neighbor nb : grid.neighbors(curPos)) {
                        if (nb.direction == d) { curPos = nb.pos; break; }
                    }
                }
            }

            if (remainingSteps > 0) actions.add(-remainingSteps);
            allActions.put(aid, actions);
        }

        for (JSONObject ag : refuelAgents) {
            allActions.put(ag.getString("agent_id"), Collections.singletonList(-daySteps));
        }

        return allActions;
    }
}
