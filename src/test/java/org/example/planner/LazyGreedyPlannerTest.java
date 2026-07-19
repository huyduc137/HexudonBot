package org.example.planner;

/**
 * TODO — CHƯA IMPLEMENT.
 *   - testDay0_assignsEachPatrolToDistinctNearestSpot(): 2 patrol không được nhận CÙNG 1 spot
 *     trong cùng ngày (đúng luật: mỗi điểm chỉ 1 lượt thu hoạch/patrol/ngày, nhưng cử 2 patrol
 *     tới cùng 1 điểm vẫn lãng phí nước đi của patrol thứ 2).
 *   - testDay0_respectsFuelBudget(): lộ trình sinh ra không được vượt quá "fuel" hiện có
 *     của patrol (server sẽ từ chối toàn bộ answer nếu vi phạm).
 *   - testDay0_respectsStepBudget(): tổng bước dùng + bước chờ còn lại phải khớp CHÍNH XÁC
 *     với daySteps (theo luật: "Each agent's action plan must match the number of steps
 *     in the day", không được thiếu/thừa).
 *   - testDayGreaterThan0_allAgentsWait(): xác nhận rõ hành vi HIỆN TẠI (đứng yên từ ngày 2)
 *     — test này nên bị XOÁ/SỬA ngay khi OrienteeringPlanner thay thế LazyGreedyPlanner
 *     làm chiến lược mặc định, để nhắc nhở đây chỉ là baseline tạm thời.
 */
public class LazyGreedyPlannerTest {
    // TODO: implement
}
