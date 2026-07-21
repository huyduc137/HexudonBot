package org.example.engine;

import org.example.client.HexudonClient;
import org.example.grid.HexGrid;
import org.example.planner.DayPlanner;
import org.example.planner.LazyGreedyPlanner;
import org.example.planner.OrienteeringPlanner;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * HexudonEngine — Điều phối luồng thi đấu tự động (CLI).
 * NÂNG CẤP PHASE 1: Nhận diện mốc thời gian "endsAt" từ Server, tính toán ngân sách thời gian
 * suy nghĩ động, tự động cập nhật tắc đường vào HexGrid và chống timeout bằng fallback.
 */
public class HexudonEngine {
    private final HexudonClient client;
    private final boolean practice;
    private final DayPlanner planner;
    private int fuelLimits = 100;

    public HexudonEngine(HexudonClient client, boolean practice) {
        this(client, practice, new OrienteeringPlanner()); // CẬP NHẬT PHASE 2: Kích hoạt AI Luyện kim!
    }

    public HexudonEngine(HexudonClient client, boolean practice, DayPlanner planner) {
        this.client = client;
        this.practice = practice;
        this.planner = planner;
    }

    public void run() {
        System.out.println("--- Khởi động Hexudon Java Engine (Procon 2026) ---");
        JSONObject mapResp = client.getMap();
        HexGrid grid = HexGrid.fromMapResponse(mapResp);

        List<JSONObject> spots = new ArrayList<>();
        JSONArray spotsArr = mapResp.optJSONArray("spots");
        if (spotsArr != null) for (int i = 0; i < spotsArr.length(); i++) spots.add(spotsArr.getJSONObject(i));

        List<Integer> dayStepsList = new ArrayList<>();
        JSONArray stepsArr = mapResp.optJSONArray("daySteps");
        if (stepsArr != null) for (int i = 0; i < stepsArr.length(); i++) dayStepsList.add(stepsArr.getInt(i));

        fuelLimits = mapResp.optInt("fuelLimits", 100);
        JSONArray agentsArr = mapResp.optJSONArray("agents");
        int nAgents = (agentsArr != null) ? agentsArr.length() : 0;

        List<Integer> agentsStarts = new ArrayList<>();
        if (agentsArr != null) for (int i = 0; i < agentsArr.length(); i++) agentsStarts.add(agentsArr.getInt(i));

        // PHASE 0: Phân giao vai trò K-Means (Đã nâng cấp ở turn trước)
        List<JSONObject> assignments = AgentRoleAssigner.autoAssign(nAgents, fuelLimits, dayStepsList, agentsStarts, grid, spots);

        System.out.println("Đang gửi cấu hình loại Agent...");
        try {
            client.selectAgentTypes(assignments);
            System.out.println("-> Cấu hình loại Agent thành công!");
        } catch (Exception e) {
            System.out.println("-> Cảnh báo: Không thể chọn loại xe (Trận đấu đã bắt đầu hoặc bị khóa). Tiếp tục chơi...");
        }

        Map<String, String> typeMap = new HashMap<>();
        for (JSONObject a : assignments) typeMap.put(a.getString("agent_id"), a.getString("type"));

        for (int day = 0; day < dayStepsList.size(); day++) {
            System.out.println("\n=== Bắt đầu Ngày " + day + " ===");
            if (!practice) waitForDay(day);

            // NÂNG CẤP PHASE 1: Lấy Day Info thật từ Server để đọc "endsAt" và "traffics"
            JSONObject dayInfo = client.getDayInfo();
            JSONObject state = client.getState();
            if ("finished".equals(state.optString("status"))) {
                System.out.println("Trận đấu đã kết thúc sớm!");
                break;
            }

            // 1. Cập nhật trạng thái giao thông thực tế (SMOOTH/CONGESTED/JAM) vào HexGrid
            JSONArray traffics = dayInfo.optJSONArray("traffics");
            if (traffics == null) traffics = state.optJSONArray("traffics");
            grid.updateTraffic(traffics);

            // 2. Tính toán Ngân sách Thời gian (Time-box) theo mốc endsAt thật
            long endsAtSec = dayInfo.optLong("endsAt", 0);
            long nowSec = System.currentTimeMillis() / 1000L;
            long timeAvailableMs;
            if (endsAtSec > 0) {
                // T_available = (endsAt - now) - IO_BUFFER (1500ms an toàn mạng)
                timeAvailableMs = ((endsAtSec - nowSec) * 1000L) - 1500L;
            } else {
                // Practice mode thường không trả endsAt -> Tính theo daySeconds
                JSONArray daySecArr = mapResp.optJSONArray("daySeconds");
                int daySecs = (daySecArr != null) ? daySecArr.optInt(day, 5) : 5;
                timeAvailableMs = (daySecs * 1000L) - 1500L;
            }

            System.out.println("-> [PHASE 1] Giao thông đã cập nhật. Ngân sách AI: " + timeAvailableMs + "ms");

            // 3. Quyết định chiến lược dựa trên thời gian thực
            String strategy = "normal|timeLimit=" + timeAvailableMs;
            if (timeAvailableMs < 600L) {
                System.out.println("-> [CẢNH BÁO KHẨN] Thời gian còn dưới 0.6s! Kích hoạt Fast Fallback (LazyGreedy).");
                strategy = "fast_fallback|timeLimit=" + timeAvailableMs;
            }

            int daySteps = dayStepsList.get(day);
            List<List<Integer>> actions = generateDayPlan(day, state, grid, spots, daySteps, nAgents, typeMap, strategy);

            System.out.println("Đang gửi lộ trình ngày " + day + "...");
            JSONObject res = practice ? client.submitPractice(day, actions) : client.submitActions(day, actions);
            System.out.println("Kết quả: " + res.toString());
        }
        System.out.println("\n--- Trận đấu hoàn tất! ---");
    }

