package org.example.engine;

import org.example.grid.HexGrid;
import org.example.grid.Pathfinder;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentRoleAssigner — tách ra vì logic "tự động gán patrol/refuel" trước đây bị COPY-PASTE
 * y hệt giữa {@code HexudonEngine} (CLI) và {@code HexudonGUI} (nút "Tự động gán").
 * Giờ cả hai chỉ gọi {@link #autoAssign}.
 *
 * Quy tắc hiện tại (baseline, xem nhận xét trong hội thoại: tỷ lệ này CHƯA có cơ sở thực nghiệm):
 *   - Chỉ dùng refueler nếu nAgents >= 3 VÀ fuelLimits < 1.5 * tổng số bước cả trận.
 *   - Nếu dùng: chỉ 1 refueler duy nhất, chọn agent có vị trí xuất phát GẦN TÂM BẢN ĐỒ nhất.
 *
 * TODO (nâng cấp — xem PHASE 0 trong pipeline đã thảo luận):
 *   - Tỷ lệ patrol/refueler và SỐ LƯỢNG refueler (hiện hardcode = 1) nên được tính bằng
 *     mô phỏng offline theo (kích thước bản đồ, mật độ spot, fuelLimits, daySteps trung bình)
 *     thay vì hằng số 1.5 cố định.
 *   - Với nAgents >= 5-6 và bản đồ lớn, cân nhắc 2 refueler phụ trách 2 vùng riêng
 *     (k-means trên toạ độ các spot, không phải toạ độ agent).
 */
public final class AgentRoleAssigner {

    private AgentRoleAssigner() {}

    public static List<JSONObject> autoAssign(int nAgents, int fuelLimits, int totalSteps,
                                                List<Integer> agentsStarts, HexGrid grid) {
        boolean useRefuel = nAgents >= 3 && fuelLimits < (totalSteps * 1.5);
        String refuelAgentId = null;

        if (useRefuel && !agentsStarts.isEmpty()) {
            int centerPos = (grid.height / 2) * grid.width + (grid.width / 2);
            int bestDist = Integer.MAX_VALUE;
            for (int i = 0; i < agentsStarts.size(); i++) {
                Pathfinder.DijkstraResult dr = Pathfinder.dijkstraFrom(grid, agentsStarts.get(i), 9999, false);
                int dist = dr.distSteps.getOrDefault(centerPos, Integer.MAX_VALUE);
                if (dist < bestDist) {
                    bestDist = dist;
                    refuelAgentId = String.valueOf(i);
                }
            }
            if (refuelAgentId == null) refuelAgentId = "0";
        }

        List<JSONObject> assignments = new ArrayList<>();
        for (int i = 0; i < nAgents; i++) {
            String aid = String.valueOf(i);
            String type = (useRefuel && aid.equals(refuelAgentId)) ? "refuel" : "patrol";
            JSONObject obj = new JSONObject();
            obj.put("agent_id", aid);
            obj.put("type", type);
            assignments.add(obj);
        }
        return assignments;
    }
}
