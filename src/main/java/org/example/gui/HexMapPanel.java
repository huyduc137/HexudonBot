package org.example.gui;

import org.example.grid.HexGrid;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * HexMapPanel — khung vẽ bản đồ lục giác + spot + agent + đường đi dự kiến.
 * Trước đây là {@code private static} inner class bên trong HexudonGUI (~140 dòng),
 * tách ra file riêng vì:
 *   1. Không phụ thuộc trạng thái của HexudonGUI (chỉ nhận dữ liệu qua setMapData) -> tái sử dụng được.
 *   2. Dễ viết unit test / preview riêng cho phần vẽ mà không cần dựng cả cửa sổ điều khiển.
 *
 * TODO (cải tiến UI, chưa làm):
 *   - Hiện chưa vẽ trạng thái đường bộ (SMOOTH/CONGESTED/JAM) bằng màu khác nhau cho ô Road
 *     -> nên tô màu theo grid.roadCond để người chơi thấy tắc đường trực quan.
 *   - Chưa vẽ vị trí agent của ĐỘI ĐỐI THỦ (dữ liệu có trong state["others"]) -> hữu ích để
 *     quan sát tranh chấp điểm đến.
 *   - Chưa hiển thị "chain đã ăn cả trận" (brandsSeen) trực quan trên các spot cùng chain.
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

    /** Cho phép GUI đọc lại danh sách agent hiện đang hiển thị (dùng khi chỉ cập nhật plannedActions). */
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

        // 1. Vẽ các ô bản đồ lục giác
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

                if (terrain == HexGrid.PLAIN) g2.setColor(new Color(200, 240, 200));
                else if (terrain == HexGrid.ROAD) g2.setColor(new Color(220, 220, 220));
                else if (terrain == HexGrid.MOUNTAIN) g2.setColor(new Color(160, 100, 50));
                else if (terrain == HexGrid.POND) g2.setColor(new Color(100, 150, 250));
                else g2.setColor(Color.WHITE);

                g2.fillPolygon(poly);
                g2.setColor(Color.GRAY);
                g2.drawPolygon(poly);

                g2.setColor(new Color(150, 150, 150));
                g2.setFont(new Font("Arial", Font.PLAIN, 9));
                g2.drawString(String.valueOf(pos), (int) cx - 6, (int) cy + 3);
            }
        }

        // 2. Vẽ các Điểm bán Udon (Spots)
        for (JSONObject spot : spots) {
            int pos = spot.getInt("pos");
            int r = pos / grid.width, c = pos % grid.width;
            double cx = offsetX + c * hexW - (r % 2 != 0 ? hexW / 2.0 : 0);
            double cy = offsetY + r * (3.0 / 4.0) * hexH;

            g2.setColor(new Color(180, 0, 0));
            g2.setFont(new Font("Arial", Font.BOLD, 11));
            g2.drawString("B" + spot.getInt("brand") + " (S" + spot.getInt("stocks") + ")", (int) cx - 18, (int) cy - 5);
        }

        // 3. Vẽ đường đi dự kiến (Planned paths)
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

        // 4. Vẽ các Xe (Agents)
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
