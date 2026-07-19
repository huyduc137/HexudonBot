package org.example.gui;

/**
 * TODO — CHƯA TÁCH. Hiện "GROUP 3: Điều khiển lượt thi đấu" (label ngày hiện tại, 3 nút
 * Fetch/Plan/Submit, ô JTextArea JSON hành động sửa tay được) vẫn nằm trong constructor
 * của {@link HexudonGUI} (~35 dòng).
 *
 * Việc cần làm khi tách:
 *   1. Panel tự quản lý JTextArea hiển thị/sửa JSON hành động + validate cú pháp JSON
 *      cơ bản trước khi cho bấm nút Submit (hiện chưa validate, lỗi JSON sẽ rơi thẳng
 *      xuống catch(Exception) trong cmdSubmitDay và chỉ log ra, dễ bị bỏ sót).
 *   2. Thêm JComboBox chọn planner (LazyGreedyPlanner / OrienteeringPlanner) khi cả hai
 *      cùng tồn tại, để người dùng so sánh trực tiếp trong lúc luyện tập (practice mode).
 *   3. Cân nhắc thêm nút "Xem trước điểm số ước tính" (gọi thử hàm reward trong
 *      OrienteeringPlanner) trước khi submit thật.
 */
public class DailyControlPanel {
    // TODO: implement — xem mô tả phía trên.
}
