package com.tournamentpredictor.services.prediction.builder;

import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.bracket.DisplayBuilder;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PathCalculator;
import com.tournamentpredictor.services.calculation.PathFatigueCalculator;
import com.tournamentpredictor.services.calculation.TeamEloSnapshot;
import com.tournamentpredictor.services.bracket.ThirdPlaceResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Last32LineBuilder {
    private final DisplayBuilder displayBuilder;
    private final PathCalculator pathCalculator;
    private static final String HEADER = "match_id,team1,team2,path,elo,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent";

    private final EloCalculator predictionHelper;
    private final ThirdPlaceResolver thirdPlaceResolver;
    private final PathFatigueCalculator pathFatigueCalc;

    public Last32LineBuilder(DisplayBuilder displayBuilder, PathCalculator pathCalculator,
                             EloCalculator predictionHelper) {
        this(displayBuilder, pathCalculator, predictionHelper, null);
    }

    public Last32LineBuilder(DisplayBuilder displayBuilder, PathCalculator pathCalculator,
                             EloCalculator predictionHelper, ThirdPlaceResolver thirdPlaceResolver) {
        this(displayBuilder, pathCalculator, predictionHelper, thirdPlaceResolver, new PathFatigueCalculator());
    }

    public Last32LineBuilder(DisplayBuilder displayBuilder, PathCalculator pathCalculator,
                             EloCalculator predictionHelper, ThirdPlaceResolver thirdPlaceResolver,
                             PathFatigueCalculator pathFatigueCalc) {
        this.displayBuilder = displayBuilder;
        this.pathCalculator = pathCalculator;
        this.predictionHelper = predictionHelper;
        this.thirdPlaceResolver = thirdPlaceResolver;
        this.pathFatigueCalc = pathFatigueCalc != null ? pathFatigueCalc : new PathFatigueCalculator();
    }

    public List<String> buildLast32Lines(Map<String, String> groups, Map<String, String> groupWinner,
                                         Map<String, String> runnerUp, Map<String, String> thirdPlace,
                                         Map<String, Integer> eloRatings, List<CsvLoader.BracketEntry> brackets) {
        return buildLast32Lines(groups, groupWinner, runnerUp, thirdPlace, eloRatings, brackets, Map.of());
    }

    public List<String> buildLast32Lines(Map<String, String> groups, Map<String, String> groupWinner,
                                         Map<String, String> runnerUp, Map<String, String> thirdPlace,
                                         Map<String, Integer> eloRatings, List<CsvLoader.BracketEntry> brackets,
                                         Map<String, TeamEloSnapshot> snapshots) {
        Map<String, String> teamGW = new HashMap<>();
        Map<String, String> teamRU = new HashMap<>();
        Map<String, String> teamTP = new HashMap<>();
        for (Map.Entry<String, String> entry : groups.entrySet()) {
            String team = entry.getValue();
            String position = entry.getKey();
            if (team != null && !team.isEmpty()) {
                teamGW.put(team, groupWinner.getOrDefault(position, ""));
                teamRU.put(team, runnerUp.getOrDefault(position, ""));
                teamTP.put(team, thirdPlace.getOrDefault(position, ""));
            }
        }

        Map<String, List<String>> resolvedAssignments = Collections.emptyMap();
        if (thirdPlaceResolver != null) {
            try {
                resolvedAssignments = thirdPlaceResolver.buildCompositeAssignments(brackets, groups, thirdPlace, eloRatings);
            } catch (IOException e) {
                System.err.println("WARNING: ThirdPlaceResolver failed, 3rd place slots may show duplicates: " + e.getMessage());
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (CsvLoader.BracketEntry bracket : brackets) {
            if (!"LAST_32".equalsIgnoreCase(bracket.stage)) {
                continue;
            }
            boolean token2IsComposite = bracket.token2 != null && bracket.token2.matches("^[A-L]+3$");
            boolean token1IsComposite = bracket.token1 != null && bracket.token1.matches("^[A-L]+3$");
            boolean hasComposite = token1IsComposite || token2IsComposite;

            List<String> displays1 = displayBuilder.buildDisplays(bracket.token1, groups, groupWinner, runnerUp, thirdPlace);
            List<String> displays2 = displayBuilder.buildDisplays(bracket.token2, groups, groupWinner, runnerUp, thirdPlace);
            for (String display1 : displays1) {
                for (String display2 : displays2) {
                    String team1Name = predictionHelper.extractTeamName(display1);
                    String team2Name = predictionHelper.extractTeamName(display2);
                    String path;
                    if (hasComposite && !resolvedAssignments.isEmpty()) {
                        path = computeCompositeResolvedPath(bracket, display1, display2,
                                team1Name, team2Name, token1IsComposite, token2IsComposite,
                                teamGW, teamRU, teamTP, resolvedAssignments);
                        if (path == null) {
                            // Team not assigned to this slot — skip entirely
                            continue;
                        }
                    } else if (hasComposite) {
                        // Resolver unavailable — safe fallback: show composites as alt only to avoid duplicates
                        path = computeCompositeFallbackPath(bracket, display1, display2,
                                team1Name, team2Name, token1IsComposite, token2IsComposite,
                                teamGW, teamRU, teamTP);
                        if (path == null) continue;
                    } else {
                        path = pathCalculator.computePredictedMatch(bracket.token1, display1, bracket.token2, display2,
                                teamGW, teamRU, teamTP);
                    }
                    GroupLoadResult groupLoad1 = groupLoadFor(team1Name, groups, eloRatings, snapshots);
                    GroupLoadResult groupLoad2 = groupLoadFor(team2Name, groups, eloRatings, snapshots);
                    int team1AdjustedElo = eloRatings.getOrDefault(team1Name, 0) + groupLoad1.adjustedElo;
                    int team2AdjustedElo = eloRatings.getOrDefault(team2Name, 0) + groupLoad2.adjustedElo;
                    String eloPrediction = predictionHelper.computeEloPredictionFromElos(team1Name, team2Name, team1AdjustedElo, team2AdjustedElo);
                    lines.add(String.join(",", bracket.matchId, displayBuilder.safe(display1),
                            displayBuilder.safe(display2), path, eloPrediction,
                            String.valueOf(groupLoad1.weightedTotal), String.valueOf(groupLoad2.weightedTotal),
                            groupLoad1.chain, groupLoad2.chain));
                }
            }
            lines.add("");
        }
        return lines;
    }

    private GroupLoadResult groupLoadFor(String teamName, Map<String, String> groups, Map<String, Integer> eloRatings,
                                         Map<String, TeamEloSnapshot> snapshots) {
        if (teamName == null || teamName.isBlank()) {
            return new GroupLoadResult(0, 0, "");
        }
        String group = groupForTeam(teamName, groups);
        if (group.isEmpty()) {
            return new GroupLoadResult(0, 0, "");
        }

        int weightedTotal = 0;
        List<String> segments = new ArrayList<>();
        for (Map.Entry<String, String> entry : groups.entrySet()) {
            String slot = entry.getKey();
            String opponent = entry.getValue();
            if (slot == null || opponent == null || opponent.isBlank() || opponent.equalsIgnoreCase(teamName)) {
                continue;
            }
            if (!slot.toUpperCase().startsWith(group)) {
                continue;
            }
            int weighted = pathFatigueCalc.groupStageWeightedContribution(
                    eloRatings.getOrDefault(opponent, pathFatigueCalc.getTournamentAvgElo()));
            if (weighted <= 0) {
                continue;
            }
            weightedTotal += weighted;
            int contributionElo = pathFatigueCalc.eloAdjustmentFromWeighted(weighted);
            segments.add("G|" + opponent + ":" + contributionElo);
        }

        int fatigue = pathFatigueCalc.eloAdjustmentFromWeighted(weightedTotal);
        TeamEloSnapshot snapshot = snapshots != null ? snapshots.get(teamName) : null;
        int depthLevel = snapshot != null ? snapshot.squadDepthLevel() : 0;
        int adjustedElo = pathFatigueCalc.applyDepthMultiplier(fatigue, depthLevel);
        return new GroupLoadResult(weightedTotal, adjustedElo, String.join(" > ", segments));
    }

    private static String groupForTeam(String teamName, Map<String, String> groups) {
        for (Map.Entry<String, String> entry : groups.entrySet()) {
            String team = entry.getValue();
            if (team != null && team.equalsIgnoreCase(teamName)) {
                String slot = entry.getKey();
                return slot == null || slot.isBlank() ? "" : slot.substring(0, 1).toUpperCase();
            }
        }
        return "";
    }

    private record GroupLoadResult(int weightedTotal, int adjustedElo, String chain) {}

    /**
     * Computes path for a match with a composite 3rd-place token using resolved slot assignments.
     * Returns null if the composite team has third_place=no (row should be skipped entirely).
     * Returns "alt" if the composite team's group is not the resolver-assigned group (genuine alternative path).
     */
    private String computeCompositeResolvedPath(CsvLoader.BracketEntry bracket,
                                                 String display1, String display2,
                                                 String team1Name, String team2Name,
                                                 boolean token1IsComposite, boolean token2IsComposite,
                                                 Map<String, String> teamGW, Map<String, String> teamRU,
                                                 Map<String, String> teamTP,
                                                 Map<String, List<String>> resolvedAssignments) {
        String compositeTeam = token2IsComposite ? team2Name : team1Name;
        String nonCompositeToken = token2IsComposite ? bracket.token1 : bracket.token2;
        String nonCompositeDisplay = token2IsComposite ? display1 : display2;
        String nonCompositeTeam = token2IsComposite ? team1Name : team2Name;

        List<String> resolved = resolvedAssignments.get(bracket.matchId);
        if (resolved == null || !resolved.contains(compositeTeam)) {
            // Not the resolver-assigned group — show as alt if they're a valid 3rd-place candidate
            String tpStatus = teamTP.getOrDefault(compositeTeam, "no");
            if ("no".equalsIgnoreCase(tpStatus) || tpStatus.isEmpty()) {
                return null; // not a 3rd-place candidate at all — skip
            }
            return "alt";
        }

        // Resolved group: "yes" 3rd-place + primary opponent = primary; "maybe" or alt opponent = alt
        String compositeTP = teamTP.getOrDefault(compositeTeam, "no");
        boolean compositeIsYes = "yes".equalsIgnoreCase(compositeTP);
        boolean nonCompositeIsPrimary = isTokenPrimary(nonCompositeToken, nonCompositeTeam, teamGW, teamRU);

        if (compositeIsYes && nonCompositeIsPrimary) {
            return "predicted";
        }
        return "alt";
    }

    /**
     * Fallback path when resolver is unavailable. Treats all composite teams as alt (never primary)
     * to avoid Scotland appearing in multiple primary slots.
     * Returns null for teams with third_place=no (skip those rows).
     */
    private String computeCompositeFallbackPath(CsvLoader.BracketEntry bracket,
                                                 String display1, String display2,
                                                 String team1Name, String team2Name,
                                                 boolean token1IsComposite, boolean token2IsComposite,
                                                 Map<String, String> teamGW, Map<String, String> teamRU,
                                                 Map<String, String> teamTP) {
        String compositeTeam = token2IsComposite ? team2Name : team1Name;
        String tpStatus = teamTP.getOrDefault(compositeTeam, "no");
        if ("no".equalsIgnoreCase(tpStatus) || tpStatus.isEmpty()) {
            return null;
        }
        return "alt";
    }

    /** Checks if a simple (non-composite) token's team is the predicted winner/runner-up. */
    private boolean isTokenPrimary(String token, String teamName,
                                    Map<String, String> teamGW, Map<String, String> teamRU) {
        if (token == null || teamName == null || teamName.isEmpty()) return false;
        if (token.matches("^[A-L]1$")) return "yes".equalsIgnoreCase(teamGW.getOrDefault(teamName, ""));
        if (token.matches("^[A-L]2$")) return "yes".equalsIgnoreCase(teamRU.getOrDefault(teamName, ""));
        return false;
    }
}
