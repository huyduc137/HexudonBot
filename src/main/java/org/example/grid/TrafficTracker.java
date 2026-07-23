package org.example.grid;

import java.util.*;

/**
 * TrafficTracker — PHASE 4: POST-DAY LEARNING & ADAPTATION
 *
 * Lưu trữ lịch sử các bước dừng lại (WAIT) của xe trên các ô Đường bộ (ROAD).
 * Dự đoán tình trạng tắc đường của ngày mai dựa trên công thức của Server:
 * Traffic = (Tổng bước dừng 2 ngày trước của mọi đội) / Số đội.
 */
public class TrafficTracker {

    // Lưu trữ số bước dừng lại của đội mình theo cấu trúc: Ngày -> (Ô (Pos) -> Số bước dừng)
    private final Map<Integer, Map<Integer, Integer>> stopStepsByDay = new HashMap<>();

    /**
     * Ghi nhận lịch sử di chuyển và chờ đợi vào cuối mỗi ngày sau khi đã chốt kế hoạch.
     */
    public void recordDay(int day, Map<String, List<Integer>> myActions, Map<String, Integer> startPosMap, HexGrid grid) {
        Map<Integer, Integer> stopsToday = new HashMap<>();

        for (Map.Entry<String, List<Integer>> entry : myActions.entrySet()) {
            int curPos = startPosMap.getOrDefault(entry.getKey(), 0);

            for (int act : entry.getValue()) {
                if (act < 0) { // Lệnh Wait (Số âm)
                    int waitSteps = -act;
                    // Chỉ tính tắc đường cho các ô là Đường Bộ (Road)
                    if (grid.cells.get(curPos) == HexGrid.ROAD) {
                        stopsToday.put(curPos, stopsToday.getOrDefault(curPos, 0) + waitSteps);
                    }
                } else if (act >= 0 && act <= 5) { // Lệnh di chuyển
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

    /**
     * Tự động dự đoán tình trạng giao thông ngày mai do CHÍNH BẢN THÂN mình gây ra.
     * Có thể dùng để nạp vào HexGrid trước khi lập kế hoạch dài hạn.
     */
    public Map<Integer, Integer> predictNextDaySelfTraffic(HexGrid grid, int nextDay, int numTeams,
                                                           int busyThreshold, int jammedThreshold) {
        Map<Integer, Integer> predictions = new HashMap<>();

        // Lấy lịch sử của 2 ngày trước đó
        Map<Integer, Integer> yesterday = stopStepsByDay.getOrDefault(nextDay - 1, new HashMap<>());
        Map<Integer, Integer> dayBefore = stopStepsByDay.getOrDefault(nextDay - 2, new HashMap<>());

        for (int pos = 0; pos < grid.cells.size(); pos++) {
            if (grid.cells.get(pos) == HexGrid.ROAD) {
                int totalStops = yesterday.getOrDefault(pos, 0) + dayBefore.getOrDefault(pos, 0);

                // Công thức theo chuẩn Procon: Tổng bước dừng / Số lượng đội
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