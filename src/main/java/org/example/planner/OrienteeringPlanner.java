package org.example.planner;

import org.example.grid.HexGrid;
import org.example.grid.Pathfinder;
import org.example.util.JsonUtil;
import org.json.JSONObject;

import java.util.*;

/**
 * OrienteeringPlanner — Bộ não AI cốt lõi cho thi đấu thật (PHASE 2 - ĐỒNG BỘ 100% SERVER BTC).
 *
 * Nâng cấp tối thượng: Tích hợp cơ chế "Ăn Udon tiện đường" (En-route Automatic Collection).
 * Mô phỏng chính xác từng bước chân trên bản đồ: Ngay khi xe đạp lên bất kỳ ô nào là Quán Udon
 * còn kho dự trữ (dù chỉ đi ngang qua hay là đích đến), lập tức tự động thu thập và cộng điểm Z!
 */
public class OrienteeringPlanner implements DayPlanner {

    private static class SimResult {
        long scoreZ;
        Map<String, List<Integer>> actions;
        SimResult(long scoreZ, Map<String, List<Integer>> actions) {
            this.scoreZ = scoreZ;
            this.actions = actions;
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
        long currentScore = bestSim.scoreZ;
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

            if (candSim.scoreZ > currentScore) {
                currentScore = candSim.scoreZ;
                currentTargets = candidateTargets;
                if (candSim.scoreZ > bestSim.scoreZ) {
                    bestSim = candSim;
                    bestTargets = candidateTargets;
                }
            }
        }

        // CHẠY LẠI LẦN CUỐI VỚI BỘ LỘ TRÌNH VÔ ĐỊCH VÀ BẬT LOG CHI TIẾT
        System.out.println("-> [PHASE 2 SA] Đã hoàn tất " + iterations + " vòng lặp mô phỏng. Đang xuất chi tiết tọa độ đồng bộ Server...");
        SimResult finalLoggedSim = simulateAndScore(grid, patrolAgents, refuelAgents, activeSpots, daySteps, brandsSeen, bestTargets, currentDay, true);

        return finalLoggedSim.actions;
    }

