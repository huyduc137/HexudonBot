package org.example.planner;

import org.example.grid.HexGrid;
import org.example.grid.Pathfinder;
import org.example.util.JsonUtil;
import org.json.JSONObject;

import java.util.*;

/**
 * OrienteeringPlanner — Bộ não AI cốt lõi cho thi đấu thật (ĐÃ NÂNG CẤP GIAI ĐOẠN 1).
 *
 * Nâng cấp đột phá:
 * 1. Lexicographic Scoring: Đánh giá nghiệm theo chuẩn từ điển (cGlobal -> cDaily -> uCount).
 * 2. Event-Driven Simulation: Sắp xếp sự kiện thăm ô theo trục thời gian thực (Step ascending),
 *    giúp giải quyết triệt để sự bất công khi nhiều xe cùng tranh chấp một Quán Udon ít kho.
 */
public class OrienteeringPlanner implements DayPlanner {

    /**
     * Cấu trúc điểm số từ điển (Lexicographic Score), chuẩn hóa theo đúng thứ tự ưu tiên của BTC.
     */
    public static class LexicographicScore implements Comparable<LexicographicScore> {
        public final long cGlobal;
        public final long cDaily;
        public final long uCount;

        public LexicographicScore(long cGlobal, long cDaily, long uCount) {
            this.cGlobal = cGlobal;
            this.cDaily = cDaily;
            this.uCount = uCount;
        }

        @Override
        public int compareTo(LexicographicScore o) {
            if (this.cGlobal != o.cGlobal) return Long.compare(this.cGlobal, o.cGlobal);
            if (this.cDaily != o.cDaily) return Long.compare(this.cDaily, o.cDaily);
            return Long.compare(this.uCount, o.uCount);
        }

        public long toLegacyZ() {
            return (10000L * cGlobal) + (100L * cDaily) + (1L * uCount);
        }

        @Override
        public String toString() {
            return String.format("[Global: %d | Daily: %d | Udon: %d (Z=%,d)]", cGlobal, cDaily, uCount, toLegacyZ());
        }
    }

    private static class SimResult {
        LexicographicScore score;
        Map<String, List<Integer>> actions;
        SimResult(LexicographicScore score, Map<String, List<Integer>> actions) {
            this.score = score;
            this.actions = actions;
        }
    }

    /**
     * Sự kiện thăm ô trên dòng thời gian để mô phỏng theo thứ tự thực tế.
     */
    private static class VisitEvent implements Comparable<VisitEvent> {
        final int step;
        final int pos;
        final String agentId;

        VisitEvent(int step, int pos, String agentId) {
            this.step = step;
            this.pos = pos;
            this.agentId = agentId;
        }

        @Override
        public int compareTo(VisitEvent o) {
            if (this.step != o.step) return Integer.compare(this.step, o.step);
            return this.agentId.compareTo(o.agentId); // Tie-break theo ID xe
        }
    }

