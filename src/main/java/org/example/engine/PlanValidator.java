package org.example.engine;

import org.example.grid.HexGrid;

import java.util.List;
import java.util.Map;

/**
 * TODO — CHƯA IMPLEMENT (PHASE 3 trong docs/architecture.md).
 *
 * HIỆN TẠI: HexudonEngine/HexudonGUI gửi thẳng kết quả của planner lên server mà KHÔNG
 * tự kiểm tra lại. Nếu planner (đặc biệt OrienteeringPlanner sau này) sinh lỗi, TOÀN BỘ
 * answer của ngày đó bị server từ chối cho MỌI agent (theo luật: "If the action plan of
 * even one agent is invalid, the action plans of all agents are treated as invalid") —
 * nghĩa là mất trắng 1 ngày thi đấu thay vì chỉ lỗi cục bộ.
 *
 * Việc cần làm — mô phỏng lại CHÍNH XÁC những gì server sẽ làm, trước khi gọi submitActions:
 *   1. Với mỗi agent: replay từng lệnh trong danh sách actions.
 *        - Lệnh <= -1: chờ N bước, N phải <= số bước còn lại trong ngày của agent đó.
 *        - Lệnh 0..5: phải trỏ tới ô LIỀN KỀ hợp lệ (không phải Pond, không ngoài biên)
 *          — dùng HexGrid.neighbors() để kiểm tra, KHÔNG tự suy luận toạ độ tay.
 *        - Patrol: trừ fuel theo HexGrid.fuelCost(pos) tại ô XUẤT PHÁT của bước di chuyển;
 *          nếu fuel dự kiến < 0 tại bất kỳ bước nào -> KHÔNG hợp lệ (patrol phải "wait" tới
 *          khi có refueler, chứ không được tự ý di chuyển tiếp khi thiếu fuel).
 *        - Refueler: không giới hạn fuel, nhưng vẫn phải đủ SỐ BƯỚC trong ngày.
 *   2. Tổng số bước tiêu thụ (di chuyển + chờ) của MỖI agent phải bằng CHÍNH XÁC daySteps.
 *   3. Nếu bất kỳ agent nào không hợp lệ -> trả về kết quả invalid kèm lý do cụ thể (agent nào,
 *      bước nào, lỗi gì), để HexudonEngine có thể fallback: dùng lại lộ trình từ
 *      LazyGreedyPlanner cho NGÀY ĐÓ thay vì gửi lộ trình lỗi lên server.
 *
 * Gợi ý API:
 *   public static class ValidationResult { boolean valid; String reason; }
 *   public static ValidationResult validate(HexGrid grid, Map<String, List<Integer>> actions,
 *                                            Map<String, Integer> agentStartPos,
 *                                            Map<String, Integer> agentFuel,
 *                                            Set<String> patrolAgentIds, int daySteps)
 */
public final class PlanValidator {
    private PlanValidator() {}
    // TODO: implement — xem mô tả phía trên.
}
