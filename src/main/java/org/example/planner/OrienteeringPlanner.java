package org.example.planner;

import org.example.grid.HexGrid;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TODO — CHƯA IMPLEMENT. Đây là chiến lược THẬT SỰ cần cho thi đấu, thay thế
 * {@link LazyGreedyPlanner} (vốn chỉ dùng được cho Ngày 1 rồi đứng yên).
 *
 * Việc cần làm (tương ứng PHASE 2 trong pipeline đã thảo luận trong hội thoại):
 *
 * 1. HÀM ĐIỂM THƯỞNG (reward function) — CỘNG DỒN, không loại trừ theo tier:
 *      score(spot) = 10000 * isNewChainEver(spot, brandsSeen)
 *                  +   100 * isNewChainToday(spot, chainsCollectedToday)
 *                  +     1                                   // luôn cộng, vì mọi lần thu = +1 phần
 *      Lưu ý: phải mô phỏng SUY GIẢM KHO DỰ TRỮ nội bộ đội mình theo thời gian trong ngày
 *      (spot.stocks giảm dần khi CHÍNH đội mình ghé — độc lập với đội khác), để tránh cử
 *      2 patrol của mình đến cùng 1 điểm đã cạn kho trong ngày.
 *
 * 2. CHỌN TẬP ĐIỂM MỤC TIÊU (Orienteering / Team Orienteering Problem):
 *      - Với bản đồ nhỏ (ít spot, ít agent): có thể giải bằng ILP/CP-SAT (OR-Tools) nếu có binding Java,
 *        hoặc brute-force có cắt tỉa.
 *      - Với bản đồ lớn: Greedy "gain/cost" rồi cải thiện bằng Simulated Annealing / Tabu Search,
 *        time-boxed theo "daySeconds" còn lại (phải chừa thời gian cho bước 4 - sandbox validate).
 *
 * 3. TÍCH HỢP TIẾP NHIÊN LIỆU NGAY TRONG HÀM ĐÁNH GIÁ (không rollback bị động):
 *      - Khi mô phỏng 1 lộ trình patrol, nếu fuel dự kiến âm tại bước k -> gọi
 *        {@link RefuelCoordinator} để tìm điểm hẹn khả thi; nếu không có -> loại route đó
 *        ngay trong hàm đánh giá (score = -infinity), KHÔNG đợi đến sandbox mới phát hiện.
 *
 * 4. MULTI-DAY LOOKAHEAD (tuỳ chọn nâng cao):
 *      - Dùng {@link org.example.grid.TrafficTracker} để dự đoán trạng thái đường ngày kế tiếp,
 *        tránh chọn lộ trình khiến chính đội mình tự làm tắc đường cho ngày sau.
 *
 * Interface đã chuẩn hoá qua {@link DayPlanner} nên khi implement xong, chỉ cần đổi
 * dòng khởi tạo planner trong HexudonEngine / HexudonGUI từ `new LazyGreedyPlanner()`
 * sang `new OrienteeringPlanner()` — không cần sửa gì khác.
 */
public class OrienteeringPlanner implements DayPlanner {

    @Override
    public Map<String, List<Integer>> plan(
            HexGrid grid,
            List<JSONObject> patrolAgents,
            List<JSONObject> refuelAgents,
            List<JSONObject> spots,
            int daySteps,
            Set<Integer> brandsSeen,
            int currentDay,
            String strategy) {
        throw new UnsupportedOperationException(
                "TODO: chưa implement OrienteeringPlanner — dùng LazyGreedyPlanner tạm thời");
    }
}
