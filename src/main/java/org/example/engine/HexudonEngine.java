package org.example.engine;

import org.example.client.HexudonClient;
import org.example.grid.HexGrid;
import org.example.planner.DayPlanner;
import org.example.planner.LazyGreedyPlanner;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * HexudonEngine — điều phối vòng lặp CLI (không có GUI): kết nối, chọn vai trò,
 * lặp qua từng ngày, gọi planner, gửi hành động.
 *
 * Không chứa logic thuật toán (đã ở grid/planner) hay logic HTTP (đã ở client)
 * — chỉ còn ORCHESTRATION, đúng vai trò 1 "engine".
 *
 * TODO:
 *   - Đổi `new LazyGreedyPlanner()` thành `new OrienteeringPlanner()` khi lớp đó
 *     đã implement xong (xem org.example.planner.OrienteeringPlanner).
 *   - Dùng client.getDayInfo() để lấy "endsAt" thật thay vì tự đoán qua polling getState().
 *   - Thêm sandbox validate (xem docs/architecture.md — PHASE 3) trước khi submitActions,
 *     để tự rollback về LazyGreedyPlanner nếu OrienteeringPlanner sinh lộ trình không hợp lệ.
 */
public class HexudonEngine {
    private final HexudonClient client;
    private final boolean practice;
    private final DayPlanner planner;

    public HexudonEngine(HexudonClient client, boolean practice) {
        this(client, practice, new LazyGreedyPlanner());
    }

    public HexudonEngine(HexudonClient client, boolean practice, DayPlanner planner) {
        this.client = client;
        this.practice = practice;
        this.planner = planner;
    }

    public void run() {
        System.out.println("--- Khởi động Hexudon Java Engine ---");
        JSONObject mapResp = client.getMap();
        HexGrid grid = HexGrid.fromMapResponse(mapResp);

        List<JSONObject> spots = new ArrayList<>();
        JSONArray spotsArr = mapResp.optJSONArray("spots");
        if (spotsArr != null) for (int i = 0; i < spotsArr.length(); i++) spots.add(spotsArr.getJSONObject(i));

        List<Integer> dayStepsList = new ArrayList<>();
        JSONArray stepsArr = mapResp.optJSONArray("daySteps");
        if (stepsArr != null) for (int i = 0; i < stepsArr.length(); i++) dayStepsList.add(stepsArr.getInt(i));

        int fuelLimits = mapResp.optInt("fuelLimits", 100);
        JSONArray agentsArr = mapResp.optJSONArray("agents");
        int nAgents = (agentsArr != null) ? agentsArr.length() : 0;

        List<Integer> agentsStarts = new ArrayList<>();
        if (agentsArr != null) for (int i = 0; i < agentsArr.length(); i++) agentsStarts.add(agentsArr.getInt(i));

        int totalSteps = dayStepsList.stream().mapToInt(Integer::intValue).sum();
        List<JSONObject> assignments = AgentRoleAssigner.autoAssign(
                nAgents, fuelLimits, dayStepsList, agentsStarts, grid, spots
        );

        System.out.println("Đang gửi cấu hình loại Agent...");
        try {
            client.selectAgentTypes(assignments);
            System.out.println("-> Cấu hình loại Agent thành công!");
        } catch (Exception e) {
            System.out.println("-> Cảnh báo: Không thể chọn loại xe (Có thể trận đấu đã bắt đầu hoặc đã bị khóa). Tiếp tục chơi...");
        }

        Map<String, String> typeMap = new HashMap<>();
        for (JSONObject a : assignments) typeMap.put(a.getString("agent_id"), a.getString("type"));

        for (int day = 0; day < dayStepsList.size(); day++) {
            System.out.println("\n=== Bắt đầu Ngày " + day + " ===");
            if (!practice) waitForDay(day);

            JSONObject state = client.getState();
            if ("finished".equals(state.optString("status"))) {
                System.out.println("Trận đấu đã kết thúc sớm!");
                break;
            }

            int daySteps = dayStepsList.get(day);
            List<List<Integer>> actions = generateDayPlan(day, state, grid, spots, daySteps, nAgents, typeMap);

            System.out.println("Đang gửi hành động cho ngày " + day + "...");
            JSONObject res = practice ? client.submitPractice(day, actions) : client.submitActions(day, actions);
            System.out.println("Kết quả: " + res.toString());
        }
        System.out.println("\n--- Trận đấu hoàn tất! ---");
    }

    private List<List<Integer>> generateDayPlan(int day, JSONObject state, HexGrid grid, List<JSONObject> spots,
                                                 int daySteps, int nAgents, Map<String, String> typeMap) {
        JSONObject teams = state.getJSONObject("teams");
        JSONObject teamData = teams.optJSONObject(client.teamId);
        if (teamData == null) teamData = teams.optJSONObject(String.valueOf(client.teamId));
        if (teamData == null) teamData = new JSONObject();

        JSONArray rawAgents = teamData.optJSONArray("agents");
        List<JSONObject> patrolAgents = new ArrayList<>();
        List<JSONObject> refuelAgents = new ArrayList<>();

        if (rawAgents != null) {
            for (int i = 0; i < rawAgents.length(); i++) {
                JSONObject ag = rawAgents.getJSONObject(i);
                String aid = ag.has("agent_id") ? String.valueOf(ag.get("agent_id")) : String.valueOf(i);
                int pos = ag.has("pos") ? ag.getInt("pos") : ag.getInt("cell");
                int fuel = ag.optInt("fuel", 0);

                boolean isPatrol = "patrol".equals(typeMap.getOrDefault(aid, "patrol"));
                JSONObject entry = new JSONObject();
                entry.put("agent_id", aid);
                entry.put("pos", pos);
                entry.put("fuel", fuel);

                (isPatrol ? patrolAgents : refuelAgents).add(entry);
            }
        }

        Set<Integer> brandsSeen = new HashSet<>();
        JSONArray brandsArr = teamData.optJSONArray("distinct_types");
        if (brandsArr != null) for (int i = 0; i < brandsArr.length(); i++) brandsSeen.add(brandsArr.getInt(i));

        int currentDay = state.optInt("day", 0);
        Map<String, List<Integer>> planned =
                planner.plan(grid, patrolAgents, refuelAgents, spots, daySteps, brandsSeen, currentDay, "lazy");

        List<List<Integer>> actions = new ArrayList<>();
        for (int i = 0; i < nAgents; i++) {
            String aid = String.valueOf(i);
            actions.add(planned.getOrDefault(aid, Collections.singletonList(-daySteps)));
        }
        return actions;
    }

    private void waitForDay(int expectedDay) {
        System.out.print("Đang chờ lượt thi đấu ngày " + expectedDay + "...");
        long deadline = System.currentTimeMillis() + 300_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                JSONObject state = client.getState();
                String status = state.optString("status", "");
                int current = state.optInt("day", -1);
                if ("finished".equals(status) || ("in_progress".equals(status) && current == expectedDay)) {
                    System.out.println(" Đã đến lượt!");
                    return;
                }
            } catch (Exception e) {
                System.out.println(" Lỗi khi kiểm tra lượt: " + e.getMessage());
            }
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
        }
        System.out.println("\nCảnh báo: Hết thời gian chờ ngày " + expectedDay);
    }
}
