package org.example.gui;

/**
 * TODO — CHƯA TÁCH. Hiện "GROUP 1: Cấu hình kết nối" (URL, Game ID, Team ID, Token,
 * Practice checkbox, nút Connect) vẫn đang được xây trực tiếp trong constructor của
 * {@link HexudonGUI} (~40 dòng).
 *
 * Việc cần làm khi tách:
 *   1. Class này extends JPanel, tự chứa các JTextField/JPasswordField/JCheckBox/JButton.
 *   2. Expose getter cho từng field (getUrl(), getGameId(), getTeamId(), getToken(), isPractice())
 *      thay vì để HexudonGUI giữ field trực tiếp.
 *   3. Nhận 1 callback (ví dụ java.util.function.Consumer<ConnectionInfo>) để gọi khi bấm
 *      nút Connect, thay vì gọi thẳng cmdConnect() của HexudonGUI -> giảm phụ thuộc 2 chiều.
 *
 * LƯU Ý BẢO MẬT: token JWT mặc định đang bị hardcode thẳng trong source (xem DEFAULT_TOKEN
 * ở HexudonGUI/Main) — nên chuyển sang đọc từ config/config.json hoặc biến môi trường,
 * và KHÔNG commit token thật lên git công khai (token có thời hạn nhưng vẫn nên xoay vòng).
 */
public class ConnectionPanel {
    // TODO: implement — xem mô tả phía trên.
}
