package org.example.engine;

import org.example.grid.HexGrid;
import java.util.*;

/**
 * PlanValidator — PHASE 3: Màng lọc Sandbox & Chuẩn hóa Lộ trình.
 *
 * Đảm bảo 100% lệnh gửi lên Server là hợp lệ:
 * 1. Khớp chính xác daySteps (Hụt thì bù WAIT, dư thì cắt).
 * 2. Đảm bảo không đâm vào Ao (Pond) hoặc đi ra ngoài biên bản đồ.
 */
public final class PlanValidator {

    private PlanValidator() {}

    public static class ValidationResult {
        public final boolean valid;
        public final String reason;
        public final Map<String, List<Integer>> normalizedActions;

        public ValidationResult(boolean valid, String reason, Map<String, List<Integer>> normalizedActions) {
            this.valid = valid;
            this.reason = reason;
            this.normalizedActions = normalizedActions;
        }
    }

    /**
     * Chạy Sandbox mô phỏng để kiểm tra và chuẩn hóa Step Budget cho mọi Agent.
     */
    public static ValidationResult validateAndNormalize(
            HexGrid grid,
            Map<String, List<Integer>> actions,
            Map<String, Integer> agentStartPos,
            int daySteps) {

        Map<String, List<Integer>> safeActions = new HashMap<>();

        for (Map.Entry<String, List<Integer>> entry : actions.entrySet()) {
            String aid = entry.getKey();
            List<Integer> rawActions = entry.getValue();
            List<Integer> cleanActions = new ArrayList<>();

            int currentPos = agentStartPos.getOrDefault(aid, 0);
            int totalStepsUsed = 0;

            for (int act : rawActions) {
                if (totalStepsUsed >= daySteps) {
                    break; // Bỏ qua các lệnh dư thừa nếu AI lỡ sinh quá tay
                }

                if (act < 0) { // Lệnh Wait (Chờ đợi)
                    int waitTime = -act;
                    if (totalStepsUsed + waitTime > daySteps) {
                        waitTime = daySteps - totalStepsUsed; // Cắt bớt thời gian chờ cho vừa đủ ngày
                    }
                    cleanActions.add(-waitTime);
                    totalStepsUsed += waitTime;

                } else if (act >= 0 && act <= 5) { // Lệnh Move (Di chuyển)
                    // Kiểm tra xem hướng đi này có hợp lệ không (Có ra ngoài biên hay vào Ao không?)
                    HexGrid.Neighbor nextNode = null;
                    for (HexGrid.Neighbor nb : grid.neighbors(currentPos)) {
                        if (nb.direction == act) {
                            nextNode = nb;
                            break;
                        }
                    }

                    if (nextNode == null) {
                        return new ValidationResult(false,
                                "Agent " + aid + " cố đâm vào Ao (Pond) hoặc ngoài biên tại ô " + currentPos + " (Hướng " + act + ")", null);
                    }

                    int stepCost = grid.travelSteps(currentPos);
                    if (totalStepsUsed + stepCost > daySteps) {
                        // Không đủ thời gian để thực hiện bước đi này -> Hủy bước này và chuyển thành Wait
                        break;
                    }

                    cleanActions.add(act);
                    totalStepsUsed += stepCost;
                    currentPos = nextNode.pos; // Cập nhật vị trí
                } else {
                    return new ValidationResult(false, "Agent " + aid + " có mã lệnh không hợp lệ: " + act, null);
                }
            }

            // =====================================================================
            // BƯỚC CHUẨN HÓA THẦN THÁNH: Bù lệnh WAIT nếu AI sinh hụt bước!
            // =====================================================================
            if (totalStepsUsed < daySteps) {
                cleanActions.add(-(daySteps - totalStepsUsed));
            }

            safeActions.put(aid, cleanActions);
        }

        return new ValidationResult(true, "OK", safeActions);
    }
}