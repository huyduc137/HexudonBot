package org.example.gui;

import org.example.grid.HexGrid;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * HexMapPanel — Khung vẽ bản đồ lục giác chiến thuật.
 *
 * Nâng cấp:
 * 1. Hiển thị Mật độ Giao thông: Đường bình thường (Xám) -> Đông đúc (Cam vàng) -> Tắc nghẽn (Đỏ).
 * 2. Hiển thị Hao hụt Udon: Khi bấm "Lập kế hoạch", bản đồ tự động mô phỏng đường đi của Agent
 *    để trừ số lượng Udon trên giao diện. Quán nào hết Udon sẽ bị mờ đi (Màu xám).
 */
public class HexMapPanel extends JPanel {
    private HexGrid grid;
    private List<JSONObject> spots = new ArrayList<>();
    private List<JSONObject> agents = new ArrayList<>();
    private List<List<Integer>> plannedActions = new ArrayList<>();
    private final int hexRadius = 22;

    public HexMapPanel() {
        setBackground(Color.WHITE);
    }

    public void setMapData(HexGrid grid, List<JSONObject> spots, List<JSONObject> agents, List<List<Integer>> plannedActions) {
        this.grid = grid;
        this.spots = spots;
        this.agents = agents;
        this.plannedActions = plannedActions;
        repaint();
    }

