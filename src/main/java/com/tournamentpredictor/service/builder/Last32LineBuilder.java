package com.tournamentpredictor.service.builder;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.util.DisplayBuilder;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.PathCalculator;
import com.tournamentpredictor.service.util.ThirdPlaceResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Last32LineBuilder {
    private final DisplayBuilder displayBuilder;
    private final PathCalculator pathCalculator;
    private final EloCalculator predictionHelper;
    private final ThirdPlaceResolver thirdPlaceResolver;

    public Last32LineBuilder(DisplayBuilder displayBuilder, PathCalculator pathCalculator,
                             EloCalculator predictionHelper) {
        this(displayBuilder, pathCalculator, predictionHelper, null);
    }

    public Last32LineBuilder(DisplayBuilder displayBuilder, PathCalculator pathCalculator,
                             EloCalculator predictionHelper, ThirdPlaceResolver thirdPlaceResolver) {
        this.displayBuilder = displayBuilder;
        this.pathCalculator = pathCalculator;
        this.predictionHelper = predictionHelper;
        this.thirdPlaceResolver = thirdPlaceResolver;
    }

    public List<String> buildLast32Lines(Map<String, String> groups, Map<String, String> groupWinner,
                                         Map<String, String> runnerUp, Map<String, String> thirdPlace,
                                         Map<String, Integer> eloRatings, List<CsvLoader.BracketEntry> brackets) {
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
        lines.add("match_id,team1,team2,path,elo");
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
                    String eloPrediction = predictionHelper.computeEloPrediction(team1Name, team2Name, eloRatings);
                    lines.add(String.join(",", bracket.matchId, displayBuilder.safe(display1),
                            displayBuilder.safe(display2), path, eloPrediction));
                }
            }
            lines.add("");
        }
        return lines;
    }

    /**
     * Computes path for a match with a composite 3rd-place token using resolved slot assignments.
     * Returns null if the composite team is not assigned to this slot (row should be skipped).
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
            // This composite team's group is not assigned to this slot — skip
            return null;
        }

        // All teams from the resolved group are primary candidates for this slot —
        // we know which group fills the slot, but not which team finishes 3rd within it.
        boolean nonCompositeIsPrimary = isTokenPrimary(nonCompositeToken, nonCompositeTeam, teamGW, teamRU);

        if (nonCompositeIsPrimary) {
            return "primary";
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
