package org.example.engine;

/**
 * GameMode — 3 chế độ đấu tương ứng đúng 3 nhóm API trong schema.pdf:
 *   PRACTICE    -> /api/game/practice/*      (tự luyện tập, 1 mình)
 *   COMPETITIVE -> /api/game/competitive/*   (đấu tập chung, có bảng xếp hạng chia sẻ)
 *   OFFICIAL    -> /api/game/*               (thi đấu chính thức)
 *
 * Trước đây HexudonEngine chỉ có "boolean practice" nên không thể chạy Competitive qua CLI.
 */
public enum GameMode {
    PRACTICE,
    COMPETITIVE,
    OFFICIAL;

    public static GameMode fromArg(String arg) {
        if (arg == null) return OFFICIAL;
        switch (arg.trim().toLowerCase()) {
            case "practice": return PRACTICE;
            case "competitive": return COMPETITIVE;
            case "official": return OFFICIAL;
            default:
                throw new IllegalArgumentException("Chế độ không hợp lệ: '" + arg + "' (chỉ nhận practice/competitive/official)");
        }
    }

    public String label() {
        switch (this) {
            case PRACTICE: return "Practice";
            case COMPETITIVE: return "Competitive";
            default: return "Official";
        }
    }
}