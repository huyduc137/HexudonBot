package org.example.grid;

import java.util.*;

/**
 * TODO — CHƯA IMPLEMENT. Đây là nơi cần code công thức traffic THẬT theo luật thi,
 * thay vì "hazard heatmap" phỏng đoán.
 *
 * Công thức chính xác (theo đề bài / theo server):
 *   traffic(cell) = ( tổng số "bước dừng lại" của MỌI agent, MỌI đội, trên ô đường đó,
 *                      trong 2 ngày trước đó (hôm qua + hôm kia) )  /  số đội (players).
 *
 *   - Ngày 1: mọi đường ở trạng thái SMOOTH.
 *   - Ngày 2: chỉ tính traffic của Ngày 1 (chưa đủ 2 ngày).
 *   - Từ Ngày 3: tính traffic gộp của (ngày N-1) + (ngày N-2).
 *
 *   status = SMOOTH   nếu traffic <  busyThreshold
 *          = CONGESTED nếu busyThreshold <= traffic < jammedThreshold
 *          = JAM       nếu traffic >= jammedThreshold
 *
 * Nguồn dữ liệu đầu vào cho lớp này:
 *   - Endpoint "/api/game/state" trả field "traffics": [{pos, status}, ...] — server đã tính sẵn
 *     trạng thái đường cho ngày hiện tại, KHÔNG cần tự tính lại bằng tay khi thi đấu thật.
 *   - Lớp này chỉ thật sự cần thiết nếu muốn TỰ DỰ ĐOÁN trạng thái đường của các ngày
 *     TƯƠNG LAI (N+1, N+2) trước khi server công bố, để lập kế hoạch dài hạn hơn 1 ngày.
 *
 * Việc cần làm:
 *   1. Lưu lịch sử "số bước dừng lại tại mỗi ô đường" theo ngày, cho cả đội mình
 *      (biết chắc) và các đội khác (suy ra từ "others" trong /api/game/day, xem model.AgentSnapshot).
 *   2. Viết hàm predictNextDayStatus(HexGrid grid, int busyThreshold, int jammedThreshold)
 *      trả về Map<pos, status> dự đoán, dùng cho OrienteeringPlanner khi lập kế hoạch
 *      nhiều ngày (multi-day lookahead).
 *   3. Cân nhắc: đội mình có thể ảnh hưởng đến traffic của chính mình ở ngày sau
 *      (né lặp lại đường mình vừa "làm tắc").
 */
public class TrafficTracker {

    /** history.get(day).get(pos) = số bước dừng lại tại ô 'pos' trong ngày đó (mọi đội). */
    private final Map<Integer, Map<Integer, Integer>> stopStepsByDay = new HashMap<>();

    public void recordDay(int day, Map<Integer, Integer> stopStepsPerCell) {
        stopStepsByDay.put(day, stopStepsPerCell);
        // TODO: implement
    }

    public Map<Integer, Integer> predictNextDayStatus(HexGrid grid, int numTeams,
                                                        int busyThreshold, int jammedThreshold) {
        throw new UnsupportedOperationException("TODO: chưa implement dự đoán traffic ngày kế tiếp");
    }
}
