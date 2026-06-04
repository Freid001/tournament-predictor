package com.tournamentpredictor.loader;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class CsvLoader {

    private static final CSVFormat CSV = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

    private final Path projectRoot;

    public CsvLoader() {
        this.projectRoot = Path.of(System.getProperty("user.dir"));
    }

    /** For testing: use a custom root path instead of the working directory. */
    public CsvLoader(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    public Map<String,String> loadGroups(String tournament) throws IOException {
        Path p = groupsPath(tournament);
        Map<String,String> map = new HashMap<>();
        Map<String,Integer> groupCounters = new HashMap<>();
        try (var reader = Files.newBufferedReader(p); var parser = CSV.parse(reader)) {
            for (CSVRecord r : parser) {
                String raw = r.get(0).trim();
                String team = r.size() > 1 ? r.get(1).trim() : "";
                if (raw.isEmpty() || team.isEmpty()) continue;
                String pos = resolvePosition(raw, groupCounters);
                map.put(pos, team);
            }
        }
        return map;
    }

    public Map<String,String> loadGroupQualifies(String tournament) throws IOException {
        Path p = groupsPath(tournament);
        Map<String,String> map = new HashMap<>();
        Map<String,Integer> groupCounters = new HashMap<>();
        try (var reader = Files.newBufferedReader(p); var parser = CSV.parse(reader)) {
            for (CSVRecord r : parser) {
                String raw = r.get(0).trim();
                String qualifies = r.isMapped("qualifies") ? r.get("qualifies").toLowerCase() :
                        (r.size() > 3 ? r.get(3).toLowerCase() : "");
                if (raw.isEmpty()) continue;
                String pos = resolvePosition(raw, groupCounters);
                map.put(pos, qualifies);
            }
        }
        return map;
    }

    public Map<String,String> loadGroupWinner(String tournament) throws IOException {
        return loadGroupsColumn(tournament, "group_winner");
    }

    public Map<String,String> loadRunnerUp(String tournament) throws IOException {
        return loadGroupsColumn(tournament, "runner_up");
    }

    public Map<String,String> loadThirdPlace(String tournament) throws IOException {
        return loadGroupsColumn(tournament, "3rd_place");
    }

    private Map<String,String> loadGroupsColumn(String tournament, String columnName) throws IOException {
        Path p = groupsPath(tournament);
        Map<String,String> map = new HashMap<>();
        Map<String,Integer> groupCounters = new HashMap<>();
        try (var reader = Files.newBufferedReader(p); var parser = CSV.parse(reader)) {
            for (CSVRecord r : parser) {
                String raw = r.get(0).trim();
                String val = r.isMapped(columnName) ? r.get(columnName).toLowerCase() : "";
                if (raw.isEmpty()) continue;
                String pos = resolvePosition(raw, groupCounters);
                map.put(pos, val);
            }
        }
        return map;
    }

    /**
     * Converts a raw position value from groups.csv to a canonical "A1"-style key.
     * Accepts both the legacy "A1" format and the new "A" (group-letter-only) format.
     * For the new format, the numeric suffix is derived from the row's order within its group.
     */
    private static String resolvePosition(String raw, Map<String, Integer> groupCounters) {
        if (raw.matches("[A-La-l][1-4]")) {
            return raw.toUpperCase();
        } else if (raw.matches("[A-La-l]")) {
            String g = raw.toUpperCase();
            int idx = groupCounters.merge(g, 1, Integer::sum);
            return g + idx;
        }
        return raw.toUpperCase();
    }

    public Map<String,String> loadOdds(String tournament, String columnName) throws IOException {
        Path p = projectRoot.resolve("data").resolve("ods").resolve(tournament).resolve("to_reach.csv");
        if (!Files.exists(p)) return new HashMap<>();
        Map<String,String> map = new HashMap<>();
        try (var reader = Files.newBufferedReader(p); var parser = CSV.parse(reader)) {
            for (CSVRecord r : parser) {
                if (!r.isMapped(columnName)) break;
                String country = r.get(0);
                String val = r.get(columnName);
                if (!country.isEmpty()) {
                    map.put(country, val);
                    map.put(normaliseCountryName(country), val);
                }
            }
        }
        return map;
    }

    private static String normaliseCountryName(String name) {
        switch (name.trim()) {
            case "USA": return "United States";
            case "United States": return "USA";
            case "Bosnia & Herzegovina": return "Bosnia and Herzegovina";
            case "Bosnia and Herzegovina": return "Bosnia & Herzegovina";
            case "Korea Republic": return "South Korea";
            case "South Korea": return "Korea Republic";
            case "Ivory Coast": return "Côte d'Ivoire";
            case "Côte d'Ivoire": return "Ivory Coast";
            default: return name;
        }
    }

    public Map<String,String> loadLast16Odds(String tournament) throws IOException {
        return loadOdds(tournament, "last_16");
    }

    public Map<String,Integer> loadElo() throws IOException {
        Path p = projectRoot.resolve("data").resolve("elo").resolve("world.csv");
        if (!Files.exists(p)) throw new IOException("ELO file not found: " + p.toAbsolutePath());
        Map<String,Integer> map = new HashMap<>();
        try (var reader = Files.newBufferedReader(p); var parser = CSV.parse(reader)) {
            for (CSVRecord r : parser) {
                if (r.size() < 4) continue;
                String name = r.get(2);
                try { map.put(name, Integer.parseInt(r.get(3))); } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    public Map<String,String> loadTeamCodes() throws IOException {
        Path p = projectRoot.resolve("data").resolve("elo").resolve("world.csv");
        if (!Files.exists(p)) return new HashMap<>();
        Map<String,String> map = new HashMap<>();
        try (var reader = Files.newBufferedReader(p); var parser = CSV.parse(reader)) {
            for (CSVRecord r : parser) {
                if (r.size() < 3) continue;
                map.put(r.get(2), r.get(1));
            }
        }
        return map;
    }

    /** Loads the team names from start.csv for a tournament (the team column). */
    public Set<String> loadStartTeams(String tournament) throws IOException {
        Path p = projectRoot.resolve("data").resolve("predictions").resolve(tournament).resolve("start.csv");
        if (!Files.exists(p)) throw new IOException("start.csv not found: " + p.toAbsolutePath());
        Set<String> teams = new LinkedHashSet<>();
        try (var reader = Files.newBufferedReader(p); var parser = CSV.parse(reader)) {
            for (CSVRecord r : parser) {
                String team = r.isMapped("team") ? r.get("team") : (r.size() > 1 ? r.get(1) : "");
                if (!team.isEmpty()) teams.add(team);
            }
        }
        return teams;
    }

    /** Loads the combined ELO map for a tournament: world.csv as base, overridden by groups.csv values. */
    public Map<String,Integer> loadTournamentElo(String tournament) throws IOException {
        Map<String,Integer> eloRatings = loadElo();
        eloRatings.putAll(loadGroupElo(tournament));
        return eloRatings;
    }

    /** Loads team ELO ratings from groups.csv (col[1]=teamName, col[2]=elo). */
    public Map<String,Integer> loadGroupElo(String tournament) throws IOException {
        Path p = groupsPath(tournament);
        if (!Files.exists(p)) return new HashMap<>();
        Map<String,Integer> map = new HashMap<>();
        try (var reader = Files.newBufferedReader(p); var parser = CSV.parse(reader)) {
            for (CSVRecord r : parser) {
                if (r.size() < 3) continue;
                String name = r.get(1);
                try { map.put(name, Integer.parseInt(r.get(2))); } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }


    private Path groupsPath(String tournament) throws IOException {
        Path p = projectRoot.resolve("data").resolve("predictions").resolve(tournament).resolve("groups.csv");
        if (!Files.exists(p))
            throw new IOException("Groups file not found: " + p.toAbsolutePath() + ". Expected data/predictions/" + tournament + "/groups.csv");
        return p;
    }

    /** Validates groups.csv and returns a list of error messages. Empty list means valid. */
    public List<String> validateGroups(String tournament) throws IOException {
        Path p = projectRoot.resolve("data").resolve("predictions").resolve(tournament).resolve("groups.csv");
        if(!Files.exists(p))
            return List.of("groups.csv not found: " + p.toAbsolutePath());

        List<String> errors = new ArrayList<>();
        List<String> lines = Files.readAllLines(p);

        // Detect format from header names
        String[] headerCols = lines.isEmpty() ? new String[0] : lines.get(0).split(",", -1);
        List<String> headers = Arrays.stream(headerCols).map(String::trim).collect(Collectors.toList());
        boolean hasPredictedPos = headers.contains("predicted_position");
        boolean hasQualForm = headers.contains("qualification_form");
        boolean hasFriendliesForm = headers.contains("friendlies_form");
        boolean hasH2hColumn = headers.contains("h2h");
        boolean hasH2hColumns = headers.contains("history_competitions") || headers.contains("group_h2h_comp");
        int gwIdx;
        int ruIdx;
        int tpIdx;
        int minCols;
        int h2hIdx = -1;
        int h2hCompIdx = -1;
        int h2hFriendlyIdx = -1;
        int qualFormIdx = -1;
        int friendliesFormIdx = -1;
        int predictedPosIdx = -1;
        if (hasPredictedPos) {
            if (hasH2hColumn) {
                // New format: group,team,elo_ranking,qualification_form,h2h,predicted_position,group_winner,runner_up,3rd_place
                h2hIdx = headers.indexOf("h2h");
                qualFormIdx = hasQualForm ? headers.indexOf("qualification_form") : -1;
                predictedPosIdx = headers.indexOf("predicted_position");
                gwIdx = headers.indexOf("group_winner");
                ruIdx = headers.indexOf("runner_up");
                tpIdx = headers.indexOf("3rd_place");
                minCols = hasQualForm ? 9 : 8;
            } else {
                h2hCompIdx = headers.contains("history_competitions") ? headers.indexOf("history_competitions") : headers.indexOf("group_h2h_comp");
                h2hFriendlyIdx = headers.contains("history_friendlies") ? headers.indexOf("history_friendlies") : headers.indexOf("group_h2h_friendly");
                qualFormIdx = hasQualForm ? headers.indexOf("qualification_form") : -1;
                friendliesFormIdx = hasFriendliesForm ? headers.indexOf("friendlies_form") : -1;
                predictedPosIdx = headers.indexOf("predicted_position");
                gwIdx = headers.indexOf("group_winner");
                ruIdx = headers.indexOf("runner_up");
                tpIdx = headers.indexOf("3rd_place");
                minCols = hasQualForm && hasFriendliesForm ? 11 : hasQualForm ? 10 : 9;
            }
        } else if (hasH2hColumns) {
            gwIdx = 5;
            ruIdx = 6;
            tpIdx = 7;
            minCols = 8;
        } else {
            gwIdx = 3;
            ruIdx = 4;
            tpIdx = 5;
            minCols = 6;
        }

        Set<String> seenPositions = new LinkedHashSet<>();
        Set<String> seenTeams = new LinkedHashSet<>();

        // group letter -> count of yes for each qualifier column
        Map<String, Integer> gwYes  = new LinkedHashMap<>();
        Map<String, Integer> ruYes  = new LinkedHashMap<>();
        // group letter -> team count
        Map<String, Integer> groupTeamCount = new LinkedHashMap<>();

        Set<String> validValues = Set.of("yes", "maybe", "no");

        for(int i = 1; i < lines.size(); i++){
            String raw = lines.get(i);
            String stripped = raw.replaceAll("\"", "").trim();
            if(stripped.isEmpty()) continue; // skip blank/separator rows
            String[] c = raw.split(",", -1);

            // Strip any trailing empty columns caused by trailing commas
            int lastNonEmpty = c.length - 1;
            while(lastNonEmpty > 0 && c[lastNonEmpty].trim().isEmpty()) lastNonEmpty--;
            int usableCols = lastNonEmpty + 1;

            if(usableCols < minCols){
                errors.add("Row " + i + ": expected " + minCols + " columns, got " + usableCols + " — \"" + raw.trim() + "\"");
                continue;
            }

            String pos  = c[0].trim();
            String team = c[1].trim();
            String gw   = c[gwIdx].trim().toLowerCase();
            String ru   = c[ruIdx].trim().toLowerCase();
            String tp   = c[tpIdx].trim().toLowerCase();

            // Position format: either [A-L][1-4] (legacy) or [A-L] (new group-letter-only)
            boolean isGroupLetterOnly = pos.matches("[A-La-l]");
            boolean isLegacyPosition  = pos.matches("[A-La-l][1-4]");
            if (!isGroupLetterOnly && !isLegacyPosition) {
                errors.add("Row " + i + ": invalid group/position \"" + pos + "\" (expected A-L or A-L followed by 1-4)");
            }

            // Team name
            if(team.isEmpty()){
                errors.add("Row " + i + ": team name is empty");
            } else if(!seenTeams.add(team)){
                errors.add("Row " + i + ": duplicate team name \"" + team + "\"");
            }

            // Duplicate position check applies only to legacy A1-style values
            if(isLegacyPosition && !seenPositions.add(pos)){
                errors.add("Row " + i + ": duplicate position \"" + pos + "\"");
            }

            // Qualifier values
            if(!validValues.contains(gw))
                errors.add("Row " + i + " (" + pos + "): group_winner must be yes/maybe/no, got \"" + c[gwIdx].trim() + "\"");
            if(!validValues.contains(ru))
                errors.add("Row " + i + " (" + pos + "): runner_up must be yes/maybe/no, got \"" + c[ruIdx].trim() + "\"");
            if(!validValues.contains(tp))
                errors.add("Row " + i + " (" + pos + "): 3rd_place must be yes/maybe/no, got \"" + c[tpIdx].trim() + "\"");

            // Validate H2H columns when present
            if (h2hIdx >= 0) {
                validateH2hPercentColumn(errors, i, pos, c, h2hIdx, "h2h");
            }
            if (hasH2hColumns && h2hCompIdx >= 0 && h2hFriendlyIdx >= 0) {
                validateH2hPercentColumn(errors, i, pos, c, h2hCompIdx, "history_competitions");
                validateH2hPercentColumn(errors, i, pos, c, h2hFriendlyIdx, "history_friendlies");
            }
            if (qualFormIdx >= 0) {
                validateH2hPercentColumn(errors, i, pos, c, qualFormIdx, "qualification_form");
            }
            if (friendliesFormIdx >= 0) {
                validateH2hPercentColumn(errors, i, pos, c, friendliesFormIdx, "friendlies_form");
            }

            // Validate predicted_position (integer 1-4) when present
            if (predictedPosIdx >= 0) {
                validatePredictedPositionColumn(errors, i, pos, c, predictedPosIdx);
            }

            // Track per-group counts
            String grp = pos.isEmpty() ? "" : pos.substring(0, 1).toUpperCase();
            if (!grp.isEmpty()) {
                groupTeamCount.merge(grp, 1, Integer::sum);
                if("yes".equals(gw)) gwYes.merge(grp, 1, Integer::sum);
                if("yes".equals(ru)) ruYes.merge(grp, 1, Integer::sum);
            }
        }

        // Per-group structural checks
        for(char g = 'A'; g <= 'L'; g++){
            String grp = String.valueOf(g);
            int count = groupTeamCount.getOrDefault(grp, 0);
            if (count != 4) errors.add("Group " + grp + ": expected 4 teams, found " + count);
            int gw = gwYes.getOrDefault(grp, 0);
            if(gw != 1) errors.add("Group " + grp + ": must have exactly 1 group_winner=yes, found " + gw);
            int ru = ruYes.getOrDefault(grp, 0);
            if(ru != 1) errors.add("Group " + grp + ": must have exactly 1 runner_up=yes, found " + ru);
        }

        return errors;
    }

    private static void validateH2hPercentColumn(List<String> errors, int rowNum, String pos,
                                                  String[] c, int colIdx, String colName) {
        String val = c[colIdx].trim();
        if ("N/A".equalsIgnoreCase(val)) return;
        String numeric = val.replace("%", "");
        try {
            int pct = Integer.parseInt(numeric);
            if (pct < 0 || pct > 100) {
                errors.add("Row " + rowNum + " (" + pos + "): " + colName + " must be between 0 and 100, got " + pct);
            }
        } catch (NumberFormatException e) {
            errors.add("Row " + rowNum + " (" + pos + "): " + colName + " must be an integer 0-100 or N/A, got \"" + val + "\"");
        }
    }

    private static void validatePredictedPositionColumn(List<String> errors, int rowNum, String pos,
                                                         String[] c, int colIdx) {
        String val = c[colIdx].trim();
        // Accept both "1" and "1 (52%)" formats
        String rankStr = val.replaceAll("\\s*\\(.*", "").trim();
        try {
            int rank = Integer.parseInt(rankStr);
            if (rank < 1 || rank > 4) {
                errors.add("Row " + rowNum + " (" + pos + "): predicted_position must be 1-4, got " + rank);
            }
        } catch (NumberFormatException e) {
            errors.add("Row " + rowNum + " (" + pos + "): predicted_position must be an integer 1-4, got \"" + val + "\"");
        }
    }

    public List<BracketEntry> loadBrackets(String tournament) throws IOException {
        Path p = projectRoot.resolve("data").resolve("bracket").resolve(tournament + ".csv");
        if (!Files.exists(p))
            throw new IOException("Bracket file not found: " + p.toAbsolutePath() + ". Expected data/bracket/" + tournament + ".csv");
        List<BracketEntry> entries = new ArrayList<>();
        try (var reader = Files.newBufferedReader(p); var parser = CSV.parse(reader)) {
            for (CSVRecord r : parser) {
                String stage = r.size() > 1 ? r.get(1) : "";
                String raw   = r.size() > 2 ? r.get(2) : "";
                String matchId = "", t1 = "", t2 = "";

                if (raw.matches("^M\\d+$")) {
                    matchId = raw;
                    // columns 3+ are the slot tokens
                    t1 = r.size() > 3 ? r.get(3) : "";
                    t2 = r.size() > 4 ? r.get(4) : "";
                } else {
                    t1 = raw;
                }
                entries.add(new BracketEntry(matchId, t1, t2, stage));
            }
        }
        return entries;
    }

    public static class BracketEntry {
        public final String matchId;
        public final String token1;
        public final String token2;
        public final String stage;
        public BracketEntry(String matchId, String token1, String token2, String stage){
            this.matchId = matchId;
            this.token1 = token1;
            this.token2 = token2;
            this.stage = stage;
        }
    }
}
