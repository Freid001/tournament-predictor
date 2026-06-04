package com.tournamentpredictor.service.util;

import com.tournamentpredictor.loader.CsvLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ThirdPlaceResolver {
    private final Path projectRoot;

    public ThirdPlaceResolver(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    public List<String> computeTop8ThirdPlace(Map<String, String> groups, Map<String, String> thirdPlace,
                                              Map<String, Integer> eloRatings) {
        Map<String, int[]> groupBest = new HashMap<>();
        Map<String, String> groupTeam = new HashMap<>();
        for (Map.Entry<String, String> entry : groups.entrySet()) {
            String position = entry.getKey();
            String team = entry.getValue();
            String thirdPlaceValue = thirdPlace.getOrDefault(position, "");
            if (!"no".equalsIgnoreCase(thirdPlaceValue) && !thirdPlaceValue.isEmpty() && team != null && !team.isEmpty()) {
                String group = String.valueOf(position.charAt(0));
                int elo = eloRatings.getOrDefault(team, 0);
                if (!groupBest.containsKey(group) || elo > groupBest.get(group)[0]) {
                    groupBest.put(group, new int[]{elo});
                    groupTeam.put(group, team);
                }
            }
        }
        List<String> candidates = new ArrayList<>(groupTeam.values());
        candidates.sort((left, right) -> Integer.compare(eloRatings.getOrDefault(right, 0), eloRatings.getOrDefault(left, 0)));
        return candidates.size() > 8 ? candidates.subList(0, 8) : candidates;
    }

    public Map<String, List<String>> buildCompositeAssignments(List<CsvLoader.BracketEntry> brackets,
                                                               Map<String, String> groups,
                                                               Map<String, String> thirdPlace,
                                                               Map<String, Integer> eloRatings) throws IOException {
        Map<String, String> groupBestTeam = new HashMap<>();
        Map<String, Integer> groupBestElo = new HashMap<>();
        Map<String, List<String>> groupCandidates = new HashMap<>();
        for (Map.Entry<String, String> entry : groups.entrySet()) {
            String position = entry.getKey();
            String team = entry.getValue();
            String thirdPlaceValue = thirdPlace.getOrDefault(position, "");
            if (!"no".equalsIgnoreCase(thirdPlaceValue) && !thirdPlaceValue.isEmpty() && team != null && !team.isEmpty()) {
                String group = String.valueOf(position.charAt(0));
                int elo = eloRatings.getOrDefault(team, 0);
                if (!groupBestElo.containsKey(group) || elo > groupBestElo.get(group)) {
                    groupBestElo.put(group, elo);
                    groupBestTeam.put(group, team);
                }
                groupCandidates.computeIfAbsent(group, ignored -> new ArrayList<>()).add(team);
            }
        }
        for (List<String> candidates : groupCandidates.values()) {
            candidates.sort((left, right) -> Integer.compare(eloRatings.getOrDefault(right, 0), eloRatings.getOrDefault(left, 0)));
        }
        List<String> topGroups = new ArrayList<>(groupBestTeam.keySet());
        topGroups.sort((left, right) -> Integer.compare(groupBestElo.getOrDefault(right, 0), groupBestElo.getOrDefault(left, 0)));
        if (topGroups.size() > 8) {
            topGroups = topGroups.subList(0, 8);
        }

        List<String> sortedGroups = new ArrayList<>(topGroups);
        Collections.sort(sortedGroups);
        String lookupKey = String.join("", sortedGroups);

        Map<String, String> columnToMatch = new HashMap<>();
        for (CsvLoader.BracketEntry bracket : brackets) {
            if (!"LAST_32".equalsIgnoreCase(bracket.stage)) {
                continue;
            }
            String groupWinnerToken = null;
            String compositeToken = null;
            if (bracket.token1 != null && bracket.token1.matches("^[A-L]+3$")) {
                compositeToken = bracket.token1;
                groupWinnerToken = bracket.token2;
            } else if (bracket.token2 != null && bracket.token2.matches("^[A-L]+3$")) {
                compositeToken = bracket.token2;
                groupWinnerToken = bracket.token1;
            }
            if (compositeToken == null || groupWinnerToken == null) {
                continue;
            }
            if (groupWinnerToken.matches("^[A-L]1$")) {
                columnToMatch.put("1" + groupWinnerToken.charAt(0), bracket.matchId);
            }
        }

        Path lookupPath = projectRoot.resolve("data").resolve("bracket").resolve("third_place_lookup.csv");
        List<String> headers = null;
        Map<String, String> colToGroupLetter = null;
        try (BufferedReader reader = Files.newBufferedReader(lookupPath)) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (first) {
                    first = false;
                    headers = Arrays.asList(parts);
                    continue;
                }
                if (parts[0].equals(lookupKey)) {
                    colToGroupLetter = new LinkedHashMap<>();
                    for (int i = 1; i < parts.length && i < headers.size(); i++) {
                        colToGroupLetter.put(headers.get(i), parts[i]);
                    }
                    break;
                }
            }
        }

        Map<String, List<String>> assignments = new LinkedHashMap<>();
        if (colToGroupLetter == null) {
            return assignments;
        }
        for (Map.Entry<String, String> entry : colToGroupLetter.entrySet()) {
            String matchId = columnToMatch.get(entry.getKey());
            List<String> candidates = groupCandidates.getOrDefault(entry.getValue(), Collections.emptyList());
            if (matchId != null && !candidates.isEmpty()) {
                assignments.put(matchId, candidates);
            }
        }
        return assignments;
    }
}
