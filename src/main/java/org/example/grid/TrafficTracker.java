package org.example.grid;

import java.util.*;

/**
 * TrafficTracker — PHASE 4: POST-DAY LEARNING & ADAPTATION
 *
 * ĐÃ NÂNG CẤP GIAI ĐOẠN 1 (CRITICAL BUG FIX):
 * Ghi nhận đầy đủ "stay steps" (bước lưu trú) theo đúng chuẩn Procon.
 * Khi xe di chuyển qua hoặc đứng chờ trên một ô Đường bộ (ROAD), toàn bộ số bước
 * tiêu tốn tại ô đó đều được ghi nhận vào traffic thực tế để dự đoán chính xác cho ngày sau.
 */
public class TrafficTracker {

    private final Map<Integer, Map<Integer, Integer>> stopStepsByDay = new HashMap<>();

    public void recordDay(int day, Map<String, List<Integer>> myActions, Map<String, Integer> startPosMap, HexGrid grid) {
        Map<Integer, Integer> stopsToday = new HashMap<>();

        for (Map.Entry<String, List<Integer>> entry : myActions.entrySet()) {
            int curPos = startPosMap.getOrDefault(entry.getKey(), 0);

            for (int act : entry.getValue()) {
                if (act < 0) { // Lệnh Wait (Số âm) -> Lưu trú tường minh
                    int waitSteps = -act;
                    if (grid.cells.get(curPos) == HexGrid.ROAD) {
                        stopsToday.put(curPos, stopsToday.getOrDefault(curPos, 0) + waitSteps);
                    }
                } else if (act >= 0 && act <= 5) { // Lệnh Move (Di chuyển)
                    // CRITICAL FIX: Lệnh di chuyển cũng chiếm dụng (lưu trú) tại ô curPos
                    // một khoảng thời gian đúng bằng travelSteps trước khi bước sang ô mới!
                    if (grid.cells.get(curPos) == HexGrid.ROAD) {
                        int staySteps = grid.travelSteps(curPos);
                        stopsToday.put(curPos, stopsToday.getOrDefault(curPos, 0) + staySteps);
                    }

                    // Cập nhật sang tọa độ tiếp theo
                    for (HexGrid.Neighbor nb : grid.neighbors(curPos)) {
                        if (nb.direction == act) {
                            curPos = nb.pos;
                            break;
                        }
                    }
                }
            }
        }
        stopStepsByDay.put(day, stopsToday);
    }

    public Map<Integer, Integer> predictNextDaySelfTraffic(HexGrid grid, int nextDay, int numTeams,
                                                           int busyThreshold, int jammedThreshold) {
        Map<Integer, Integer> predictions = new HashMap<>();
        Map<Integer, Integer> yesterday = stopStepsByDay.getOrDefault(nextDay - 1, new HashMap<>());
        Map<Integer, Integer> dayBefore = stopStepsByDay.getOrDefault(nextDay - 2, new HashMap<>());

        for (int pos = 0; pos < grid.cells.size(); pos++) {
            if (grid.cells.get(pos) == HexGrid.ROAD) {
                int totalStops = yesterday.getOrDefault(pos, 0) + dayBefore.getOrDefault(pos, 0);
                int trafficScore = totalStops / Math.max(1, numTeams);

                if (trafficScore >= jammedThreshold) {
                    predictions.put(pos, HexGrid.JAM);
                } else if (trafficScore >= busyThreshold) {
                    predictions.put(pos, HexGrid.CONGESTED);
                } else {
                    predictions.put(pos, HexGrid.SMOOTH);
                }
            }
        }
        return predictions;
    }
}