    public List<JSONObject> getAgents() {
        return agents;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (grid == null) return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double hexW = Math.sqrt(3) * hexRadius;
        double hexH = 2 * hexRadius;
        int offsetX = 40, offsetY = 40;

        // =========================================================================
        // 1. VẼ CÁC Ô BẢN ĐỒ VÀ MẬT ĐỘ GIAO THÔNG (TRAFFIC DENSITY)
        // =========================================================================
        for (int r = 0; r < grid.height; r++) {
            for (int c = 0; c < grid.width; c++) {
                int pos = r * grid.width + c;
                int terrain = grid.cells.get(pos);

                double cx = offsetX + c * hexW;
                if (r % 2 != 0) cx -= hexW / 2.0;
                double cy = offsetY + r * (3.0 / 4.0) * hexH;

                Polygon poly = new Polygon();
                for (int i = 0; i < 6; i++) {
                    double angleDeg = 60 * i - 30;
                    double angleRad = Math.PI / 180 * angleDeg;
                    poly.addPoint((int) (cx + hexRadius * Math.cos(angleRad)),
                            (int) (cy + hexRadius * Math.sin(angleRad)));
                }

                // Tô màu theo địa hình và MẬT ĐỘ TẮC ĐƯỜNG
                if (terrain == HexGrid.PLAIN) {
                    g2.setColor(new Color(200, 240, 200)); // Xanh lá nhạt
                } else if (terrain == HexGrid.MOUNTAIN) {
                    g2.setColor(new Color(160, 100, 50));  // Nâu núi
                } else if (terrain == HexGrid.POND) {
                    g2.setColor(new Color(100, 150, 250)); // Xanh dương ao hồ
                } else if (terrain == HexGrid.ROAD) {
                    // Xử lý màu sắc đường bộ theo Road Condition
                    int status = grid.roadCond.getOrDefault(pos, HexGrid.SMOOTH);
                    if (status == HexGrid.JAM) {
                        g2.setColor(new Color(231, 76, 60));   // Đỏ (Tắc nghẽn)
                    } else if (status == HexGrid.CONGESTED) {
                        g2.setColor(new Color(241, 196, 15));  // Cam vàng (Đông đúc)
                    } else {
                        g2.setColor(new Color(220, 220, 220)); // Xám (Thông thoáng)
                    }
                } else {
                    g2.setColor(Color.WHITE);
                }

                g2.fillPolygon(poly);
                g2.setColor(Color.GRAY);
                g2.drawPolygon(poly);

                g2.setColor(new Color(150, 150, 150));
                g2.setFont(new Font("Arial", Font.PLAIN, 9));
                g2.drawString(String.valueOf(pos), (int) cx - 6, (int) cy + 3);
            }
        }

        // =========================================================================
        // 2. MÔ PHỎNG HAO HỤT UDON DỰA TRÊN LỘ TRÌNH ĐƯỢC LẬP
        // =========================================================================
        Map<Integer, Integer> currentStocks = new HashMap<>();
        for (JSONObject spot : spots) {
            currentStocks.put(spot.getInt("pos"), spot.getInt("stocks"));
        }

        if (plannedActions != null && plannedActions.size() == agents.size()) {
            for (int i = 0; i < agents.size(); i++) {
                JSONObject ag = agents.get(i);
                if ("refuel".equals(ag.optString("type"))) continue; // Xe tiếp xăng không ăn Udon

                int curPos = ag.has("pos") ? ag.getInt("pos") : ag.getInt("cell");
                Set<Integer> visitedToday = new HashSet<>(); // Mỗi xe chỉ thu 1 suất/quán/ngày

                // Kiểm tra ăn ngay tại điểm xuất phát
                if (currentStocks.containsKey(curPos) && !visitedToday.contains(curPos)) {
                    int s = currentStocks.get(curPos);
                    if (s > 0) {
                        currentStocks.put(curPos, s - 1);
                        visitedToday.add(curPos);
                    }
                }

                // Lần theo quỹ đạo lộ trình để trừ Udon tiện đường
                for (int act : plannedActions.get(i)) {
                    if (act >= 0 && act <= 5) {
                        for (HexGrid.Neighbor nb : grid.neighbors(curPos)) {
                            if (nb.direction == act) {
                                curPos = nb.pos;
                                break;
                            }
                        }
                        if (currentStocks.containsKey(curPos) && !visitedToday.contains(curPos)) {
                            int s = currentStocks.get(curPos);
                            if (s > 0) {
                                currentStocks.put(curPos, s - 1);
                                visitedToday.add(curPos);
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 3. VẼ CÁC ĐIỂM BÁN UDON (SPOTS)
        // =========================================================================
        for (JSONObject spot : spots) {
            int pos = spot.getInt("pos");
            int r = pos / grid.width, c = pos % grid.width;
            double cx = offsetX + c * hexW - (r % 2 != 0 ? hexW / 2.0 : 0);
            double cy = offsetY + r * (3.0 / 4.0) * hexH;

            int stock = currentStocks.getOrDefault(pos, spot.getInt("stocks"));

            // Đổi màu nếu quán bị ăn cạn Udon
            if (stock > 0) {
                g2.setColor(new Color(180, 0, 0)); // Đỏ rực (Còn hàng)
                g2.setFont(new Font("Arial", Font.BOLD, 11));
            } else {
                g2.setColor(new Color(120, 120, 120)); // Xám mờ chìm xuống (Hết hàng)
                g2.setFont(new Font("Arial", Font.ITALIC, 11));
            }

            g2.drawString("B" + spot.getInt("brand") + " (S" + stock + ")", (int) cx - 18, (int) cy - 5);
        }

        // =========================================================================
        // 4. VẼ ĐƯỜNG ĐI DỰ KIẾN (PLANNED PATHS) VÀ TỌA ĐỘ XE
        // =========================================================================
        if (plannedActions != null && plannedActions.size() == agents.size()) {
            g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{4}, 0));
            for (int i = 0; i < agents.size(); i++) {
                JSONObject ag = agents.get(i);
                int curPos = ag.has("pos") ? ag.getInt("pos") : ag.getInt("cell");
                int r = curPos / grid.width, c = curPos % grid.width;
                double cx = offsetX + c * hexW - (r % 2 != 0 ? hexW / 2.0 : 0);
                double cy = offsetY + r * (3.0 / 4.0) * hexH;

                g2.setColor("patrol".equals(ag.optString("type")) ? new Color(0, 150, 0) : Color.MAGENTA);

                for (int action : plannedActions.get(i)) {
                    if (action >= 0 && action <= 5) {
                        for (HexGrid.Neighbor nb : grid.neighbors(curPos)) {
                            if (nb.direction == action) {
                                int nr = nb.pos / grid.width, nc = nb.pos % grid.width;
                                double nx = offsetX + nc * hexW - (nr % 2 != 0 ? hexW / 2.0 : 0);
                                double ny = offsetY + nr * (3.0 / 4.0) * hexH;
                                g2.drawLine((int) cx, (int) cy, (int) nx, (int) ny);
                                cx = nx; cy = ny;
                                curPos = nb.pos;
                                break;
                            }
                        }
                    }
                }
            }
            g2.setStroke(new BasicStroke(1));
        }

        for (JSONObject ag : agents) {
            int pos = ag.has("pos") ? ag.getInt("pos") : ag.getInt("cell");
            String aid = ag.has("agent_id") ? String.valueOf(ag.get("agent_id")) : "?";
            String type = ag.optString("type", "patrol");

            int r = pos / grid.width, c = pos % grid.width;
            double cx = offsetX + c * hexW - (r % 2 != 0 ? hexW / 2.0 : 0);
            double cy = offsetY + r * (3.0 / 4.0) * hexH;

            g2.setColor("patrol".equals(type) ? Color.BLUE : Color.MAGENTA);
            g2.fillOval((int) cx - 10, (int) cy - 10, 20, 20);
            g2.setColor(Color.WHITE);
            g2.drawOval((int) cx - 10, (int) cy - 10, 20, 20);

            g2.setFont(new Font("Arial", Font.BOLD, 12));
            g2.drawString(aid, (int) cx - 3, (int) cy + 4);
        }
    }
}