    @Override
    public Map<String, List<Integer>> plan(
            HexGrid grid,
            List<JSONObject> patrolAgents,
            List<JSONObject> refuelAgents,
            List<JSONObject> spots,
            int daySteps,
            Set<Integer> brandsSeen,
            int currentDay,
            String strategy) {

        long timeLimitMs = 3000L;
        if (strategy != null && strategy.contains("timeLimit=")) {
            try {
                String val = strategy.substring(strategy.indexOf("timeLimit=") + 10);
                if (val.contains("|")) val = val.substring(0, val.indexOf("|"));
                timeLimitMs = Math.max(300L, Long.parseLong(val) - 300L); // Chừa 300ms an toàn
            } catch (Exception ignored) {}
        }
        long endTime = System.currentTimeMillis() + timeLimitMs;

        Map<Integer, JSONObject> activeSpots = new HashMap<>();
        for (JSONObject s : spots) {
            if (s.optInt("stocks", 1) > 0) {
                activeSpots.put(s.getInt("pos"), s);
            }
        }

        if (activeSpots.isEmpty() || patrolAgents.isEmpty()) {
            return buildAllWait(patrolAgents, refuelAgents, daySteps);
        }

        Map<String, List<Integer>> currentTargets = buildInitialTargets(grid, patrolAgents, activeSpots, brandsSeen);
        SimResult bestSim = simulateAndScore(grid, patrolAgents, refuelAgents, activeSpots, daySteps, brandsSeen, currentTargets, currentDay, false);
        LexicographicScore currentScore = bestSim.score;
        Map<String, List<Integer>> bestTargets = currentTargets;

        Random rand = new Random();
        List<String> patrolIds = new ArrayList<>(currentTargets.keySet());
        List<Integer> allSpotPositions = new ArrayList<>(activeSpots.keySet());
        int iterations = 0;

        while (System.currentTimeMillis() < endTime) {
            iterations++;
            Map<String, List<Integer>> candidateTargets = new HashMap<>();
            for (String aid : patrolIds) {
                candidateTargets.put(aid, new ArrayList<>(currentTargets.get(aid)));
            }

            String targetAid = patrolIds.get(rand.nextInt(patrolIds.size()));
            List<Integer> list = candidateTargets.get(targetAid);

            int op = rand.nextInt(3);
            if (op == 0 && !list.isEmpty()) {
                int idx1 = rand.nextInt(list.size());
                int idx2 = rand.nextInt(list.size());
                Collections.swap(list, idx1, idx2);
            } else if (op == 1 && !allSpotPositions.isEmpty()) {
                int newSpot = allSpotPositions.get(rand.nextInt(allSpotPositions.size()));
                if (!list.contains(newSpot)) {
                    int posIdx = list.isEmpty() ? 0 : rand.nextInt(list.size() + 1);
                    list.add(posIdx, newSpot);
                }
            } else if (op == 2 && !list.isEmpty()) {
                list.remove(rand.nextInt(list.size()));
            }

            SimResult candSim = simulateAndScore(grid, patrolAgents, refuelAgents, activeSpots, daySteps, brandsSeen, candidateTargets, currentDay, false);

            // So sánh chuẩn Từ điển: candSim.score > currentScore
            if (candSim.score.compareTo(currentScore) > 0) {
                currentScore = candSim.score;
                currentTargets = candidateTargets;
                if (candSim.score.compareTo(bestSim.score) > 0) {
                    bestSim = candSim;
                    bestTargets = candidateTargets;
                }
            }
        }

        System.out.println("-> [PHASE 2 SA] Đã hoàn tất " + iterations + " vòng lặp mô phỏng. Xuất báo cáo sự kiện dòng thời gian...");
        SimResult finalLoggedSim = simulateAndScore(grid, patrolAgents, refuelAgents, activeSpots, daySteps, brandsSeen, bestTargets, currentDay, true);

        return finalLoggedSim.actions;
    }

