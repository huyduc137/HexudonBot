package org.example.util;

import java.util.*;

/**
 * MatchAuditLogger — Lớp tổng kết thành tích toàn trận đấu (Final Match Audit).
 * Ghi nhận điểm số, số phần Udon, số Brand độc nhất qua từng ngày và in ra bảng tổng kết.
 */
public final class MatchAuditLogger {

    public static class DailyRecord {
        public final int day;
        public final long scoreZ;
        public final int cGlobal;
        public final int cDaily;
        public final int uCount;

        public DailyRecord(int day, long scoreZ, int cGlobal, int cDaily, int uCount) {
            this.day = day;
            this.scoreZ = scoreZ;
            this.cGlobal = cGlobal;
            this.cDaily = cDaily;
            this.uCount = uCount;
        }
    }

    private static final List<DailyRecord> history = new ArrayList<>();
    private static final Set<Integer> allTimeBrands = new HashSet<>();

    private MatchAuditLogger() {}

    public static void clear() {
        history.clear();
        allTimeBrands.clear();
    }

    public static void recordDay(int day, long scoreZ, int cGlobal, int cDaily, int uCount, Set<Integer> brandsSeenToday) {
        history.add(new DailyRecord(day, scoreZ, cGlobal, cDaily, uCount));
        if (brandsSeenToday != null) {
            allTimeBrands.addAll(brandsSeenToday);
        }
    }

    /**
     * In ra Bảng Tổng Kết Cuối Trận Đấu ra Log Console.
     */
    public static String generateFinalReport(String gameId, String teamId) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========================================================================================\n");
        sb.append("                 🏆 BÁO CÁO TỔNG KẾT CUỐI TRẬN ĐẤU (FINAL MATCH AUDIT) 🏆\n");
        sb.append("========================================================================================\n");
        sb.append(" • Game ID : ").append(gameId).append("\n");
        sb.append(" • Team ID : ").append(teamId).append("\n");
        sb.append(" • Tổng số ngày thi đấu: ").append(history.size()).append(" ngày\n");
        sb.append("----------------------------------------------------------------------------------------\n");
        sb.append(String.format(" %-8s | %-15s | %-18s | %-15s | %-12s \n", "NGÀY", "ĐIỂM SỐ (Z)", "BRAND MỚI TOÀN TRẬN", "BRAND ĐỘC NHẤT", "Tổng SỐ BÁT UDON"));
        sb.append("----------------------------------------------------------------------------------------\n");

        long totalZ = 0;
        int totalUdon = 0;
        int maxDailyBrands = 0;

        for (DailyRecord r : history) {
            totalZ += r.scoreZ;
            totalUdon += r.uCount;
            if (r.cDaily > maxDailyBrands) maxDailyBrands = r.cDaily;

            sb.append(String.format(" Ngày %-3d | %-15d | %-18d | %-15d | %-12d \n",
                    r.day, r.scoreZ, r.cGlobal, r.cDaily, r.uCount));
        }

        sb.append("========================================================================================\n");
        sb.append(" 📈 THÀNH TÍCH CHUNG CUỘC:\n");
        sb.append("    1. Tổng số Thương Hiệu độc nhất ăn được (Ưu tiên #1) : ").append(allTimeBrands.size()).append(" / 4 Brand\n");
        sb.append("    2. Kỷ lục Brand độc nhất trong 1 ngày (Ưu tiên #2)   : ").append(maxDailyBrands).append(" Brand\n");
        sb.append("    3. TỔNG SỐ PHẦN UDON THU THẬP ĐƯỢC (Ưu tiên #3)      : ").append(totalUdon).append(" phần\n");
        sb.append("    4. TỔNG ĐIỂM HÀM MỤC TIÊU TÍCH LŨY (Total Z)         : ").append(totalZ).append(" điểm\n");
        sb.append("========================================================================================\n");

        String report = sb.toString();
        System.out.println(report);
        return report;
    }
}