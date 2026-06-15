package com.tournamentpredictor.services.bracket;

import com.tournamentpredictor.services.io.CsvLoader;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DisplayBuilder {
    private final TokenResolver tokenResolver;

    public DisplayBuilder(TokenResolver tokenResolver) {
        this.tokenResolver = tokenResolver;
    }


    public List<RouteOption> buildOptions(String token, Map<String, String> groups, Map<String, String> groupWinner,
                                          Map<String, String> runnerUp, Map<String, String> thirdPlace) {
        List<RouteOption> out = new ArrayList<>();
        if (token == null || token.isEmpty()) {
            out.add(new RouteOption(token, "", "", "", token));
            return out;
        }

        if (token.matches("^[A-L][1-4]$")) {
            addGroupPositionOptions(token, groups, groupWinner, runnerUp, thirdPlace, out);
            return out;
        }

        if (token.matches("^[A-L]+3$")) {
            for (char group : token.toCharArray()) {
                if (group == '3') {
                    break;
                }
                Set<String> seen = new LinkedHashSet<>();
                for (int i = 1; i <= 4; i++) {
                    String sourceSlot = "" + group + i;
                    String thirdPlaceValue = thirdPlace.getOrDefault(sourceSlot, "");
                    if ("no".equalsIgnoreCase(thirdPlaceValue)) {
                        continue;
                    }
                    String team = groups.getOrDefault(sourceSlot, "");
                    if (team == null || team.isEmpty()) {
                        team = sourceSlot;
                    }
                    if (seen.add(team)) {
                        out.add(new RouteOption(token, team, "", sourceSlot, token));
                    }
                }
            }
            return out;
        }

        String resolved = tokenResolver.resolveToken(token, groups);
        out.add(new RouteOption(token, resolved == null ? "" : resolved, sourceMatchIdFromToken(token), groupFinishForTeam(resolved, groups), token));
        return out;
    }

    public List<RouteOption> buildWinnerOptions(String token, Map<String, String> groups, Map<String, String> groupWinner,
                                                Map<String, String> runnerUp, Map<String, String> thirdPlace,
                                                List<CsvLoader.BracketEntry> brackets, List<String> last32Rows) {
        List<RouteOption> out = new ArrayList<>();
        if (token == null || token.isEmpty()) {
            out.add(new RouteOption(token, "", "", "", token));
            return out;
        }
        if (token.matches("^W\\d+$")) {
            String matchId = "M" + token.substring(1);
            Set<RouteOption> yesOptions = new LinkedHashSet<>();
            Set<RouteOption> maybeOptions = new LinkedHashSet<>();
            Map<String, Integer> header = header(last32Rows);
            for (String row : last32Rows) {
                if (row.trim().isEmpty() || row.startsWith("match_id")) {
                    continue;
                }
                String[] cols = row.split(",", -1);
                if (cols.length < 5 || !matchId.equalsIgnoreCase(cols[0].trim())) {
                    continue;
                }
                String path = cols[3].trim();
                RouteOption option1 = optionForSide(cols, header, true);
                RouteOption option2 = optionForSide(cols, header, false);
                if ("predicted".equalsIgnoreCase(path)) {
                    yesOptions.add(option1);
                    yesOptions.add(option2);
                } else if ("alt".equalsIgnoreCase(path) || "upset".equalsIgnoreCase(path)) {
                    maybeOptions.add(option1);
                    maybeOptions.add(option2);
                }
            }
            Set<RouteOption> allOptions = new LinkedHashSet<>(yesOptions);
            allOptions.addAll(maybeOptions);
            if (!allOptions.isEmpty()) {
                for (RouteOption option : allOptions) {
                    out.add(option.withSlot(token).withSourceMatch(matchId));
                }
                return out;
            }
            for (CsvLoader.BracketEntry source : brackets) {
                if (matchId.equalsIgnoreCase(source.matchId)) {
                    boolean token1Composite = source.token1 != null && source.token1.matches("^[A-L]+3$");
                    boolean token2Composite = source.token2 != null && source.token2.matches("^[A-L]+3$");
                    Set<RouteOption> seen = new LinkedHashSet<>();
                    if (!token1Composite || token2Composite) {
                        seen.addAll(buildOptions(source.token1, groups, groupWinner, runnerUp, thirdPlace));
                    }
                    if (!token2Composite || token1Composite) {
                        seen.addAll(buildOptions(source.token2, groups, groupWinner, runnerUp, thirdPlace));
                    }
                    for (RouteOption option : seen) {
                        out.add(option.withSlot(token).withSourceMatch(matchId));
                    }
                    return out;
                }
            }
        }
        return buildOptions(token, groups, groupWinner, runnerUp, thirdPlace);
    }

    private static Map<String, Integer> header(List<String> rows) {
        java.util.Map<String, Integer> header = new java.util.LinkedHashMap<>();
        if (rows == null || rows.isEmpty()) return header;
        String[] cols = rows.get(0).split(",", -1);
        for (int i = 0; i < cols.length; i++) {
            header.put(cols[i].trim(), i);
        }
        return header;
    }

    private static RouteOption optionForSide(String[] cols, Map<String, Integer> header, boolean team1) {
        String prefix = team1 ? "team1_" : "team2_";
        String team = valueAt(cols, header, prefix + "team", "");
        String bracketSlot = valueAt(cols, header, prefix + "bracket_slot", "");
        String groupFinish = valueAt(cols, header, prefix + "group_finish", "");
        String sourceMatch = valueAt(cols, header, prefix + "source_match", "");
        return new RouteOption(bracketSlot, team, sourceMatch, groupFinish, bracketSlot);
    }

    private static String valueAt(String[] cols, Map<String, Integer> header, String column, String fallback) {
        Integer index = header.get(column);
        if (index == null || index < 0 || index >= cols.length) return fallback;
        String value = cols[index].trim();
        return value.isBlank() ? fallback : value;
    }


    public String safe(String value) {
        return value == null ? "" : value.replaceAll(",", " ");
    }

    private void addGroupPositionOptions(String token, Map<String, String> groups,
                                         Map<String, String> groupWinner,
                                         Map<String, String> runnerUp,
                                         Map<String, String> thirdPlace,
                                         List<RouteOption> out) {
        Map<String, String> statusBySlot = switch (token.charAt(1)) {
            case '1' -> groupWinner;
            case '2' -> runnerUp;
            case '3' -> thirdPlace;
            default -> Map.of();
        };
        Set<String> seen = new LinkedHashSet<>();
        addSlotOption(token, token, groups.getOrDefault(token, ""), statusBySlot.getOrDefault(token, ""), seen, out);

        String group = token.substring(0, 1);
        for (int i = 1; i <= 4; i++) {
            String sourceSlot = group + i;
            if (sourceSlot.equalsIgnoreCase(token)) {
                continue;
            }
            addSlotOption(token, sourceSlot, groups.getOrDefault(sourceSlot, ""),
                    statusBySlot.getOrDefault(sourceSlot, ""), seen, out);
        }
    }

    private void addSlotOption(String targetToken, String sourceSlot, String team, String status,
                               Set<String> seen, List<RouteOption> out) {
        if (team == null || team.isBlank()) {
            return;
        }
        if (seen.add(team)) {
            out.add(new RouteOption(targetToken, team, "", sourceSlot, targetToken));
        }
    }

    private static String sourceMatchIdFromToken(String token) {
        if (token == null || !token.matches("^W\\d+$")) return "";
        return "M" + token.substring(1);
    }




    private static String groupFinishForTeam(String teamName, Map<String, String> groups) {
        if (teamName == null || teamName.isBlank()) return "";
        for (Map.Entry<String, String> entry : groups.entrySet()) {
            if (teamName.equalsIgnoreCase(entry.getValue())) {
                return entry.getKey() == null ? "" : entry.getKey().trim().toUpperCase();
            }
        }
        return "";
    }



    public record RouteOption(String slot, String team, String sourceMatchId, String groupFinish, String bracketSlot) {
        public String display() {
            if (slot == null || slot.isBlank()) return team == null ? "" : team;
            if (team == null || team.isBlank() || team.equals(slot)) return slot;
            return slot + "(" + team + ")";
        }

        public RouteOption withSlot(String newSlot) {
            return new RouteOption(newSlot, team, sourceMatchId, groupFinish, bracketSlot);
        }

        public RouteOption withSourceMatch(String newSourceMatchId) {
            return new RouteOption(slot, team, newSourceMatchId, groupFinish, bracketSlot);
        }
    }
}
