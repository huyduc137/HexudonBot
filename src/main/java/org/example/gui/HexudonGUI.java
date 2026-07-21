package org.example.gui;

import org.example.client.HexudonClient;
import org.example.engine.AgentRoleAssigner;
import org.example.engine.PlanValidator;
import org.example.grid.HexGrid;
import org.example.planner.DayPlanner;
import org.example.planner.LazyGreedyPlanner;
import org.example.planner.OrienteeringPlanner;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * HexudonGUI — cửa sổ điều khiển thủ công theo từng ngày (kết nối, chọn vai trò,
 * lập kế hoạch, xem trước trên bản đồ, sửa tay JSON hành động, gửi).
 *
 * Đã tách khỏi file gốc (~615 dòng, 1 file):
 *   - {@link HexMapPanel}   -> phần vẽ bản đồ (không còn là inner class).
 *   - {@link AgentRoleAssigner} (org.example.engine) -> logic auto-assign vai trò,
 *     dùng chung với HexudonEngine (trước đây bị copy-paste 2 nơi).
 *
 * TODO (chưa làm, xem mô tả chi tiết trong từng file stub tương ứng):
 *   - {@link ConnectionPanel}, {@link AgentRolesPanel}, {@link DailyControlPanel}:
 *     tách tiếp 3 "GROUP" UI hiện đang xây dựng trực tiếp trong constructor (~160 dòng)
 *     thành các JPanel con độc lập, để constructor của HexudonGUI chỉ còn việc "lắp ráp".
 *   - Hiện luôn dùng {@link LazyGreedyPlanner} cứng; khi OrienteeringPlanner xong,
 *     nên thêm 1 JComboBox chọn chiến lược (planner) ngay trong Group 3.
 */
public class HexudonGUI extends JFrame {

    private static final String DEFAULT_URL = "https://hexudon.hairbui76.id.vn";
    private static final String DEFAULT_GAME_ID = "a759fcca-5de2-4fe6-9dff-1a5a3ba33fb5";
    private static final String DEFAULT_TEAM_ID = "21";
    private static final String DEFAULT_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MjEsIm5hbWUiOiJUTEUgU3BlZWRydW4iLCJpc19hZG1pbiI6ZmFsc2UsImlhdCI6MTc4NDU2NTMyNSwiZXhwIjoxNzg0NzM4MTI1fQ.vEWbiFCoNcaIkseS0fH-ebyPY7hK5Mlj9-Dccv5sUpo";

    // UI Components
    private JTextField inputUrl, inputGameId, inputTeamId;
    private JPasswordField inputToken;
    private JCheckBox chkPractice;
    private JButton btnConnect, btnAutoRoles, btnSubmitRoles, btnFetchDay, btnPlanDay, btnSubmitDay;
    private JPanel agentsContainer;
    private List<JComboBox<String>> agentRoleCombos = new ArrayList<>();
    private JLabel lblDay, lblStatus;
    private JTextArea logArea, planJsonArea;
    private HexMapPanel mapPanel;

    // Game State
    private HexudonClient client;
    private HexGrid grid;
    private final List<JSONObject> spots = new ArrayList<>();
    private final List<Integer> dayStepsList = new ArrayList<>();
    private final List<Integer> agentsStarts = new ArrayList<>();
    private final Map<String, String> typeAssignments = new HashMap<>();
    private int currentDay = 0;
    private JSONObject currentState;
    private int nAgents = 0;
    private int totalSteps = 0;
    private int fuelLimits = 100;

    /** Chiến lược lập kế hoạch đang dùng cho nút "Tự động Lập kế hoạch". */
    private final DayPlanner planner = new OrienteeringPlanner();

