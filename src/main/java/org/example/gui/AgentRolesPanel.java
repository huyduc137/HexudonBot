package org.example.gui;

/**
 * TODO — CHƯA TÁCH. Hiện "GROUP 2: Chỉ định vai trò Agent" (danh sách ComboBox patrol/refuel
 * theo từng agent, nút "Tự động gán", nút "Chốt & Gửi") vẫn nằm trong constructor của
 * {@link HexudonGUI} (~30 dòng) + 2 field agentsContainer/agentRoleCombos.
 *
 * Việc cần làm khi tách:
 *   1. buildAgentRolesUI(int nAgents) -> dựng lại danh sách ComboBox khi số agent thay đổi
 *      (đã có sẵn logic, chỉ cần move nguyên sang đây).
 *   2. getSelectedAssignments() -> trả về List<JSONObject> {agent_id, type} để HexudonGUI
 *      gửi lên server, KHÔNG để HexudonGUI tự đọc từng JComboBox.
 *   3. Khi OrienteeringPlanner hỗ trợ gợi ý số refueler > 1 (xem AgentRoleAssigner TODO),
 *      panel này cần hiển thị được nhóm/khu vực mà mỗi refueler phụ trách.
 */
public class AgentRolesPanel {
    // TODO: implement — xem mô tả phía trên.
}
