package org.example.grid;

/**
 * TODO — CHƯA CÓ TEST NÀO trong toàn dự án. Đây là rủi ro lớn nhất hiện tại: mọi thay đổi
 * (đặc biệt là khi implement OrienteeringPlanner) đều không có lưới an toàn để phát hiện
 * hồi quy (regression) trước khi mang ra thi đấu thật.
 *
 * Cần thêm dependency JUnit 5 vào pom.xml (xem TODO trong pom.xml) rồi viết các case tối thiểu:
 *
 *   - testNeighbors_evenRow_vs_oddRow(): xác nhận offset EVEN_DELTAS/ODD_DELTAS đúng hướng
 *     0..5 theo đúng thứ tự "0 = upper-left, 1 = upper-right, ... clockwise" như schema.pdf
 *     mô tả (Agent Type Answer Format / Action Plan Answer Format).
 *   - testNeighbors_excludesPondAndOutOfBounds(): ô Pond hoặc ngoài biên không được trả về.
 *   - testTravelSteps_perTerrain(): Plain=2, Mountain=3, Road SMOOTH/CONGESTED/JAM = 1/2/4.
 *   - testFuelCost_perTerrain(): Plain=1, Mountain=2, Road=2 (Pond không áp dụng, không đi được).
 *   - testFromMapResponse_parsesSampleFromSchema(): dùng đúng ví dụ JSON map 8x8 trong
 *     schema.pdf (phần "Map Configuration Format Before the Match Starts") làm fixture,
 *     để đảm bảo parser không lệch khi server đổi định dạng nhỏ.
 */
public class HexGridTest {
    // TODO: implement
}
