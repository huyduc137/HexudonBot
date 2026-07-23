package org.example.client;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * HexudonClient — lớp DUY NHẤT được phép gọi HTTP ra server HEXUDON.
 * Không chứa logic game; chỉ map các endpoint trong schema.pdf sang phương thức Java.
 *
 * TODO còn thiếu so với schema.pdf (server đã hỗ trợ nhưng client chưa gọi tới):
 *   - GET /api/game/day     -> getDayInfo(): thông tin ngày hiện tại kèm "endsAt" (deadline),
 *                              cần để canh thời gian phản hồi chính xác hơn polling /state.
 *   - GET /api/game/board   -> getBoardConfig(): cấu hình board độc lập với team (dùng cho spectator/debug).
 *   - GET /api/game/result  -> getResult(): bảng xếp hạng cuối trận + chi tiết điểm từng đội.
 *   - GET /api/game/replay  -> getReplay(): replay từng bước các ngày đã kết thúc (phục vụ debug/phân tích đối thủ).
 *   - Endpoint "/api/game/competitive/*" nếu dùng chế độ competitive-practice (đấu tập với đội khác).
 *   - Xử lý lỗi 422 (ValidationError) nên parse chi tiết field "detail" thay vì chỉ ném RuntimeException thô
 *     -> nên dùng {@link ApiException} (đang là stub) để phân loại lỗi (validation / auth / network).
 */
public class HexudonClient {
    private final String baseUrl;
    private final String gameId;
    public final String teamId;
    private final String token;
    private final HttpClient httpClient;

    public HexudonClient(String baseUrl, String gameId, String teamId, String token) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.gameId = gameId;
        this.teamId = teamId;
        this.token = token;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    private JSONObject get(String path, Map<String, String> params) {
        try {
            StringBuilder urlBuilder = new StringBuilder(baseUrl + path);
            if (params != null && !params.isEmpty()) {
                urlBuilder.append("?");
                List<String> query = new ArrayList<>();
                for (Map.Entry<String, String> e : params.entrySet()) {
                    query.add(e.getKey() + "=" + e.getValue());
                }
                urlBuilder.append(String.join("&", query));
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .header("User-Agent", "Hexudon-Java/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("GET " + path + " -> HTTP " + response.statusCode() + ": " + response.body());
            }
            return new JSONObject(response.body());
        } catch (Exception e) {
            throw new RuntimeException("HTTP GET Error: " + e.getMessage(), e);
        }
    }

    private JSONObject post(String path, JSONObject body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .header("User-Agent", "Hexudon-Java/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("POST " + path + " -> HTTP " + response.statusCode() + ": " + response.body());
            }
            return new JSONObject(response.body());
        } catch (Exception e) {
            throw new RuntimeException("HTTP POST Error: " + e.getMessage(), e);
        }
    }

    public JSONObject getMap() {
        return get("/api/game/config", Map.of("game_id", gameId));
    }

    public JSONObject getState() {
        return get("/api/game/state", Map.of("game_id", gameId));
    }

    public JSONObject getDayInfo() {
        return get("/api/game/day", Map.of("game_id", gameId));
    }

    public JSONObject getCompetitiveDayInfo() {
        return get("/api/game/competitive/state", Map.of("game_id", gameId));
    }

    public JSONObject getCompetitiveState() {
        return get("/api/game/competitive/state", Map.of("game_id", gameId));
    }


    /** TODO: chưa dùng — hữu ích để đọc điểm số / xếp hạng cuối trận. */
    public JSONObject getResult() {
        return get("/api/game/result", Map.of("game_id", gameId));
    }

    public JSONObject selectAgentTypes(List<JSONObject> assignments) {
        assignments.sort(Comparator.comparingInt(a -> Integer.parseInt(a.getString("agent_id"))));
        JSONArray types = new JSONArray();
        for (JSONObject a : assignments) {
            types.put(a.getString("type").equals("patrol") ? 0 : 1);
        }
        JSONObject body = new JSONObject();
        body.put("game_id", gameId);
        body.put("types", types);
        return post("/api/game/agent-types", body);
    }

    public JSONObject submitActions(int day, List<List<Integer>> actions) {
        return postActions("/api/game/actions", day, actions);
    }

    public JSONObject submitPractice(int day, List<List<Integer>> actions) {
        return postActions("/api/game/practice/actions", day, actions);
    }

    public JSONObject submitCompetitivePractice(int day, List<List<Integer>> actions) {
        return postActions("/api/game/competitive/actions", day, actions);
    }



    private JSONObject postActions(String endpoint, int day, List<List<Integer>> actions) {
        JSONArray actionsArr = new JSONArray();
        for (List<Integer> list : actions) actionsArr.put(new JSONArray(list));
        JSONObject body = new JSONObject();
        body.put("game_id", gameId);
        body.put("day", day);
        body.put("actions", actionsArr);
        return post(endpoint, body);
    }
}
