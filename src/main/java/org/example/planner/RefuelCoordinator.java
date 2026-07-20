package org.example.planner;

import org.example.grid.HexGrid;
import org.example.grid.Pathfinder;

import java.util.*;

/**
 * RefuelCoordinator — Thực thi khớp lệnh Hậu cần Không - Thời gian (PHASE 2).
 *
 * Nhiệm vụ: Khi một xe Tuần tra báo động sắp hết xăng tại ô X vào bước thứ T,
 * lớp này sẽ quét danh sách Xe Tiếp Xăng, tìm chiếc xe có thể đến ô X tại thời điểm <= T
 * với quãng đường di chuyển ngắn nhất, và lập lịch điểm hẹn thành công.
 */
public class RefuelCoordinator {

    public static class RendezvousPlan {
        public final String refuelAgentId;
        public final int meetPos;
        public final int meetStep;
        public final List<Integer> refuelPathDirs;
        public final int refuelStepsUsedAfter;

        public RendezvousPlan(String refuelAgentId, int meetPos, int meetStep,
                              List<Integer> refuelPathDirs, int refuelStepsUsedAfter) {
            this.refuelAgentId = refuelAgentId;
            this.meetPos = meetPos;
            this.meetStep = meetStep;
            this.refuelPathDirs = refuelPathDirs;
            this.refuelStepsUsedAfter = refuelStepsUsedAfter;
        }
    }

    /**
     * Tìm xe tiếp xăng khả thi để cứu hộ xe Tuần tra tại ô meetPos trước/tại bước meetStep.
     */
    public static RendezvousPlan findRendezvous(
            HexGrid grid,
            int meetPos,
            int meetStep,
            Map<String, Integer> refuelCurrentPos,
            Map<String, Integer> refuelStepsUsed,
            int daySteps) {

        String bestRefuelId = null;
        List<Integer> bestPath = null;
        int bestArrivalStep = Integer.MAX_VALUE;

        // Quét qua tất cả các xe Tiếp xăng mà đội đang sở hữu
        for (Map.Entry<String, Integer> entry : refuelCurrentPos.entrySet()) {
            String rId = entry.getKey();
            int rPos = entry.getValue();
            int rUsed = refuelStepsUsed.getOrDefault(rId, 0);

            // Nếu xe tiếp xăng đã tiêu tốn số bước vượt quá thời điểm cần gặp -> Bỏ qua
            if (rUsed >= meetStep) continue;

            // Tìm đường đi ngắn nhất từ vị trí hiện tại của xe tiếp xăng tới điểm hẹn
            Pathfinder.DijkstraResult dr = Pathfinder.dijkstraFrom(grid, rPos, 9999, false);
            if (!dr.distSteps.containsKey(meetPos)) continue;

            int travelSteps = dr.distSteps.get(meetPos);
            int arrivalStep = rUsed + travelSteps;

            // Điều kiện khớp lệnh: Xe tiếp xăng phải đến điểm hẹn <= thời điểm Patrol cần đổ xăng
            // VÀ tổng số bước di chuyển không được vượt quá quỹ bước trong ngày (daySteps)
            if (arrivalStep <= meetStep && arrivalStep <= daySteps) {
                if (arrivalStep < bestArrivalStep) {
                    bestArrivalStep = arrivalStep;
                    bestRefuelId = rId;
                    bestPath = Pathfinder.reconstructPath(dr.prev, rPos, meetPos);
                }
            }
        }

        if (bestRefuelId != null) {
            return new RendezvousPlan(bestRefuelId, meetPos, meetStep, bestPath, bestArrivalStep);
        }
        return null; // Không có xe tiếp xăng nào tới kịp -> Lộ trình tuần tra bị đánh giá không khả thi!
    }
}