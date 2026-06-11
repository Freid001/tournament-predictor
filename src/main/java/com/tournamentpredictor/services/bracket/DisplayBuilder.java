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

    public List<String> buildDisplays(String token, Map<String, String> groups, Map<String, String> groupWinner,
                                      Map<String, String> runnerUp, Map<String, String> thirdPlace) {
        List<String> out = new ArrayList<>();
        if (token == null || token.isEmpty()) {
            out.add("");
            return out;
        }

        if (token.matches("^[A-L][1-4]$")) {
            addGroupPositionDisplays(token, groups, groupWinner, runnerUp, thirdPlace, out);
            return out;
        }

        if (token.matches("^[A-L]+3$")) {
            for (char group : token.toCharArray()) {
                if (group == '3') {
                    break;
                }
                Set<String> seen = new LinkedHashSet<>();
                for (int i = 1; i <= 4; i++) {
                    String slot = "" + group + i;
                    String thirdPlaceValue = thirdPlace.getOrDefault(slot, "");
                    if ("no".equalsIgnoreCase(thirdPlaceValue)) {
                        continue;
                    }
                    String team = groups.getOrDefault(slot, "");
                    if (team == null || team.isEmpty()) {
                        team = slot;
                    }
                    if (seen.add(team)) {
                        out.add(token + "(" + team + ")");
                    }
                }
            }
            return out;
        }

        String resolved = tokenResolver.resolveToken(token, groups);
        out.add(displayTokenWithName(token, resolved));
        return out;
    }

    public List<String> buildWinnerDisplays(String token, Map<String, String> groups, Map<String, String> groupWinner,
                                            Map<String, String> runnerUp, Map<String, String> thirdPlace,
                                            List<CsvLoader.BracketEntry> brackets, List<String> last32Rows) {
        List<String> out = new ArrayList<>();
        if (token == null || token.isEmpty()) {
            out.add("");
            return out;
        }
        if (token.matches("^W\\d+$")) {
            String matchId = "M" + token.substring(1);
            Set<String> yesDisplays = new LinkedHashSet<>();
            Set<String> maybeDisplays = new LinkedHashSet<>();
            for (String row : last32Rows) {
                if (row.trim().isEmpty()) {
                    continue;
                }
                String[] cols = row.split(",", -1);
                if (cols.length < 5 || !matchId.equalsIgnoreCase(cols[0].trim())) {
                    continue;
                }
                String path = cols[3].trim();
                String display1 = cols[1].trim();
                String display2 = cols[2].trim();
                if ("predicted".equalsIgnoreCase(path)) {
                    yesDisplays.add(display1);
                    yesDisplays.add(display2);
                } else if ("alt".equalsIgnoreCase(path) || "upset".equalsIgnoreCase(path)) {
                    maybeDisplays.add(display1);
                    maybeDisplays.add(display2);
                }
            }
            Set<String> allDisplays = new LinkedHashSet<>(yesDisplays);
            allDisplays.addAll(maybeDisplays);
            if (!allDisplays.isEmpty()) {
                for (String display : allDisplays) {
                    out.add(token + "(" + display + ")");
                }
                return out;
            }
            for (CsvLoader.BracketEntry source : brackets) {
                if (matchId.equalsIgnoreCase(source.matchId)) {
                    boolean token1Composite = source.token1 != null && source.token1.matches("^[A-L]+3$");
                    boolean token2Composite = source.token2 != null && source.token2.matches("^[A-L]+3$");
                    Set<String> seen = new LinkedHashSet<>();
                    if (!token1Composite || token2Composite) {
                        seen.addAll(buildDisplays(source.token1, groups, groupWinner, runnerUp, thirdPlace));
                    }
                    if (!token2Composite || token1Composite) {
                        seen.addAll(buildDisplays(source.token2, groups, groupWinner, runnerUp, thirdPlace));
                    }
                    for (String display : seen) {
                        out.add(token + "(" + display + ")");
                    }
                    return out;
                }
            }
        }
        return buildDisplays(token, groups, groupWinner, runnerUp, thirdPlace);
    }

    public String displayTokenWithName(String token, String resolved) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        if (resolved == null || resolved.isEmpty() || resolved.equals(token)) {
            return token;
        }
        return token + "(" + resolved + ")";
    }

    public boolean isCompositeDisplay(String display) {
        return display != null && display.matches("^[A-L]{2,}3\\(.*\\)$");
    }

    public String safe(String value) {
        return value == null ? "" : value.replaceAll(",", " ");
    }

    private void addGroupPositionDisplays(String token, Map<String, String> groups,
                                          Map<String, String> groupWinner,
                                          Map<String, String> runnerUp,
                                          Map<String, String> thirdPlace,
                                          List<String> out) {
        Map<String, String> statusBySlot = switch (token.charAt(1)) {
            case '1' -> groupWinner;
            case '2' -> runnerUp;
            case '3' -> thirdPlace;
            default -> Map.of();
        };
        Set<String> seen = new LinkedHashSet<>();
        addSlotDisplay(token, token, groups.getOrDefault(token, ""), statusBySlot.getOrDefault(token, ""), seen, out);

        String group = token.substring(0, 1);
        for (int i = 1; i <= 4; i++) {
            String sourceSlot = group + i;
            if (sourceSlot.equalsIgnoreCase(token)) {
                continue;
            }
            addSlotDisplay(token, sourceSlot, groups.getOrDefault(sourceSlot, ""),
                    statusBySlot.getOrDefault(sourceSlot, ""), seen, out);
        }
    }

    private void addSlotDisplay(String targetToken, String sourceSlot, String team, String status,
                                Set<String> seen, List<String> out) {
        if (status == null || status.isBlank() || "no".equalsIgnoreCase(status)) {
            return;
        }
        String resolved = team == null || team.isEmpty() ? sourceSlot : team;
        if (seen.add(resolved)) {
            out.add(targetToken + "(" + resolved + ")");
        }
    }
}