    /**
     * Mô phỏng theo Dòng sự kiện (Event-Driven Simulation) và Đánh giá Từ điển.
     */
    private SimResult simulateAndScore(
            HexGrid grid,
            List<JSONObject> patrolAgents,
            List<JSONObject> refuelAgents,
            Map<Integer, JSONObject> activeSpots,
            int daySteps,
            Set<Integer> initialBrandsSeen,
            Map<String, List<Integer>> targetSequences,
            int currentDay,
            boolean verbose) {

        Map<String, List<Integer>> actions = new HashMap<>();
        Map<String, Integer> pPos = new HashMap<>();
        Map<String, Integer> pFuel = new HashMap<>();
        Map<String, Integer> pStepsUsed = new HashMap<>();

        for (JSONObject ag : patrolAgents) {
            String aid = ag.getString("agent_id");
            pPos.put(aid, JsonUtil.getAgentPos(ag));
            pFuel.put(aid, ag.optInt("fuel", 0));
            pStepsUsed.put(aid, 0);
            actions.put(aid, new ArrayList<>());
        }

        Map<String, Integer> rPos = new HashMap<>();
        Map<String, Integer> rStepsUsed = new HashMap<>();
        for (JSONObject ag : refuelAgents) {
            String aid = ag.getString("agent_id");
            rPos.put(aid, JsonUtil.getAgentPos(ag));
            rStepsUsed.put(aid, 0);
            actions.put(aid, new ArrayList<>());
        }

        int fuelLimit = patrolAgents.isEmpty() ? 100 : patrolAgents.get(0).optInt("fuelLimits", 100);
        StringBuilder logBuilder = new StringBuilder();

        if (verbose) {
            logBuilder.append("\n========================================================================================\n");
            logBuilder.append("    [BÁO CÁO MÔ PHỎNG DÒNG THỜI GIAN SỰ KIỆN NGÀY ").append(currentDay).append(" - GIAI ĐOẠN 1]\n");
            logBuilder.append("========================================================================================\n");
        }

        // BƯỚC 1: Lập quỹ đạo di chuyển và điều phối tiếp xăng cho từng xe (Chưa trừ Udon)
        List<VisitEvent> timeline = new ArrayList<>();

        for (JSONObject ag : patrolAgents) {
            String aid = ag.getString("agent_id");
            List<Integer> targets = targetSequences.getOrDefault(aid, Collections.emptyList());
            int curPos = pPos.get(aid);
            int curFuel = pFuel.get(aid);
            int curStep = 0;
            List<Integer> myActions = actions.get(aid);

            // Ghi nhận sự kiện xuất phát tại ô hiện tại lúc step = 0
            timeline.add(new VisitEvent(0, curPos, aid));

            if (verbose) {
                logBuilder.append("🚗 AGENT ").append(aid).append(" (Xuất phát: ô ").append(curPos).append(" | Xăng: ").append(curFuel).append(")\n");
            }

            for (int targetPos : targets) {
                if (curStep >= daySteps) break;
                if (curPos == targetPos) continue;

                Pathfinder.DijkstraResult dr = Pathfinder.dijkstraFrom(grid, curPos, 9999, false);
                if (!dr.distSteps.containsKey(targetPos)) continue;

                int reqSteps = dr.distSteps.get(targetPos);
                int reqFuel = dr.distFuel.get(targetPos);

                if (curStep + reqSteps > daySteps) break;

                // CẢNH BÁO XĂNG VÀ ĐIỀU ĐỘNG TIẾP XĂNG
                if (curFuel < reqFuel) {
                    RefuelCoordinator.RendezvousPlan plan = RefuelCoordinator.findRendezvous(
                            grid, curPos, curStep, rPos, rStepsUsed, daySteps
                    );

                    if (plan != null) {
                        String rId = plan.refuelAgentId;
                        List<Integer> rActions = actions.get(rId);
                        rActions.addAll(plan.refuelPathDirs);
                        if (plan.refuelStepsUsedAfter < curStep) {
                            rActions.add(-(curStep - plan.refuelStepsUsedAfter));
                        }
                        if (verbose) {
                            logBuilder.append("   🆘 Cạn xăng tại ô ").append(curPos).append(" (Bước ").append(curStep)
                                    .append("). Đã điều Refueler ").append(rId).append(" chạy tới cứu hộ!\n");
                        }
                        rPos.put(rId, curPos);
                        rStepsUsed.put(rId, curStep);
                        curFuel = fuelLimit;
                    } else {
                        break; // Hết xăng, không ai cứu -> Dừng chặng
                    }
                }

                List<Integer> pathDirs = Pathfinder.reconstructPath(dr.prev, curPos, targetPos);
                myActions.addAll(pathDirs);

                // Ghi nhận từng bước di chuyển vào Dòng thời gian sự kiện
                for (int d : pathDirs) {
                    int stepCost = grid.travelSteps(curPos);
                    int fuelCost = grid.fuelCost(curPos);
                    curStep += stepCost;
                    curFuel -= fuelCost;

                    for (HexGrid.Neighbor nb : grid.neighbors(curPos)) {
                        if (nb.direction == d) {
                            curPos = nb.pos;
                            break;
                        }
                    }
                    timeline.add(new VisitEvent(curStep, curPos, aid));
                }
            }

            if (curStep < daySteps) {
                myActions.add(-(daySteps - curStep));
            }
        }

        // Bổ sung lệnh chờ cho xe tiếp xăng
        for (JSONObject ag : refuelAgents) {
            String aid = ag.getString("agent_id");
            int used = rStepsUsed.get(aid);
            if (used < daySteps) {
                actions.get(aid).add(-(daySteps - used));
            }
        }

        // BƯỚC 2: Phát lại sự kiện theo trục thời gian (Event-Driven Playback) để tính điểm công bằng
        Collections.sort(timeline);

        Map<Integer, Integer> simStocks = new HashMap<>();
        for (Map.Entry<Integer, JSONObject> e : activeSpots.entrySet()) {
            simStocks.put(e.getKey(), e.getValue().optInt("stocks", 1));
        }

        Set<Integer> simGlobalSeen = new HashSet<>(initialBrandsSeen);
        Set<Integer> simDailySeen = new HashSet<>();
        Map<String, Set<Integer>> visitedSpotsToday = new HashMap<>();
        for (JSONObject ag : patrolAgents) {
            visitedSpotsToday.put(ag.getString("agent_id"), new HashSet<>());
        }

        long cGlobal = 0, cDaily = 0, uCount = 0;

        if (verbose) {
            logBuilder.append("\n----------------------------------------------------------------------------------------\n");
            logBuilder.append("⏱️ DÒNG THỜI GIAN THU THẬP UDON THỰC TẾ (SẮP XẾP THEO BƯỚC):\n");
        }

        for (VisitEvent ev : timeline) {
            if (activeSpots.containsKey(ev.pos) && !visitedSpotsToday.get(ev.agentId).contains(ev.pos)) {
                int remainingStock = simStocks.getOrDefault(ev.pos, 0);
                if (remainingStock > 0) {
                    simStocks.put(ev.pos, remainingStock - 1);
                    visitedSpotsToday.get(ev.agentId).add(ev.pos);
                    uCount++;

                    int brand = activeSpots.get(ev.pos).getInt("brand");
                    String tag = "• Ăn tiện đường";
                    if (!simGlobalSeen.contains(brand)) {
                        cGlobal++;
                        simGlobalSeen.add(brand);
                        simDailySeen.add(brand);
                        tag = "★ BRAND MỚI TOÀN TRẬN";
                    } else if (!simDailySeen.contains(brand)) {
                        cDaily++;
                        simDailySeen.add(brand);
                        tag = "☆ Brand mới trong ngày";
                    }

                    if (verbose) {
                        logBuilder.append(String.format("   [Step %3d] 🚗 AGENT %-3s tại ô %3d | Thu thập BRAND %d (Kho còn %d) | %s\n",
                                ev.step, ev.agentId, ev.pos, brand, remainingStock - 1, tag));
                    }
                }
            }
        }

        LexicographicScore finalScore = new LexicographicScore(cGlobal, cDaily, uCount);

        if (verbose) {
            logBuilder.append("========================================================================================\n");
            logBuilder.append("📊 KẾT QUẢ ĐÁNH GIÁ TỪ ĐIỂM NGÀY ").append(currentDay).append(": ").append(finalScore).append("\n");
            logBuilder.append("========================================================================================\n");
            System.out.println(logBuilder.toString());

            org.example.util.MatchAuditLogger.recordDay(currentDay, finalScore.toLegacyZ(), (int)cGlobal, (int)cDaily, (int)uCount, simGlobalSeen);
        }

        return new SimResult(finalScore, actions);
    }

