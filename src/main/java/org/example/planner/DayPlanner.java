package org.example.planner;

import org.example.grid.HexGrid;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hợp đồng chung cho mọi chiến lược lập kế hoạch trong ngày.
 * Tách ra interface để có thể đổi chiến lược (Strategy Pattern) mà không sửa
 * {@code HexudonEngine} / {@code HexudonGUI} — chỉ cần đổi implementation được inject vào.
 *
 * Cài đặt hiện có:
 *   - {@link LazyGreedyPlanner}   : baseline đơn giản (patrol gần nhất, đứng yên từ ngày 2).
 *
 * Cài đặt còn thiếu (xem TODO riêng từng file):
 *   - {@link OrienteeringPlanner} : chiến lược chính cho thi đấu thật (multi-day, coverage chain, SA).
 *   - {@link RefuelCoordinator}   : dùng nội bộ bởi OrienteeringPlanner để lên lịch tiếp nhiên liệu.
 */
public interface DayPlanner {

    /**
     * @param grid          bản đồ + trạng thái đường đã cập nhật cho ngày hiện tại
     * @param patrolAgents  danh sách JSONObject {agent_id, pos, fuel} của xe tuần tra đội mình
     * @param refuelAgents  danh sách JSONObject {agent_id, pos, fuel} của xe tiếp nhiên liệu đội mình
     * @param spots         toàn bộ điểm đến trên bản đồ {brand, pos, stocks}
     * @param daySteps      ngân sách số bước cho ngày này
     * @param brandsSeen    tập các "chain" đã từng thu thập trong TOÀN trận (ưu tiên #1 của luật thắng)
     * @param currentDay    chỉ số ngày hiện tại (0-indexed theo response server)
     * @param strategy      tên chiến lược phụ (để 1 planner có thể có nhiều biến thể nội bộ)
     * @return map agent_id -> danh sách lệnh nguyên thủy (0..5 = hướng di chuyển, số âm = chờ N bước)
     */
    Map<String, List<Integer>> plan(
            HexGrid grid,
            List<JSONObject> patrolAgents,
            List<JSONObject> refuelAgents,
            List<JSONObject> spots,
            int daySteps,
            Set<Integer> brandsSeen,
            int currentDay,
            String strategy);
}