    /**
     * Hàm mô phỏng tất định có hỗ trợ "Ăn tiện đường" (En-route Step-by-step Scoring).
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

        Map<Integer, Integer> simStocks = new HashMap<>();
        for (Map.Entry<Integer, JSONObject> e : activeSpots.entrySet()) {
            simStocks.put(e.getKey(), e.getValue().optInt("stocks", 1));
        }
        Set<Integer> simGlobalSeen = new HashSet<>(initialBrandsSeen);
        Set<Integer> simDailySeen = new HashSet<>();

        long cGlobal = 0, cDaily = 0, uCount = 0;
        int fuelLimit = patrolAgents.isEmpty() ? 100 : patrolAgents.get(0).optInt("fuelLimits", 100);

        StringBuilder logBuilder = new StringBuilder();
        if (verbose) {
            logBuilder.append("\n========================================================================================\n");
            logBuilder.append("       [BÁO CÁO QUỸ ĐẠO TỌA ĐỘ VÀ THU THẬP BRAND NGÀY ").append(currentDay).append(" - AI PHASE 2]\n");
            logBuilder.append("========================================================================================\n");
        }

        // MÔ PHỎNG CHI TIẾT TỪNG XE TUẦN TRA
        for (JSONObject ag : patrolAgents) {
            String aid = ag.getString("agent_id");
            List<Integer> targets = targetSequences.getOrDefault(aid, Collections.emptyList());
            int curPos = pPos.get(aid);
            int curFuel = pFuel.get(aid);
            int curStep = 0;
            List<Integer> myActions = actions.get(aid);

            // Mỗi xe chỉ được ăn tối đa 1 suất tại mỗi quán trong cùng 1 ngày
            Set<Integer> visitedSpotsToday = new HashSet<>();

            if (verbose) {
                logBuilder.append("🚗 AGENT ").append(aid).append(" (Tuần tra - Xuất phát tại ô ").append(curPos).append(" | Xăng: ").append(curFuel).append("):\n");
            }

            boolean visitedAny = false;

            // KIỂM TRA NGAY VỊ TRÍ XUẤT PHÁT ĐẦU NGÀY (Biết đâu xe đứng sẵn trên quán vừa được bơm kho)
            if (activeSpots.containsKey(curPos) && !visitedSpotsToday.contains(curPos)) {
                int remainingStock = simStocks.getOrDefault(curPos, 0);
                if (remainingStock > 0) {
                    simStocks.put(curPos, remainingStock - 1);
                    visitedSpotsToday.add(curPos);
                    uCount++;
                    visitedAny = true;
                    int brand = activeSpots.get(curPos).getInt("brand");
                    if (!simGlobalSeen.contains(brand)) { cGlobal++; simGlobalSeen.add(brand); simDailySeen.add(brand); }
                    else if (!simDailySeen.contains(brand)) { cDaily++; simDailySeen.add(brand); }
                    if (verbose) {
                        logBuilder.append("   ⚡ [Thu ngay đầu ngày]: Bước   0 tại ô ").append(String.format("%3d", curPos))
                                .append(" | Thu thập BRAND ").append(brand).append(" (Kho còn ").append(remainingStock - 1).append(")\n\n");
                    }
                }
            }

            for (int targetPos : targets) {
                if (curStep >= daySteps) break;
                if (curPos == targetPos) continue;

                Pathfinder.DijkstraResult dr = Pathfinder.dijkstraFrom(grid, curPos, 9999, false);
                if (!dr.distSteps.containsKey(targetPos)) continue;

                int reqSteps = dr.distSteps.get(targetPos);
                int reqFuel = dr.distFuel.get(targetPos);

                if (curStep + reqSteps > daySteps) {
                    if (verbose) logBuilder.append("   ⚠️ Dừng lộ trình: Không đủ bước tới ô ").append(targetPos).append(" (Cần ").append(reqSteps).append(" bước, quỹ ngày chỉ còn ").append(daySteps - curStep).append(").\n");
                    break;
                }

                // CẢNH BÁO XĂNG VÀ ĐIỀU ĐỘNG TIẾP XĂNG
                if (curFuel < reqFuel) {
                    RefuelCoordinator.RendezvousPlan plan = RefuelCoordinator.findRendezvous(
                            grid, curPos, curStep, rPos, rStepsUsed, daySteps
                    );

                    if (plan != null) {
                        String rId = plan.refuelAgentId;
                        List<Integer> rActions = actions.get(rId);
                        int rUsed = rStepsUsed.get(rId);

                        rActions.addAll(plan.refuelPathDirs);
                        if (plan.refuelStepsUsedAfter < curStep) {
                            rActions.add(-(curStep - plan.refuelStepsUsedAfter));
                        }

                        if (verbose) {
                            String rPathStr = formatPathCoords(grid, rPos.get(rId), plan.refuelPathDirs);
                            logBuilder.append("   🆘 CẢNH BÁO XĂNG: Cạn xăng tại ô ").append(curPos).append(" (Bước ").append(curStep).append(").\n");
                            logBuilder.append("      -> Đã điều động Refueler ").append(rId).append(" chạy cứu hộ theo lộ trình: ").append(rPathStr).append(" (Gặp tại bước ").append(plan.refuelStepsUsedAfter).append(")!\n");
                        }

                        rPos.put(rId, curPos);
                        rStepsUsed.put(rId, curStep);
                        curFuel = fuelLimit;
                    } else {
                        if (verbose) logBuilder.append("   ❌ HẾT XĂNG: Không có xe tiếp xăng nào cứu kịp tại ô ").append(curPos).append(". Hủy chặng đường tiếp theo.\n");
                        break;
                    }
                }

                List<Integer> pathDirs = Pathfinder.reconstructPath(dr.prev, curPos, targetPos);

                if (verbose) {
                    String pathStr = formatPathCoords(grid, curPos, pathDirs);
                    logBuilder.append("   🗺️ [Lộ trình]: ").append(pathStr).append(" (Tốn ").append(reqSteps).append(" bước, ").append(reqFuel).append(" xăng)\n");
                }

                myActions.addAll(pathDirs);

                // =========================================================================
                // MÔ PHỎNG TỪNG BƯỚC CHÂN (STEP-BY-STEP) ĐỂ ĂN UDON TIỆN ĐƯỜNG
                // =========================================================================
                for (int d : pathDirs) {
                    int stepCost = grid.travelSteps(curPos);
                    int fuelCost = grid.fuelCost(curPos);

                    curStep += stepCost;
                    curFuel -= fuelCost;

                    // Cập nhật tọa độ sang ô tiếp theo theo hướng d
                    for (HexGrid.Neighbor nb : grid.neighbors(curPos)) {
                        if (nb.direction == d) {
                            curPos = nb.pos;
                            break;
                        }
                    }

                    // KIỂM TRA XEM Ô VỪA BƯỚC VÀO CÓ PHẢI QUÁN UDON KHÔNG
                    if (activeSpots.containsKey(curPos) && !visitedSpotsToday.contains(curPos)) {
                        int remainingStock = simStocks.getOrDefault(curPos, 0);
                        if (remainingStock > 0) {
                            simStocks.put(curPos, remainingStock - 1);
                            visitedSpotsToday.add(curPos);
                            uCount++;
                            visitedAny = true;
                            int brand = activeSpots.get(curPos).getInt("brand");

                            String rewardTag;
                            if (!simGlobalSeen.contains(brand)) {
                                cGlobal++;
                                simGlobalSeen.add(brand);
                                simDailySeen.add(brand);
                                rewardTag = "★ BRAND MỚI TOÀN TRẬN (+10,000đ)";
                            } else if (!simDailySeen.contains(brand)) {
                                cDaily++;
                                simDailySeen.add(brand);
                                rewardTag = "☆ Brand mới trong ngày (+100đ)";
                            } else {
                                rewardTag = "• Ăn tiện đường (+1đ)";
                            }

                            if (verbose) {
                                boolean isFinalTarget = (curPos == targetPos);
                                String actionLabel = isFinalTarget ? "🍜 [Đích đến]" : "⚡ [Tiện đường]";
                                logBuilder.append("   ").append(actionLabel).append(": Bước ").append(String.format("%3d", curStep)).append(" tại ô ").append(String.format("%3d", curPos))
                                        .append(" | Thu thập BRAND ").append(brand).append(" (Kho còn ").append(remainingStock - 1).append(")")
                                        .append(" | ").append(rewardTag).append(" | Xăng còn: ").append(curFuel).append("\n\n");
                            }
                        } else if (verbose && curPos == targetPos) {
                            logBuilder.append("   🍜 [Đích đến]: Bước ").append(String.format("%3d", curStep)).append(" tại ô ").append(String.format("%3d", curPos))
                                    .append(" | Quán đã hết Udon từ trước! (Lãng phí bước)\n\n");
                        }
                    }
                }
            }

            if (verbose && !visitedAny) {
                logBuilder.append("   (Không thu thập được quán Udon nào trong ngày hôm nay)\n");
            }

            if (curStep < daySteps) {
                int waitSteps = daySteps - curStep;
                myActions.add(-waitSteps);
                if (verbose) {
                    logBuilder.append("   ⏳ [Chờ đợi]: Đứng yên ").append(waitSteps).append(" bước tại ô ").append(curPos).append(" cho đến hết ngày.\n\n");
                }
            }
        }

        // MÔ PHỎNG CHI TIẾT XE TIẾP XĂNG
        for (JSONObject ag : refuelAgents) {
            String aid = ag.getString("agent_id");
            int used = rStepsUsed.get(aid);
            int rFinalPos = rPos.get(aid);
            if (verbose) {
                logBuilder.append("🚚 AGENT ").append(aid).append(" (Tiếp xăng - Vị trí hiện tại: ô ").append(rFinalPos).append("):\n");
                if (used > 0) {
                    logBuilder.append("   -> Đã di chuyển tổng cộng ").append(used).append(" bước để tiếp xăng cho các xe Tuần tra.\n");
                } else {
                    logBuilder.append("   -> ⏳ [Chờ đợi]: Đứng yên trọn vẹn ").append(daySteps).append(" bước tại trạm cố định ô ").append(rFinalPos).append(" (Không có yêu cầu cứu hộ).\n\n");
                }
            }
            if (used < daySteps) {
                actions.get(aid).add(-(daySteps - used));
            }
        }

        long finalZ = (10000L * cGlobal) + (100L * cDaily) + (1L * uCount);

        if (verbose) {
            logBuilder.append("----------------------------------------------------------------------------------------\n");
            logBuilder.append("📊 TỔNG KẾT THÀNH TÍCH NGÀY ").append(currentDay).append(":\n");
            logBuilder.append("   • Số Brand MỚI TINH thu được (C_global) : ").append(cGlobal).append(" brand\n");
            logBuilder.append("   • Số Brand độc nhất trong ngày (C_daily): ").append(cDaily).append(" brand\n");
            logBuilder.append("   • Tổng số phần Udon ăn được (U_count)   : ").append(uCount).append(" phần\n");
            logBuilder.append("   • TỔNG ĐIỂM HÀM MỤC TIÊU (Z)            : ").append(finalZ).append(" điểm\n");
            logBuilder.append("========================================================================================\n");
            System.out.println(logBuilder.toString());

            // Ghi nhận thành tích vào bộ kiểm toán toàn trận
            org.example.util.MatchAuditLogger.recordDay(currentDay, finalZ, (int)cGlobal, (int)cDaily, (int)uCount, simGlobalSeen);
        }

        return new SimResult(finalZ, actions);
    }

    private static String formatPathCoords(HexGrid grid, int startPos, List<Integer> dirs) {
        if (dirs == null || dirs.isEmpty()) return "[" + startPos + " (đứng yên)]";
        StringBuilder sb = new StringBuilder("[");
        sb.append(startPos);
        int cur = startPos;
        for (int i = 0; i < dirs.size(); i++) {
            int d = dirs.get(i);
            for (HexGrid.Neighbor nb : grid.neighbors(cur)) {
                if (nb.direction == d) {
                    cur = nb.pos;
                    sb.append(" ➔ ").append(cur);
                    break;
                }
            }
        }
        sb.append("]");
        return sb.toString();
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