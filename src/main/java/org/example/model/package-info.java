/**
 * TODO — GÓI CÒN TRỐNG, CHƯA IMPLEMENT (rủi ro cao nếu làm vội, xem lý do bên dưới).
 *
 * Hiện toàn bộ code (grid, planner, engine, gui) đang truyền dữ liệu qua lại bằng
 * {@code org.json.JSONObject} thô (ví dụ 1 agent là JSONObject với field "agent_id","pos","fuel").
 * Cách này hoạt động ổn định nhưng có 3 nhược điểm khi dự án lớn dần:
 *   1. Không có kiểm tra kiểu lúc biên dịch — gõ nhầm "brand" thành "brnad" chỉ phát hiện lúc chạy.
 *   2. IDE không autocomplete được field.
 *   3. Cùng một khái niệm "agent" bị parse lặp lại 3 nơi khác nhau (Engine, GUI, DayPlanner)
 *      với logic fallback hơi khác nhau (has("pos") ? ... : getInt("cell")).
 *
 * Đề xuất các lớp POJO cần thêm khi refactor (KHÔNG làm gấp — nên làm sau khi
 * OrienteeringPlanner đã ổn định, để tránh vừa đổi thuật toán vừa đổi kiểu dữ liệu cùng lúc):
 *
 *   - Agent      { String agentId; int pos; int fuel; AgentType type; }
 *   - AgentType  enum { PATROL, REFUEL }
 *   - Spot       { int brand; int pos; int stocks; }
 *   - MapConfig  { int width, height; List<Integer> cells; List<Spot> spots;
 *                  List<Integer> daySteps; int fuelLimits; int players;
 *                  int busyThreshold; int jammedThreshold; }
 *   - DayState   { int day; long endsAt; List<Agent> myAgents;
 *                  Map<Integer,List<Agent>> otherTeamsAgents; Map<Integer,Integer> roadCond; }
 *
 * Mỗi lớp nên có factory tĩnh fromJson(JSONObject) để việc chuyển đổi vẫn tập trung 1 chỗ,
 * và {@link org.example.util.JsonUtil} có thể dùng các factory này thay vì parse tay.
 */
package org.example.model;