    private Map<String, List<Integer>> buildInitialTargets(
            HexGrid grid,
            List<JSONObject> patrolAgents,
            Map<Integer, JSONObject> activeSpots,
            Set<Integer> brandsSeen) {

        Map<String, List<Integer>> targets = new HashMap<>();
        Set<Integer> claimedSpots = new HashSet<>();
        Set<Integer> tempBrandsSeen = new HashSet<>(brandsSeen);

        for (JSONObject ag : patrolAgents) {
            String aid = ag.getString("agent_id");
            int pos = JsonUtil.getAgentPos(ag);
            int fuel = ag.optInt("fuel", 0);

            Pathfinder.DijkstraResult dr = Pathfinder.dijkstraFrom(grid, pos, fuel, false);
            Integer bestSpot = null;
            int bestDist = Integer.MAX_VALUE;

            for (Map.Entry<Integer, JSONObject> e : activeSpots.entrySet()) {
                int spos = e.getKey();
                if (claimedSpots.contains(spos)) continue;
                int brand = e.getValue().getInt("brand");
                if (tempBrandsSeen.contains(brand)) continue;

                if (dr.distSteps.containsKey(spos) && dr.distSteps.get(spos) < bestDist) {
                    bestDist = dr.distSteps.get(spos);
                    bestSpot = spos;
                }
            }

            if (bestSpot == null) {
                bestDist = Integer.MAX_VALUE;
                for (Map.Entry<Integer, JSONObject> e : activeSpots.entrySet()) {
                    int spos = e.getKey();
                    if (claimedSpots.contains(spos)) continue;
                    if (dr.distSteps.containsKey(spos) && dr.distSteps.get(spos) < bestDist) {
                        bestDist = dr.distSteps.get(spos);
                        bestSpot = spos;
                    }
                }
            }

            List<Integer> myTargets = new ArrayList<>();
            if (bestSpot != null) {
                myTargets.add(bestSpot);
                claimedSpots.add(bestSpot);
                tempBrandsSeen.add(activeSpots.get(bestSpot).getInt("brand"));
            }
            targets.put(aid, myTargets);
        }
        return targets;
    }

    private Map<String, List<Integer>> buildAllWait(List<JSONObject> patrols, List<JSONObject> refuels, int daySteps) {
        Map<String, List<Integer>> res = new HashMap<>();
        for (JSONObject p : patrols) res.put(p.getString("agent_id"), Collections.singletonList(-daySteps));
        for (JSONObject r : refuels) res.put(r.getString("agent_id"), Collections.singletonList(-daySteps));
        return res;
    }
}