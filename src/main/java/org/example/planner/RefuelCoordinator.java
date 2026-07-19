package org.example.planner;

import org.example.grid.HexGrid;

/**
 * TODO — CHƯA IMPLEMENT. Được {@link OrienteeringPlanner} gọi khi mô phỏng một lộ trình
 * patrol phát hiện sẽ hết nhiên liệu giữa chừng.
 *
 * Việc cần làm:
 *   1. Với vị trí/thời điểm patrol sẽ cạn fuel, tìm refueler mà đội mình đang quản lý
 *      có thể ĐẾN CÙNG Ô đúng lúc hoặc sớm hơn (refueler không tốn fuel khi di chuyển,
 *      nhưng vẫn bị giới hạn bởi số bước còn lại trong ngày).
 *   2. Nếu nhiều patrol cùng cần refueler trong ngày, đây là bài toán lập lịch nhiều
 *      điểm hẹn (scheduling) cho 1 hoặc vài refueler — cân nhắc thứ tự ưu tiên theo
 *      "spot có giá trị coverage cao nhất đang chờ patrol tiếp cận".
 *   3. Trả về: hoặc một lộ trình refueler khả thi + điểm/bước hẹn, hoặc "infeasible"
 *      để OrienteeringPlanner loại route patrol tương ứng ngay trong hàm đánh giá.
 *
 * Gợi ý cấu trúc:
 *   public Optional<RendezvousPlan> findRendezvous(HexGrid grid,
 *                                                   Agent patrol, int fuelExhaustedAtStep, int exhaustedAtPos,
 *                                                   List<Agent> availableRefuelers, int remainingDaySteps)
 */
public class RefuelCoordinator {

    public RefuelCoordinator(HexGrid grid) {
        // TODO: implement
    }
}
