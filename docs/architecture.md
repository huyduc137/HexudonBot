# Kiến trúc HEXUDON Java Bot

Tài liệu này ánh xạ các PHASE trong pipeline đã thảo luận (PHASE 0-4) sang vị trí code cụ thể,
để khi phát triển tiếp không bị lạc giữa lý thuyết và implementation.

| PHASE trong pipeline | Vai trò | File/gói tương ứng | Trạng thái |
|---|---|---|---|
| PHASE 0 — Pre-match setup & role allocation | Phân loại patrol/refuel trước trận | `engine/AgentRoleAssigner.java` | Đã có (baseline), tỷ lệ hardcode cần thực nghiệm lại |
| PHASE 1 — Daily perception & dynamic graph update | Cập nhật trạng thái đường mỗi ngày | `grid/HexGrid.roadCond`, `grid/TrafficTracker.java` | Đọc trực tiếp từ server đã có; tự dự đoán ngày tương lai **chưa làm** |
| PHASE 2 — Core engine (coupled VRP & refueling) | Chọn spot + định tuyến + tích hợp tiếp nhiên liệu | `planner/OrienteeringPlanner.java`, `planner/RefuelCoordinator.java` | **Chưa làm** — hiện dùng `LazyGreedyPlanner` (chỉ ngày 1, đứng yên các ngày sau) |
| PHASE 3 — Action translation & sandbox validation | Dịch lộ trình -> lệnh nguyên thủy, kiểm tra hợp lệ trước khi gửi | *(chưa có file riêng)* | **Chưa làm** — nên thêm `engine/PlanValidator.java` |
| PHASE 4 — Post-day learning & adaptation | Lưu lịch sử traffic, cập nhật brandsSeen | `grid/TrafficTracker.recordDay()` | **Chưa làm** |

## Nguyên tắc tách lớp đang áp dụng

- **grid**: chỉ dữ liệu bản đồ + thuật toán tìm đường thuần tuý, không biết gì về "chiến lược chơi".
- **planner**: chỉ logic ra quyết định (interface `DayPlanner` + nhiều implementation), không tự gọi HTTP.
- **client**: chỉ HTTP, không biết gì về thuật toán game.
- **engine**: orchestration cho chế độ CLI — gọi client, gọi planner, lặp ngày.
- **gui**: orchestration cho chế độ có giao diện — cùng gọi client/planner như engine nhưng cho phép
  người dùng can thiệp tay (sửa JSON hành động trước khi gửi).

Nhờ tách theo interface `DayPlanner`, khi `OrienteeringPlanner` viết xong, chỉ cần đổi **1 dòng**
khởi tạo ở `HexudonEngine` (constructor) và `HexudonGUI` (field `planner`) — không phải sửa
logic gọi API hay logic UI.

## Việc cần ưu tiên làm tiếp (theo thứ tự đề xuất)

1. `planner/OrienteeringPlanner.java` — giá trị cao nhất, vì baseline hiện tại chỉ chơi Ngày 1.
2. `planner/RefuelCoordinator.java` — cần thiết ngay khi OrienteeringPlanner mô phỏng nhiều bước.
3. Viết test thật cho `HexGridTest`/`PathfinderTest` (cần trước khi tin tưởng OrienteeringPlanner).
4. `engine/PlanValidator.java` *(chưa tạo file)* — sandbox kiểm tra fuel/step/pond trước khi submit,
   tránh bị server trả lời "invalid" làm mất trắng cả ngày thi đấu.
5. Tách `ConnectionPanel`/`AgentRolesPanel`/`DailyControlPanel` — ưu tiên thấp, chỉ ảnh hưởng
   khả năng bảo trì GUI, không ảnh hưởng điểm số thi đấu.