    private List<List<Integer>> generateDayPlan(int day, JSONObject state, HexGrid grid, List<JSONObject> spots,
                                                int daySteps, int nAgents, Map<String, String> typeMap, String strategy) {
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
                entry.put("fuelLimits", fuelLimits);

                (isPatrol ? patrolAgents : refuelAgents).add(entry);
            }
        }

        Set<Integer> brandsSeen = new HashSet<>();
        JSONArray brandsArr = teamData.optJSONArray("distinct_types");
        if (brandsArr != null) for (int i = 0; i < brandsArr.length(); i++) brandsSeen.add(brandsArr.getInt(i));

        int currentDay = state.optInt("day", 0);
        // Truyền tham số strategy mang theo Time-box xuống cho DayPlanner
        Map<String, List<Integer>> planned =
                planner.plan(grid, patrolAgents, refuelAgents, spots, daySteps, brandsSeen, currentDay, strategy);

        Map<String, Integer> startPosMap = new HashMap<>();
        for (JSONObject ag : patrolAgents) startPosMap.put(ag.getString("agent_id"), org.example.util.JsonUtil.getAgentPos(ag));
        for (JSONObject ag : refuelAgents) startPosMap.put(ag.getString("agent_id"), org.example.util.JsonUtil.getAgentPos(ag));

        PlanValidator.ValidationResult valRes = PlanValidator.validateAndNormalize(grid, planned, startPosMap, daySteps);

        if (!valRes.valid) {
            System.out.println("-> [CẢNH BÁO SANDBOX KHẨN] Kế hoạch AI bị lỗi: " + valRes.reason);
            System.out.println("-> [ROLLBACK] Kích hoạt chạy lại LazyGreedyPlanner để cứu Ngày " + currentDay + "!");

            DayPlanner fallback = new LazyGreedyPlanner();
            planned = fallback.plan(grid, patrolAgents, refuelAgents, spots, daySteps, brandsSeen, currentDay, "fallback");

            // Chuẩn hóa lại lộ trình Fallback một lần nữa cho chắc ăn!
            valRes = PlanValidator.validateAndNormalize(grid, planned, startPosMap, daySteps);

            if (!valRes.valid) {
                System.out.println("-> [LỖI TRẦM TRỌNG] Fallback cũng bị lỗi: " + valRes.reason + ". Ép đứng yên toàn bộ!");
                planned = new HashMap<>();
                for (String aid : startPosMap.keySet()) {
                    planned.put(aid, Collections.singletonList(-daySteps));
                }
                valRes = new PlanValidator.ValidationResult(true, "Force Wait", planned);
            }
        }

        List<List<Integer>> actions = new ArrayList<>();
        for (int i = 0; i < nAgents; i++) {
            String aid = String.valueOf(i);
            actions.add(valRes.normalizedActions.getOrDefault(aid, Collections.singletonList(-daySteps)));
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