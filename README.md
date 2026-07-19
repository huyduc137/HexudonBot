# HEXUDON Java Bot — Khung dự án phát triển & bảo trì

Xem `docs/architecture.md` để biết từng PHASE trong pipeline ánh xạ tới file nào.

## Cây thư mục đầy đủ

```
hexudon-bot/
├── pom.xml                                    [MỚI] Build Maven, phụ thuộc org.json
├── README.md                                  [MỚI] File này
├── config/
│   └── config.json                            [ĐÃ CÓ] server_url, game_id, team_id, token
├── docs/
│   └── architecture.md                        [MỚI] Ánh xạ PHASE pipeline <-> file code
│
├── src/main/java/org/example/
│   ├── Main.java                              [ĐÃ CÓ] Entry point CLI (không GUI)
│   │
│   ├── client/                                 — TẦNG GIAO TIẾP HTTP (không biết logic game)
│   │   ├── HexudonClient.java                 [ĐÃ CÓ] Gọi các endpoint /api/game/*
│   │   └── ApiException.java                  [TODO]  Phân loại lỗi AUTH/VALIDATION/NETWORK/SERVER
│   │
│   ├── grid/                                   — DỮ LIỆU BẢN ĐỒ + TÌM ĐƯỜNG (thuần thuật toán)
│   │   ├── HexGrid.java                       [ĐÃ CÓ] Địa hình, neighbors, travelSteps, fuelCost
│   │   ├── Pathfinder.java                    [ĐÃ CÓ] Dijkstra 2 tiêu chí (tách khỏi HexGrid cũ)
│   │   └── TrafficTracker.java                [TODO]  Công thức traffic thật + dự đoán ngày sau
│   │
│   ├── planner/                                — CHIẾN LƯỢC RA QUYẾT ĐỊNH (Strategy Pattern)
│   │   ├── DayPlanner.java                    [ĐÃ CÓ] Interface chung cho mọi chiến lược
│   │   ├── LazyGreedyPlanner.java             [ĐÃ CÓ] Baseline: ngày 1 đi spot gần nhất, sau đó đứng yên
│   │   ├── OrienteeringPlanner.java           [TODO]  Chiến lược chính: reward cộng dồn, coverage
│   │   │                                              chain, SA/Tabu, tích hợp refuel trong đánh giá
│   │   └── RefuelCoordinator.java             [TODO]  Lập lịch điểm hẹn patrol-refueler
│   │
│   ├── engine/                                 — ĐIỀU PHỐI (orchestration) cho chế độ CLI
│   │   ├── HexudonEngine.java                 [ĐÃ CÓ] Vòng lặp: connect -> roles -> loop ngày -> submit
│   │   ├── AgentRoleAssigner.java             [ĐÃ CÓ] Auto-assign patrol/refuel (tách khỏi Engine+GUI)
│   │   └── PlanValidator.java                 [TODO]  Sandbox kiểm tra fuel/step/pond trước khi gửi
│   │
│   ├── gui/                                    — ĐIỀU PHỐI cho chế độ có giao diện điều khiển tay
│   │   ├── HexudonGUI.java                    [ĐÃ CÓ] Cửa sổ chính, lắp ráp các panel
│   │   ├── HexMapPanel.java                   [ĐÃ CÓ] Vẽ bản đồ hex + spot + agent + lộ trình
│   │   ├── ConnectionPanel.java               [TODO]  Tách Group 1 (URL/GameID/Token/Practice)
│   │   ├── AgentRolesPanel.java               [TODO]  Tách Group 2 (ComboBox vai trò từng agent)
│   │   └── DailyControlPanel.java             [TODO]  Tách Group 3 (Fetch/Plan/Submit + JSON editor)
│   │
│   ├── model/                                  — (TRỐNG, xem package-info.java)
│   │   └── package-info.java                  [TODO]  Kế hoạch chuyển JSONObject thô -> POJO
│   │                                                   (Agent, Spot, MapConfig, DayState...)
│   │
│   └── util/
│       └── JsonUtil.java                      [TODO]  Gom các đoạn parse JSON đang bị lặp code
│
└── src/test/java/org/example/
    ├── grid/
    │   ├── HexGridTest.java                   [TODO]  Test terrain/neighbors/travelSteps/fuelCost
    │   └── PathfinderTest.java                [TODO]  Test Dijkstra + đúng convention hướng 0..5
    └── planner/
        └── LazyGreedyPlannerTest.java         [TODO]  Test baseline: fuel budget, step budget
```

**[ĐÃ CÓ]** = logic giữ nguyên từ code gốc bạn gửi, chỉ đổi package/vị trí file để tách trách nhiệm.
**[TODO]** = file mới, hiện chỉ có class rỗng + Javadoc mô tả CHI TIẾT việc cần làm và vì sao —
mở file lên là biết ngay phải code gì, không cần đọc lại toàn bộ hội thoại.

## Tách (tóm tắt)

- `HexGrid` (dữ liệu bản đồ) và `Pathfinder` (Dijkstra) trước đây gộp chung 1 file — tách ra để
  `HexGrid` chỉ có 1 lý do để thay đổi (Single Responsibility), và để dễ viết `PathfinderTest`
  độc lập với logic địa hình.
- `DayPlanner` trở thành **interface**, `LazyGreedyPlanner` là 1 implementation — khi
  `OrienteeringPlanner` viết xong, `HexudonEngine`/`HexudonGUI` chỉ cần đổi **1 dòng khởi tạo**,
  không phải sửa code gọi API hay UI.
- Logic "tự động gán vai trò" trước đây bị **copy-paste y hệt** giữa `demo_client.py`-tương-đương
  (`HexudonEngine`) và `HexudonGUI` — gom vào `AgentRoleAssigner` dùng chung.
- `HexMapPanel` trước đây là `private static` inner class trong `HexudonGUI` (~140 dòng) — tách
  ra file riêng vì nó không phụ thuộc trạng thái của cửa sổ chính, có thể test/tái sử dụng riêng.

