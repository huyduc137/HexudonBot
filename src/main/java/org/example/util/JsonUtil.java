package org.example.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * JsonUtil — Lớp tiện ích xử lý JSON dùng chung cho toàn bộ Engine, GUI và Planner.
 * Loại bỏ hoàn toàn sự lặp lại code khi parse dữ liệu từ Server HEXUDON.
 */
public final class JsonUtil {
    private JsonUtil() {}

    /**
     * Lấy dữ liệu của đội mình một cách an toàn từ object "teams" trong state.
     * Hỗ trợ cả key là String lẫn key là Integer (tuỳ phiên bản serialize của server).
     */
    public static JSONObject getTeamData(JSONObject state, String teamId) {
        if (state == null || !state.has("teams")) return new JSONObject();
        JSONObject teams = state.getJSONObject("teams");
        JSONObject teamData = teams.optJSONObject(teamId);
        if (teamData == null) {
            teamData = teams.optJSONObject(String.valueOf(teamId));
        }
        return (teamData != null) ? teamData : new JSONObject();
    }

    /**
     * Lấy vị trí cell 1D (pos) của Agent, tương thích ngược với trường "cell" cũ.
     */
    public static int getAgentPos(JSONObject agentJson) {
        if (agentJson.has("pos")) return agentJson.getInt("pos");
        if (agentJson.has("cell")) return agentJson.getInt("cell");
        throw new IllegalArgumentException("Agent JSON không chứa trường 'pos' hoặc 'cell': " + agentJson);
    }

    /**
     * Chuyển đổi JSONArray 2D từ Server thành List<List<Integer>> trong Java.
     */
    public static List<List<Integer>> toActionLists(JSONArray jsonActions) {
        List<List<Integer>> actions = new ArrayList<>();
        if (jsonActions == null) return actions;
        for (int i = 0; i < jsonActions.length(); i++) {
            JSONArray arr = jsonActions.getJSONArray(i);
            List<Integer> list = new ArrayList<>();
            for (int j = 0; j < arr.length(); j++) {
                list.add(arr.getInt(j));
            }
            actions.add(list);
        }
        return actions;
    }

    /**
     * Chuyển đổi List<List<Integer>> thành JSONArray 2D để chuẩn bị gửi POST lên Server.
     */
    public static JSONArray fromActionLists(List<List<Integer>> actions) {
        JSONArray jsonActions = new JSONArray();
        if (actions == null) return jsonActions;
        for (List<Integer> list : actions) {
            jsonActions.put(new JSONArray(list));
        }
        return jsonActions;
    }
}