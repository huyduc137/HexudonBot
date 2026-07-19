package org.example.grid;

/**
 * TODO — CHƯA IMPLEMENT.
 *   - testShortestPath_prefersFewerSteps(): trên bản đồ trộn Plain/Road/Mountain, xác nhận
 *     đường đi trả về đúng là đường có TỔNG BƯỚC nhỏ nhất, không phải tổng SỐ Ô nhỏ nhất.
 *   - testFuelLimit_prunesUnreachableCells(): đặt fuelLimit thấp, xác nhận các ô ngoài tầm
 *     nhiên liệu KHÔNG xuất hiện trong distSteps/distFuel.
 *   - testOptimizeFuelFlag_swapsPriority(): với optimizeFuel=true, xác nhận thuật toán ưu
 *     tiên tối thiểu NHIÊN LIỆU trước bước — quan trọng cho RefuelCoordinator khi tìm điểm
 *     hẹn refueler tốn ít fuel nhất.
 *   - testReconstructPath_matchesDirectionsConsumedByServer(): hướng trả về (0..5) phải
 *     khớp đúng convention "0=upper-left...clockwise" của schema.pdf, vì đây là dữ liệu
 *     GỬI THẲNG lên server — sai hướng ở đây sẽ khiến toàn bộ answer bị server từ chối
 *     ("move to non-adjacent cell" -> invalid response cho CẢ ĐỘI, theo luật).
 */
public class PathfinderTest {
    // TODO: implement
}