    public HexudonGUI() {
        setTitle("HEXUDON Java Engine - Procon 2026 GUI");
        setSize(1150, 780);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setPreferredSize(new Dimension(390, 720));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 5));

        // --- GROUP 1: Config ---
        // TODO: tách thành ConnectionPanel riêng (xem docs/architecture.md)
        JPanel configGroup = new JPanel(new GridLayout(6, 2, 5, 5));
        configGroup.setBorder(new TitledBorder("1. Cấu hình kết nối"));

        configGroup.add(new JLabel("Server URL:"));
        inputUrl = new JTextField(DEFAULT_URL);
        configGroup.add(inputUrl);

        configGroup.add(new JLabel("Game ID:"));
        inputGameId = new JTextField(DEFAULT_GAME_ID);
        configGroup.add(inputGameId);

        configGroup.add(new JLabel("Team ID:"));
        inputTeamId = new JTextField(DEFAULT_TEAM_ID);
        configGroup.add(inputTeamId);

        configGroup.add(new JLabel("Token (JWT):"));
        inputToken = new JPasswordField(DEFAULT_TOKEN);
        configGroup.add(inputToken);

        configGroup.add(new JLabel("Chế độ chơi:"));
        chkPractice = new JCheckBox("Luyện tập (Practice Mode)", true);
        configGroup.add(chkPractice);

        btnConnect = new JButton("Kết nối & Tải Bản đồ");
        btnConnect.setBackground(new Color(46, 204, 113));
        btnConnect.setForeground(Color.WHITE);
        btnConnect.addActionListener(e -> cmdConnect());
        configGroup.add(new JLabel(""));
        configGroup.add(btnConnect);
        leftPanel.add(configGroup);

        // --- GROUP 2: Agent Roles ---
        // TODO: tách thành AgentRolesPanel riêng
        JPanel rolesGroup = new JPanel(new BorderLayout(5, 5));
        rolesGroup.setBorder(new TitledBorder("2. Chỉ định vai trò Agent"));

        JPanel rolesBtnPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        btnAutoRoles = new JButton("Tự động gán");
        btnAutoRoles.setEnabled(false);
        btnAutoRoles.addActionListener(e -> cmdAutoRoles());

        btnSubmitRoles = new JButton("Chốt & Gửi vai trò");
        btnSubmitRoles.setBackground(new Color(230, 126, 34));
        btnSubmitRoles.setForeground(Color.WHITE);
        btnSubmitRoles.setEnabled(false);
        btnSubmitRoles.addActionListener(e -> cmdSubmitRoles());

        rolesBtnPanel.add(btnAutoRoles);
        rolesBtnPanel.add(btnSubmitRoles);
        rolesGroup.add(rolesBtnPanel, BorderLayout.NORTH);

        agentsContainer = new JPanel();
        agentsContainer.setLayout(new BoxLayout(agentsContainer, BoxLayout.Y_AXIS));
        JScrollPane rolesScroll = new JScrollPane(agentsContainer);
        rolesScroll.setPreferredSize(new Dimension(360, 110));
        rolesGroup.add(rolesScroll, BorderLayout.CENTER);

        leftPanel.add(Box.createVerticalStrut(8));
        leftPanel.add(rolesGroup);

        // --- GROUP 3: Daily Execution ---
        // TODO: tách thành DailyControlPanel riêng
        JPanel dailyGroup = new JPanel();
        dailyGroup.setLayout(new BoxLayout(dailyGroup, BoxLayout.Y_AXIS));
        dailyGroup.setBorder(new TitledBorder("3. Điều khiển lượt thi đấu (Từng ngày)"));

        lblDay = new JLabel("Ngày: -");
        lblDay.setFont(new Font("Arial", Font.BOLD, 14));
        dailyGroup.add(lblDay);
        dailyGroup.add(Box.createVerticalStrut(5));

        btnFetchDay = new JButton("1. Lấy trạng thái Ngày hiện tại");
        btnFetchDay.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnFetchDay.setEnabled(false);
        btnFetchDay.addActionListener(e -> cmdFetchDay());
        dailyGroup.add(btnFetchDay);
        dailyGroup.add(Box.createVerticalStrut(5));

        btnPlanDay = new JButton("2. Tự động Lập kế hoạch (AI SA)");
        btnPlanDay.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnPlanDay.setEnabled(false);
        btnPlanDay.addActionListener(e -> cmdPlanDay());
        dailyGroup.add(btnPlanDay);
        dailyGroup.add(Box.createVerticalStrut(5));

        planJsonArea = new JTextArea(4, 20);
        planJsonArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        planJsonArea.setBorder(BorderFactory.createTitledBorder("JSON Hành động (Có thể sửa tay):"));
        dailyGroup.add(new JScrollPane(planJsonArea));
        dailyGroup.add(Box.createVerticalStrut(5));

        btnSubmitDay = new JButton("3. Gửi Lộ trình đi (Submit Plan)");
        btnSubmitDay.setBackground(new Color(52, 152, 219));
        btnSubmitDay.setForeground(Color.WHITE);
        btnSubmitDay.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnSubmitDay.setEnabled(false);
        btnSubmitDay.addActionListener(e -> cmdSubmitDay());
        dailyGroup.add(btnSubmitDay);

        leftPanel.add(Box.createVerticalStrut(8));
        leftPanel.add(dailyGroup);

        // --- GROUP 4: Log ---
        JPanel logGroup = new JPanel(new BorderLayout());
        logGroup.setBorder(new TitledBorder("4. Log Console"));
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logGroup.add(new JScrollPane(logArea), BorderLayout.CENTER);

        leftPanel.add(Box.createVerticalStrut(8));
        leftPanel.add(logGroup);

        add(leftPanel, BorderLayout.WEST);

        mapPanel = new HexMapPanel();
        mapPanel.setBorder(BorderFactory.createTitledBorder("Bản đồ trận đấu"));
        add(mapPanel, BorderLayout.CENTER);

        lblStatus = new JLabel(" Sẵn sàng. Vui lòng kiểm tra cấu hình và nhấn Kết nối.");
        lblStatus.setBorder(BorderFactory.createEtchedBorder());
        add(lblStatus, BorderLayout.SOUTH);
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void buildAgentRolesUI() {
        agentsContainer.removeAll();
        agentRoleCombos.clear();
        for (int i = 0; i < nAgents; i++) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
            row.add(new JLabel("Agent " + i + ":"));
            JComboBox<String> cb = new JComboBox<>(new String[]{"patrol", "refuel"});
            row.add(cb);
            agentsContainer.add(row);
            agentRoleCombos.add(cb);
        }
        agentsContainer.revalidate();
        agentsContainer.repaint();
    }

    // --- CÁC HÀM XỬ LÝ SỰ KIỆN ---

    private void cmdConnect() {
        String url = inputUrl.getText().trim();
        String gameId = inputGameId.getText().trim();
        String teamId = inputTeamId.getText().trim();
        String token = new String(inputToken.getPassword()).trim();

        if (gameId.isEmpty() || token.isEmpty() || "YOUR_GAME_ID_HERE".equals(gameId)) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ Game ID và Token hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        btnConnect.setEnabled(false);
        lblStatus.setText(" Đang kết nối tới máy chủ...");
        log("Đang kết nối tới: " + url);

        new Thread(() -> {
            try {
                org.example.util.MatchAuditLogger.clear();
                client = new HexudonClient(url, gameId, teamId, token);
                JSONObject mapResp = client.getMap();
                grid = HexGrid.fromMapResponse(mapResp);

                spots.clear();
                JSONArray spotsArr = mapResp.optJSONArray("spots");
                if (spotsArr != null) for (int i = 0; i < spotsArr.length(); i++) spots.add(spotsArr.getJSONObject(i));

                dayStepsList.clear();
                JSONArray stepsArr = mapResp.optJSONArray("daySteps");
                if (stepsArr != null) for (int i = 0; i < stepsArr.length(); i++) dayStepsList.add(stepsArr.getInt(i));

                agentsStarts.clear();
                JSONArray agentsArr = mapResp.optJSONArray("agents");
                nAgents = (agentsArr != null) ? agentsArr.length() : 0;
                if (agentsArr != null) for (int i = 0; i < agentsArr.length(); i++) agentsStarts.add(agentsArr.getInt(i));

                totalSteps = dayStepsList.stream().mapToInt(Integer::intValue).sum();
                fuelLimits = mapResp.optInt("fuelLimits", 100);

                log("Kết nối thành công! Bản đồ: " + grid.width + "x" + grid.height + ", Số Agent: " + nAgents);

                SwingUtilities.invokeLater(() -> {
                    buildAgentRolesUI();
                    cmdAutoRoles();
                    btnAutoRoles.setEnabled(true);
                    btnSubmitRoles.setEnabled(true);
                    mapPanel.setMapData(grid, spots, new ArrayList<>(), new ArrayList<>());
                    lblStatus.setText(" Tải bản đồ thành công! Vui lòng chọn vai trò Agent và nhấn 'Chốt & Gửi'.");
                });
            } catch (Exception e) {
                log("Lỗi kết nối: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    btnConnect.setEnabled(true);
                    lblStatus.setText(" Lỗi kết nối. Vui lòng kiểm tra lại Log.");
                });
            }
        }).start();
    }

    private void cmdAutoRoles() {
        // CẬP NHẬT PHASE 0: Truyền dayStepsList và spots vào bộ phân giao vai trò
        List<JSONObject> suggested = AgentRoleAssigner.autoAssign(
                nAgents, fuelLimits, dayStepsList, agentsStarts, grid, spots
        );
        for (int i = 0; i < nAgents; i++) {
            agentRoleCombos.get(i).setSelectedItem(suggested.get(i).getString("type"));
        }
        log("Đã tính toán xong vai trò gợi ý theo K-Means (Phase 0 Auto Assign).");
    }

    private void cmdSubmitRoles() {
        btnSubmitRoles.setEnabled(false);
        btnAutoRoles.setEnabled(false);
        lblStatus.setText(" Đang gửi đăng ký vai trò xe...");

        new Thread(() -> {
            List<JSONObject> assignments = new ArrayList<>();
            for (int i = 0; i < nAgents; i++) {
                String aid = String.valueOf(i);
                String type = (String) agentRoleCombos.get(i).getSelectedItem();
                typeAssignments.put(aid, type);
                JSONObject obj = new JSONObject();
                obj.put("agent_id", aid);
                obj.put("type", type);
                assignments.add(obj);
            }

            try {
                client.selectAgentTypes(assignments);
                log("Đã đăng ký loại xe thành công!");
            } catch (Exception ex) {
                log("Cảnh báo đăng ký xe: Trận đấu đã bắt đầu hoặc loại xe đã được chốt từ trước.");
            }

            currentDay = 0;
            SwingUtilities.invokeLater(() -> {
                lblStatus.setText(" Chốt vai trò thành công! Sẵn sàng chơi thi đấu.");
                btnFetchDay.setEnabled(true);
                btnPlanDay.setEnabled(true);
                btnSubmitDay.setEnabled(true);
                cmdFetchDay();
            });
        }).start();
    }

    private void cmdFetchDay() {
        lblStatus.setText(" Đang tải thông tin Ngày " + currentDay + " và trạng thái Giao thông...");
        new Thread(() -> {
            try {
                // NÂNG CẤP PHASE 1: Lấy thêm getDayInfo() để cập nhật Giao thông và Deadline
                JSONObject dayInfo = client.getDayInfo();
                currentState = client.getState();
                currentDay = currentState.optInt("day", currentDay);

                // Cập nhật đường bộ vào lưới HexGrid
                JSONArray traffics = dayInfo.optJSONArray("traffics");
                if (traffics == null) traffics = currentState.optJSONArray("traffics");
                grid.updateTraffic(traffics);

                long endsAt = dayInfo.optLong("endsAt", 0);
                long timeLeftSec = (endsAt > 0) ? (endsAt - (System.currentTimeMillis() / 1000L)) : 0;

                JSONObject teams = currentState.getJSONObject("teams");
                JSONObject teamData = teams.optJSONObject(client.teamId);
                if (teamData == null) teamData = teams.optJSONObject(String.valueOf(client.teamId));
                if (teamData == null) teamData = new JSONObject();

                JSONArray rawAgents = teamData.optJSONArray("agents");
                List<JSONObject> agents = new ArrayList<>();
                if (rawAgents != null) {
                    for (int i = 0; i < rawAgents.length(); i++) {
                        JSONObject ag = rawAgents.getJSONObject(i);
                        String aid = ag.has("agent_id") ? String.valueOf(ag.get("agent_id")) : String.valueOf(i);
                        ag.put("type", typeAssignments.getOrDefault(aid, "patrol"));
                        agents.add(ag);
                    }
                }

                String timeMsg = (timeLeftSec > 0) ? (" (Deadline còn: " + timeLeftSec + "s)") : "";
                log("Đã tải Ngày " + currentDay + timeMsg + ". Đã cập nhật trạng thái tắc đường trên HexGrid.");
                SwingUtilities.invokeLater(() -> {
                    lblDay.setText("Ngày: " + currentDay + " / " + (dayStepsList.size() - 1) + timeMsg);
                    mapPanel.setMapData(grid, spots, agents, new ArrayList<>());
                    lblStatus.setText(" Ngày " + currentDay + " sẵn sàng. Nhấn 'Tự động Lập kế hoạch' để tiếp tục.");
                });
            } catch (Exception e) {
                log("Lỗi khi tải ngày: " + e.getMessage());
            }
        }).start();
    }

    private void cmdPlanDay() {
        if (currentState == null || grid == null) return;
        lblStatus.setText(" Đang tính toán đường đi...");
        new Thread(() -> {
            try {
                int daySteps = (currentDay < dayStepsList.size()) ? dayStepsList.get(currentDay) : currentState.optInt("steps_today", 50);

                JSONObject teams = currentState.getJSONObject("teams");
                JSONObject teamData = teams.optJSONObject(client.teamId);
                if (teamData == null) teamData = teams.optJSONObject(String.valueOf(client.teamId));
                if (teamData == null) teamData = new JSONObject();

                JSONArray rawAgents = teamData.optJSONArray("agents");
                List<JSONObject> patrolAgents = new ArrayList<>();
                List<JSONObject> refuelAgents = new ArrayList<>();

                if (rawAgents != null) {
                    for (int i = 0; i < rawAgents.length(); i++) {
                        JSONObject ag = rawAgents.getJSONObject(i);
                        String aid = ag.has("agent_id") ? String.valueOf(ag.get("agent_id")) : String.valueOf(i);
                        int pos = ag.has("pos") ? ag.getInt("pos") : ag.getInt("cell");
                        int fuel = ag.optInt("fuel", 0);

                        boolean isPatrol = "patrol".equals(typeAssignments.getOrDefault(aid, "patrol"));
                        JSONObject entry = new JSONObject();
                        entry.put("agent_id", aid);
                        entry.put("pos", pos);
                        entry.put("fuel", fuel);
                        entry.put("fuelLimits", fuelLimits);

                        (isPatrol ? patrolAgents : refuelAgents).add(entry);
                    }
                }

                Set<Integer> brandsSeen = new HashSet<>();
                JSONArray brandsArr = teamData.optJSONArray("distinct_types");
                if (brandsArr != null) for (int i = 0; i < brandsArr.length(); i++) brandsSeen.add(brandsArr.getInt(i));

                // NÂNG CẤP PHASE 1: Tính ngân sách thời gian để truyền vào strategy
                // NÂNG CẤP: Tính ngân sách thời gian để truyền vào strategy
                long endsAtSec = client.getDayInfo().optLong("endsAt", 0);
                long nowSec = System.currentTimeMillis() / 1000L;
                long timeAvailableMs;

                if (chkPractice.isSelected()) {
                    // NẾU LÀ CHẾ ĐỘ PRACTICE: Ép buộc AI phải suy nghĩ đúng 3 giây (3000ms)
                    timeAvailableMs = 5000L;
                } else if (endsAtSec > nowSec) {
                    // THI ĐẤU THẬT: Trừ hao 1.5 giây trễ mạng
                    timeAvailableMs = ((endsAtSec - nowSec) * 1000L) - 1500L;
                } else {
                    timeAvailableMs = 3000L;
                }

                String strategy = "normal|timeLimit=" + timeAvailableMs;

                Map<String, List<Integer>> planned =
                        planner.plan(grid, patrolAgents, refuelAgents, spots, daySteps, brandsSeen, currentDay, strategy);

                Map<String, Integer> startPosMap = new HashMap<>();
                for (JSONObject ag : patrolAgents) startPosMap.put(ag.getString("agent_id"), org.example.util.JsonUtil.getAgentPos(ag));
                for (JSONObject ag : refuelAgents) startPosMap.put(ag.getString("agent_id"), org.example.util.JsonUtil.getAgentPos(ag));

                PlanValidator.ValidationResult valRes = PlanValidator.validateAndNormalize(grid, planned, startPosMap, daySteps);

                if (!valRes.valid) {
                    log("-> [CẢNH BÁO SANDBOX KHẨN] Kế hoạch AI bị lỗi: " + valRes.reason);
                    log("-> [ROLLBACK] Kích hoạt chạy lại LazyGreedyPlanner để cứu Ngày " + currentDay + "!");

                    DayPlanner fallback = new LazyGreedyPlanner();
                    planned = fallback.plan(grid, patrolAgents, refuelAgents, spots, daySteps, brandsSeen, currentDay, "fallback");
                    valRes = PlanValidator.validateAndNormalize(grid, planned, startPosMap, daySteps);
                }

                List<List<Integer>> actions = new ArrayList<>();
                JSONArray jsonActions = new JSONArray();
                for (int i = 0; i < nAgents; i++) {
                    String aid = String.valueOf(i);
                    List<Integer> act = valRes.normalizedActions.getOrDefault(aid, Collections.singletonList(-daySteps));
                    actions.add(act);
                    jsonActions.put(new JSONArray(act));
                }

                log("Đã lập kế hoạch Ngày " + currentDay + " (Ngân sách AI: " + timeAvailableMs + "ms)!");
                SwingUtilities.invokeLater(() -> {
                    planJsonArea.setText(jsonActions.toString(2));
                    mapPanel.setMapData(grid, spots, mapPanel.getAgents(), actions);
                    lblStatus.setText(" Kế hoạch đã sẵn sàng. Bạn có thể xem đường dây trên bản đồ và nhấn Gửi.");
                });
            } catch (Exception e) {
                log("Lỗi lập kế hoạch: " + e.getMessage());
            }
        }).start();
    }

    private void cmdSubmitDay() {
        String jsonStr = planJsonArea.getText().trim();
        if (jsonStr.isEmpty()) return;

        boolean isPractice = chkPractice.isSelected();
        lblStatus.setText(" Đang gửi lộ trình đi cho Ngày " + currentDay + (isPractice ? " (Chế độ Practice)" : " (Chế độ Active)") + "...");

        new Thread(() -> {
            try {
                JSONArray jsonActions = new JSONArray(jsonStr);
                List<List<Integer>> actions = new ArrayList<>();
                for (int i = 0; i < jsonActions.length(); i++) {
                    JSONArray arr = jsonActions.getJSONArray(i);
                    List<Integer> list = new ArrayList<>();
                    for (int j = 0; j < arr.length(); j++) list.add(arr.getInt(j));
                    actions.add(list);
                }

                JSONObject res = isPractice ? client.submitPractice(currentDay, actions) : client.submitActions(currentDay, actions);
                log("Gửi thành công Ngày " + currentDay + "! Máy chủ trả về: " + res.toString());

                currentDay++;
                SwingUtilities.invokeLater(() -> {
                    planJsonArea.setText("");
                    if (currentDay < dayStepsList.size()) {
                        cmdFetchDay();
                    } else {
                        log("TRẬN ĐẤU ĐÃ HOÀN TẤT ALL DAYS!");
                        lblStatus.setText(" Trận đấu kết thúc! Chúc mừng bạn hoàn thành.");
                        String finalReport = org.example.util.MatchAuditLogger.generateFinalReport(
                                inputGameId.getText().trim(), inputTeamId.getText().trim()
                        );
                        log(finalReport);
                    }
                });
            } catch (Exception e) {
                log("Lỗi gửi hành động: " + e.getMessage());
                SwingUtilities.invokeLater(() -> lblStatus.setText(" Lỗi khi gửi lộ trình. Kiểm tra Log."));
            }
        }).start();
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            HexudonGUI gui = new HexudonGUI();
            gui.setVisible(true);
        });
    }
}
