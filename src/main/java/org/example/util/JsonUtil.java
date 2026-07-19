package org.example.util;

/**
 * TODO — CHƯA IMPLEMENT. Hiện các đoạn code sau đang bị LẶP LẠI y hệt ở cả
 * HexudonEngine.generateDayPlan() và HexudonGUI.cmdFetchDay()/cmdPlanDay():
 *
 *   - Lấy teamData an toàn từ "teams" (thử cả key String lẫn key số):
 *       teams.optJSONObject(teamId) rồi fallback teams.optJSONObject(String.valueOf(teamId))
 *       rồi fallback new JSONObject()
 *   - Đọc "pos" agent: thử cả field "pos" lẫn field cũ "cell"
 *   - Convert JSONArray<JSONArray<Integer>> <-> List<List<Integer>>
 *
 * Việc cần làm: gom các hàm tiện ích tĩnh vào đây, ví dụ:
 *   public static JSONObject getTeamData(JSONObject state, String teamId)
 *   public static int getAgentPos(JSONObject agentJson)
 *   public static List<List<Integer>> toActionLists(JSONArray jsonActions)
 *   public static JSONArray fromActionLists(List<List<Integer>> actions)
 *
 * Lợi ích: nếu server đổi field "cell" -> "pos" hẳn (hoặc ngược lại), chỉ cần sửa 1 nơi
 * thay vì tìm lại từng chỗ dùng ag.has("pos") ? ... : ....
 */
public final class JsonUtil {
    private JsonUtil() {}
    // TODO: implement các hàm tĩnh mô tả ở trên.
}
