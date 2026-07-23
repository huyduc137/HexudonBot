package org.example.grid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * HexGrid — Quản lý dữ liệu bản đồ và chi phí di chuyển.
 * NÂNG CẤP PHASE 1: Thêm khả năng cập nhật trạng thái tắc đường thực tế theo từng ngày.
 */
public class HexGrid {
    public static final int PLAIN = 0, ROAD = 1, MOUNTAIN = 2, POND = 3;
    public static final int SMOOTH = 0, CONGESTED = 1, JAM = 2;

    private static final int PLAIN_STEPS = 2;
    private static final int MOUNTAIN_STEPS = 3;
    private static final Map<Integer, Integer> ROAD_STEPS = Map.of(SMOOTH, 1, CONGESTED, 2, JAM, 4);
    private static final Map<Integer, Integer> FUEL_COST = Map.of(PLAIN, 1, ROAD, 2, MOUNTAIN, 2);

    private static final int[][] EVEN_DELTAS = {{-1, 0}, {-1, 1}, {0, 1}, {1, 1}, {1, 0}, {0, -1}};
    private static final int[][] ODD_DELTAS  = {{-1, -1}, {-1, 0}, {0, 1}, {1, 0}, {1, -1}, {0, -1}};
    private static final Map<String, Integer> CHAR_TO_TERRAIN = Map.of("P", PLAIN, "R", ROAD, "M", MOUNTAIN, "O", POND);

    public final int width;
    public final int height;
    public final List<Integer> cells;
    /** Trạng thái đường bộ hiện tại: pos -> SMOOTH/CONGESTED/JAM. */
    public Map<Integer, Integer> roadCond;

    public HexGrid(int width, int height, List<Integer> cells, Map<Integer, Integer> roadCond) {
        this.width = width;
        this.height = height;
        this.cells = cells;
        this.roadCond = roadCond;
    }

    /**
     * NÂNG CẤP PHASE 1: Cập nhật trạng thái giao thông thật từ Server vào đầu mỗi ngày.
     * Khi gọi hàm này, travelSteps(pos) sẽ tự động trả về chi phí mới (1, 2, hoặc 4 bước).
     */
    public void updateTraffic(JSONArray trafficsArr) {
        if (trafficsArr == null) return;
        for (int i = 0; i < trafficsArr.length(); i++) {
            JSONObject t = trafficsArr.getJSONObject(i);
            int pos = t.getInt("pos");
            int status = t.getInt("status"); // 0: SMOOTH, 1: CONGESTED, 2: JAM
            roadCond.put(pos, status);
        }
    }

    public static HexGrid fromMapResponse(JSONObject resp) {
        int w, h;
        List<Integer> flat = new ArrayList<>();
        if (resp.has("map")) {
            JSONObject m = resp.getJSONObject("map");
            w = m.getInt("width");
            h = m.getInt("height");
            JSONArray cellsArr = m.getJSONArray("cells");
            for (int i = 0; i < cellsArr.length(); i++) {
                JSONArray row = cellsArr.getJSONArray(i);
                for (int j = 0; j < row.length(); j++) flat.add(row.getInt(j));
            }
        } else {
            w = resp.getInt("width");
            h = resp.getInt("height");
            JSONArray raw = resp.getJSONArray("cells");
            for (int i = 0; i < raw.length(); i++) {
                Object val = raw.get(i);
                flat.add(val instanceof Integer
                        ? (Integer) val
                        : CHAR_TO_TERRAIN.getOrDefault(val.toString().toUpperCase(), 0));
            }
        }
        return new HexGrid(w, h, flat, new HashMap<>());
    }

    public static class Neighbor {
        public final int direction;
        public final int pos;
        public Neighbor(int direction, int pos) { this.direction = direction; this.pos = pos; }
    }

    public List<Neighbor> neighbors(int pos) {
        int row = pos / width;
        int col = pos % width;
        int[][] deltas = (row % 2 == 0) ? EVEN_DELTAS : ODD_DELTAS;
        List<Neighbor> result = new ArrayList<>();
        for (int d = 0; d < deltas.length; d++) {
            int nr = row + deltas[d][0];
            int nc = col + deltas[d][1];
            if (nr >= 0 && nr < height && nc >= 0 && nc < width) {
                int nb = nr * width + nc;
                if (cells.get(nb) != POND) result.add(new Neighbor(d, nb));
            }
        }
        return result;
    }

    /** Số bước cần thiết để rời khỏi ô pos (phụ thuộc địa hình + trạng thái tắc đường thực tế). */
    public int travelSteps(int pos) {
        int t = cells.get(pos);
        if (t == ROAD) return ROAD_STEPS.getOrDefault(roadCond.getOrDefault(pos, SMOOTH), 1);
        return (t == PLAIN) ? PLAIN_STEPS : MOUNTAIN_STEPS;
    }

    /** Nhiên liệu tiêu thụ khi rời khỏi ô pos. */
    public int fuelCost(int pos) {
        return FUEL_COST.getOrDefault(cells.get(pos), 0);
    }
    public void updateTrafficFromObject(JSONObject roadCondObj) {
        if (roadCondObj == null) return;
        for (String key : roadCondObj.keySet()) {
            try {
                int pos = Integer.parseInt(key);
                int status = roadCondObj.getInt(key);
                roadCond.put(pos, status);
            } catch (Exception ignored) {}
        }
    }
}