package com.tournamentpredictor.service.handler;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.util.ConsoleReporter;
import com.tournamentpredictor.service.util.EloCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class PathHandler {

    private static final Logger log = LoggerFactory.getLogger(PathHandler.class);

    private static final int MAX_DISPLAY_PATHS = 100;
    private static final int DFS_CAP = 5000;

    // Maps bracket stage key → short display label
    private static final Map<String, String> STAGE_LABELS = new LinkedHashMap<>();
    static {
        STAGE_LABELS.put("LAST_32", "R32");
        STAGE_LABELS.put("LAST_16", "R16");
        STAGE_LABELS.put("QUARTER", "QF");
        STAGE_LABELS.put("SEMI", "SF");
        STAGE_LABELS.put("FINAL", "Final");
    }

    // Maps round csv filename → bracket stage key
    private static final String[][] ROUND_FILES = {
        {"last_32", "LAST_32"},
        {"last_16", "LAST_16"},
        {"last_8",  "QUARTER"},
        {"last_4",  "SEMI"},
        {"final",   "FINAL"}
    };

    private final CsvLoader loader;
    private final Path projectRoot;
    private final EloCalculator eloCalculator;
    private final ConsoleReporter consoleReporter;

    public PathHandler(CsvLoader loader, Path projectRoot, EloCalculator eloCalculator,
                       ConsoleReporter consoleReporter) {
        this.loader = loader;
        this.projectRoot = projectRoot;
        this.eloCalculator = eloCalculator;
        this.consoleReporter = consoleReporter;
    }

    private static class MatchStep {
        final String matchId;
        final String stage;
        final String slot;
        final String opponent;
        final String opponentFlag;
        final boolean teamWins;
        final int winPct;
        final boolean isPrimary;

        MatchStep(String matchId, String stage, String slot, String opponent,
                  String opponentFlag, boolean teamWins, int winPct, boolean isPrimary) {
            this.matchId      = matchId;
            this.stage        = stage;
            this.slot         = slot;
            this.opponent     = opponent;
            this.opponentFlag = opponentFlag;
            this.teamWins     = teamWins;
            this.winPct       = winPct;
            this.isPrimary    = isPrimary;
        }
    }

    /** A single stage condition: path must have an opponent at `stage` matching `pattern`.
     *  Pattern supports * as a wildcard (e.g. "mex*", "*", "port*gal"). */
    private static class PathCondition {
        final String stage;
        final String pattern;
        PathCondition(String stage, String pattern) { this.stage = stage; this.pattern = pattern; }
        boolean matches(List<MatchStep> path) {
            for (MatchStep step : path) {
                if (step.stage.equalsIgnoreCase(stage) && wildcardMatch(step.opponent.toLowerCase(), pattern)) {
                    return true;
                }
            }
            return false;
        }
        /** True if this condition targets the given stage and the opponent matches the pattern. */
        boolean matchesOpponent(String opponentStage, String opponent) {
            return opponentStage.equalsIgnoreCase(stage) && wildcardMatch(opponent.toLowerCase(), pattern);
        }
        static boolean wildcardMatch(String text, String pattern) {
            if ("*".equals(pattern)) return true;
            if (!pattern.contains("*")) return text.contains(pattern);
            String[] parts = pattern.split("\\*", -1);
            int pos = 0;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.isEmpty()) continue;
                int idx = text.indexOf(part, pos);
                if (idx < 0) return false;
                if (i == 0 && !pattern.startsWith("*") && idx != 0) return false;
                pos = idx + part.length();
            }
            if (!pattern.endsWith("*") && !text.endsWith(parts[parts.length - 1])) return false;
            return true;
        }
    }

    /**
     * Difficulty score for a path: average of (100 - winPct) across all steps.
     * Higher = harder. Range 0–100.
     */
    private static int stageWeight(String stage) {
        switch (stage) {
            case "LAST_32": return 1;
            case "LAST_16": return 2;
            case "QUARTER": return 3;
            case "SEMI":    return 4;
            case "FINAL":   return 5;
            default:        return 1;
        }
    }

    static int difficultyScore(List<MatchStep> path) {
        if (path.isEmpty()) return 0;
        int weightedSum = 0;
        int totalWeight = 0;
        for (MatchStep s : path) {
            int w = stageWeight(s.stage);
            weightedSum += (100 - s.winPct) * w;
            totalWeight += w;
        }
        return totalWeight == 0 ? 0 : Math.round((float) weightedSum / totalWeight);
    }

    static String difficultyLabel(int score) {
        if (score < 25) return "EASY";
        if (score < 40) return "MEDIUM";
        if (score < 55) return "HARD";
        return "BRUTAL";
    }

    /** Checks whether a path's difficulty matches a filter like "hard", ">50", "<=40". */
    static boolean matchesDifficulty(int score, String filter) {
        String f = filter.trim().toLowerCase();
        if (f.startsWith(">=")) { try { return score >= Integer.parseInt(f.substring(2).trim()); } catch (NumberFormatException e) { return false; } }
        if (f.startsWith("<=")) { try { return score <= Integer.parseInt(f.substring(2).trim()); } catch (NumberFormatException e) { return false; } }
        if (f.startsWith(">"))  { try { return score >  Integer.parseInt(f.substring(1).trim()); } catch (NumberFormatException e) { return false; } }
        if (f.startsWith("<"))  { try { return score <  Integer.parseInt(f.substring(1).trim()); } catch (NumberFormatException e) { return false; } }
        if (f.startsWith("="))  { try { return score == Integer.parseInt(f.substring(1).trim()); } catch (NumberFormatException e) { return false; } }
        // Label match
        return difficultyLabel(score).equalsIgnoreCase(f);
    }


    /** Expands shorthand filter keys to their canonical form. */
    private static String resolveKey(String key) {
        switch (key.toLowerCase()) {
            case "t":  return "team";
            case "g":  return "group";
            case "d":  return "difficulty";
            case "32": return "last_32";
            case "16": return "last_16";
            case "q":  return "quarter";
            case "s":  return "semi";
            default:   return key;
        }
    }

    private static String stageKey(String key) {
        switch (key.toLowerCase().replace("-", "_")) {
            case "last_32": case "r32": case "32": return "LAST_32";
            case "last_16": case "r16": case "16": return "LAST_16";
            case "quarter": case "qf":  case "q": case "quarter_final": return "QUARTER";
            case "semi":    case "sf":  case "s": case "semi_final":    return "SEMI";
            case "final":   case "f":                                    return "FINAL";
            default: return null;
        }
    }

    /**
     * Parses a compound filter string into a list of team names, AND-conditions, and slot patterns.
     *
     * Syntax: team:england||team:france&last_16:Mexico&group:L2
     *   - "&"  = AND between condition groups
     *   - "||" = OR between alternatives within a group
     *   - "team:xxx" adds a team; multiple team:xxx entries joined with || → multiple teams
     *   - "group:L2" filters by the team's starting group slot (e.g. L1, L2, K1…)
     *
     * If no ":" is present, the whole string is treated as a single plain team name.
     */
    private static List<String> parseFilterConditions(String raw, List<List<PathCondition>> andConditions,
                                                       List<String> slotPatterns, List<String> difficultyFilters) {
        List<String> teams = new ArrayList<>();
        if (!raw.contains(":")) {
            teams.add(raw.toLowerCase());
            return teams;
        }
        String[] andParts = raw.split("&");
        for (String andPart : andParts) {
            String[] orParts = andPart.split("\\|\\|");
            List<PathCondition> orGroup = new ArrayList<>();
            for (String orPart : orParts) {
                int colon = orPart.indexOf(':');
                if (colon < 0) continue;
                String key = resolveKey(orPart.substring(0, colon).trim().toLowerCase());
                String val = orPart.substring(colon + 1).trim().toLowerCase();
                if ("team".equals(key)) {
                    if (!val.isEmpty()) teams.add(val);
                } else if ("group".equals(key)) {
                    if (!val.isEmpty()) slotPatterns.add(val);
                } else if ("difficulty".equals(key) || "diff".equals(key)) {
                    if (!val.isEmpty()) difficultyFilters.add(val);
                } else {
                    String stage = stageKey(key);
                    if (stage != null) {
                        orGroup.add(new PathCondition(stage, val));
                    }
                }
            }
            if (!orGroup.isEmpty()) andConditions.add(orGroup);
        }
        return teams;
    }

    public void handle(String tournament) throws IOException {
        String rawFilter = consoleReporter.getTeamFilter();
        String pathMode  = consoleReporter.getPathFilter();

        if (rawFilter == null || rawFilter.isEmpty()) {
            log.error("--mode=path requires --filter=<team>  (e.g. --filter=England)");
            System.exit(2);
        }

        // Build bracket graph: "W80" → "M92", and "M80" → "LAST_32"
        List<CsvLoader.BracketEntry> brackets = loader.loadBrackets(tournament);
        Map<String, String> winnerToNextMatch = new HashMap<>();
        Map<String, String> matchToStage      = new HashMap<>();
        for (CsvLoader.BracketEntry entry : brackets) {
            matchToStage.put(entry.matchId, entry.stage);
            if (entry.token1.startsWith("W")) winnerToNextMatch.put(entry.token1, entry.matchId);
            if (entry.token2.startsWith("W")) winnerToNextMatch.put(entry.token2, entry.matchId);
        }

        // Load all matchup rows indexed by matchId
        Map<String, List<String[]>> matchRows    = new HashMap<>();
        Map<String, String[]>       matchHeaders = new HashMap<>();

        Path simulationDir = projectRoot.resolve("data").resolve("simulations").resolve(tournament);
        for (String[] roundFile : ROUND_FILES) {
            Path file = simulationDir.resolve("matchup_paths_" + roundFile[0] + ".csv");
            if (!Files.exists(file)) continue;
            List<String> lines = Files.readAllLines(file);
            if (lines.size() < 2) continue;
            String[] headers = lines.get(0).split(",", -1);
            int mIdx = indexOf(headers, "match_id");
            if (mIdx < 0) continue;
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;
                String[] cols = line.split(",", -1);
                String mId = valueAt(cols, mIdx);
                matchRows.computeIfAbsent(mId, k -> new ArrayList<>()).add(cols);
                matchHeaders.putIfAbsent(mId, headers);
            }
        }

        // Find all starting matchIds where team appears in last_32
        Path last32File = simulationDir.resolve("matchup_paths_last_32.csv");
        if (!Files.exists(last32File)) {
            System.out.println("\n  No simulation matchup-path data. Run --mode=groups first to generate last_32 matchups.\n");
            return;
        }

        List<String> last32Lines = Files.readAllLines(last32File);
        String[] last32Headers   = last32Lines.get(0).split(",", -1);
        int t1Idx   = indexOf(last32Headers, "team1");
        int t2Idx   = indexOf(last32Headers, "team2");
        int mIdIdx  = indexOf(last32Headers, "match_id");

        // Parse compound filter: team:england||team:france&last_16:Mexico&group:L2
        List<List<PathCondition>> andConditions = new ArrayList<>();
        List<String> slotPatterns = new ArrayList<>();
        List<String> difficultyFilters = new ArrayList<>();
        List<String> teams = parseFilterConditions(rawFilter, andConditions, slotPatterns, difficultyFilters);

        if (teams.isEmpty()) {
            log.error("--filter must include a team name (e.g. --filter=England or --filter=team:England&last_16:Mexico)");
            System.exit(2);
        }

        for (String teamLower : teams) {
            handleTeam(tournament, teamLower, pathMode, rawFilter, andConditions, slotPatterns, difficultyFilters,
                last32Lines, t1Idx, t2Idx, mIdIdx,
                matchRows, matchHeaders, matchToStage, winnerToNextMatch);
        }
    }

    private void handleTeam(String tournament,
                             String teamLower,
                             String pathMode,
                             String rawFilter,
                             List<List<PathCondition>> andConditions,
                             List<String> slotPatterns,
                             List<String> difficultyFilters,
                             List<String> last32Lines,
                             int t1Idx, int t2Idx, int mIdIdx,
                             Map<String, List<String[]>> matchRows,
                             Map<String, String[]> matchHeaders,
                             Map<String, String> matchToStage,
                             Map<String, String> winnerToNextMatch) {
        Set<String> startingMatchIds = new LinkedHashSet<>();
        for (int i = 1; i < last32Lines.size(); i++) {
            String line = last32Lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] cols = line.split(",", -1);
            String t1 = eloCalculator.extractTeamName(valueAt(cols, t1Idx));
            String t2 = eloCalculator.extractTeamName(valueAt(cols, t2Idx));
            if (t1.toLowerCase().contains(teamLower) || t2.toLowerCase().contains(teamLower)) {
                startingMatchIds.add(valueAt(cols, mIdIdx));
            }
        }

        if (startingMatchIds.isEmpty()) {
            System.out.println("\n  No simulation matchup-path data found for: " + teamLower);
            System.out.println("  Check that --filter matches a team name in the matchup files.\n");
            return;
        }

        // DFS from each starting matchId to build all possible paths
        // For primary mode, only explore primary rows — avoids exponential blowup from alt branches
        boolean primaryOnly = "predicted".equalsIgnoreCase(pathMode);
        List<List<MatchStep>> allPaths = new ArrayList<>();
        for (String startMatchId : startingMatchIds) {
            dfs(startMatchId, teamLower, primaryOnly, andConditions, slotPatterns, matchRows, matchHeaders, matchToStage,
                winnerToNextMatch, new ArrayList<>(), allPaths);
            if (allPaths.size() >= DFS_CAP) break;
        }

        // Filter by pathMode
        List<List<MatchStep>> filteredPaths;
        if ("predicted".equalsIgnoreCase(pathMode)) {
            filteredPaths = allPaths.stream()
                .filter(p -> p.stream().allMatch(s -> s.isPrimary))
                .collect(Collectors.toList());
        } else if ("alt".equalsIgnoreCase(pathMode)) {
            filteredPaths = allPaths.stream()
                .filter(p -> p.stream().anyMatch(s -> !s.isPrimary))
                .collect(Collectors.toList());
        } else {
            filteredPaths = new ArrayList<>(allPaths);
        }

        // Apply compound stage conditions (AND of OR-groups)
        if (!andConditions.isEmpty()) {
            filteredPaths = filteredPaths.stream()
                .filter(path -> andConditions.stream()
                    .allMatch(orGroup -> orGroup.stream().anyMatch(cond -> cond.matches(path))))
                .collect(Collectors.toList());
        }

        // Apply group/slot filter
        if (!slotPatterns.isEmpty()) {
            filteredPaths = filteredPaths.stream()
                .filter(path -> !path.isEmpty() && slotPatterns.stream()
                    .anyMatch(p -> PathCondition.wildcardMatch(path.get(0).slot.toLowerCase(), p)))
                .collect(Collectors.toList());
        }

        // Apply difficulty filter
        if (!difficultyFilters.isEmpty()) {
            filteredPaths = filteredPaths.stream()
                .filter(path -> difficultyFilters.stream()
                    .anyMatch(f -> matchesDifficulty(difficultyScore(path), f)))
                .collect(Collectors.toList());
        }

        // Sort: hardest first → most rounds reached → primary before alt
        filteredPaths.sort(Comparator
            .comparingInt((List<MatchStep> p) -> -difficultyScore(p))
            .thenComparingInt((List<MatchStep> p) -> -p.size())
            .thenComparing(p -> p.stream().allMatch(s -> s.isPrimary) ? 0 : 1));

        // Resolve display name from a matching row
        String displayTeam = resolveDisplayName(teamLower, allPaths);

        System.out.println();
        System.out.println(ConsoleReporter.colorSectionHeader("=== " + displayTeam + " — tournament paths (" + pathMode + ") ==="));
        System.out.println();

        if (filteredPaths.isEmpty()) {
            System.out.println("  No paths found for --path=" + pathMode + ". Try --path=both to see all scenarios.");
        } else {
            int page = Math.max(1, consoleReporter.getPageNumber());
            int totalPages = (int) Math.ceil((double) filteredPaths.size() / MAX_DISPLAY_PATHS);
            page = Math.min(page, totalPages);
            int fromIdx = (page - 1) * MAX_DISPLAY_PATHS;
            int toIdx   = Math.min(fromIdx + MAX_DISPLAY_PATHS, filteredPaths.size());
            List<List<MatchStep>> toDisplay = filteredPaths.subList(fromIdx, toIdx);
            int slotW = "GROUP".length();
            Map<Integer, Integer> stageW  = new HashMap<>();
            Map<Integer, String>  colStages = new HashMap<>();
            int resultW = "RESULT".length();

            for (List<MatchStep> path : toDisplay) {
                if (!path.isEmpty()) slotW = Math.max(slotW, path.get(0).slot.length());
                for (int i = 0; i < path.size(); i++) {
                    MatchStep step = path.get(i);
                    String text = step.opponentFlag + step.opponent + " (" + step.winPct + "%)";
                    // +2 safety margin: some terminals render flag emoji as 4 cols instead of 2
                    stageW.put(i, Math.max(stageW.getOrDefault(i, 0), ConsoleReporter.displayWidth(text) + 2));
                    if (!colStages.containsKey(i)) {
                        String label = stageLabel(step.stage);
                        colStages.put(i, label);
                        // Ensure column is wide enough for "STAGE (win %)" header
                        stageW.put(i, Math.max(stageW.getOrDefault(i, 0), (label + " (win %)").length()));
                    }
                }
                boolean won = !path.isEmpty() && path.get(path.size() - 1).teamWins
                    && "FINAL".equals(path.get(path.size() - 1).stage);
                String result = won
                    ? consoleReporter.flagFor(displayTeam) + displayTeam + " \uD83C\uDFC6"
                    : "\u2717";
                resultW = Math.max(resultW, ConsoleReporter.displayWidth(result) + 2);
            }

            int numCols = stageW.size();
            int diffW = "DIFFICULTY".length();
            for (List<MatchStep> path : toDisplay) {
                String diff = difficultyLabel(difficultyScore(path)) + " (" + difficultyScore(path) + "%)";
                diffW = Math.max(diffW, diff.length());
            }

            System.out.println(tableBorder('┌', '┬', '┐', slotW, stageW, numCols, resultW, diffW));
            System.out.println(tableHeaderRow("GROUP", colStages, slotW, stageW, numCols, resultW, diffW));
            System.out.println(tableBorder('├', '┼', '┤', slotW, stageW, numCols, resultW, diffW));

            for (List<MatchStep> path : toDisplay) {
                System.out.println(tableDataRow(path, displayTeam, slotW, stageW, numCols, resultW, diffW));
            }
            System.out.println(tableBorder('└', '┴', '┘', slotW, stageW, numCols, resultW, diffW));

            System.out.println("  Page " + page + " of " + totalPages
                + " (" + filteredPaths.size() + " total paths)");
            if (page < totalPages) {
                System.out.println("  Next page: --filter=\"" + rawFilter + "\" --page=" + (page + 1));
            }
        }
        System.out.println();
    }

    private void dfs(String matchId,
                     String teamLower,
                     boolean primaryOnly,
                     List<List<PathCondition>> andConditions,
                     List<String> slotPatterns,
                     Map<String, List<String[]>> matchRows,
                     Map<String, String[]> matchHeaders,
                     Map<String, String> matchToStage,
                     Map<String, String> winnerToNextMatch,
                     List<MatchStep> current,
                     List<List<MatchStep>> allPaths) {
        if (allPaths.size() >= DFS_CAP) return;

        List<String[]> rows    = matchRows.get(matchId);
        String[]       headers = matchHeaders.get(matchId);

        if (rows == null || headers == null) {
            // Round file not yet generated — record incomplete path (if any steps exist)
            if (!current.isEmpty()) allPaths.add(new ArrayList<>(current));
            return;
        }

        int team1Idx = indexOf(headers, "team1");
        int team2Idx = indexOf(headers, "team2");
        int pathIdx  = indexOf(headers, "path");
        int predIdx  = indexOf(headers, "prediction");
        if (predIdx < 0) predIdx = indexOf(headers, "predicted_winner");

        String stage = matchToStage.getOrDefault(matchId, "");

        // Sort rows so primary comes before alt — ensures primary paths are explored first
        // before the DFS cap can be hit by alt branches
        List<String[]> sortedRows = new ArrayList<>(rows);
        sortedRows.sort((a, b) -> {
            String pa = valueAt(a, pathIdx);
            String pb = valueAt(b, pathIdx);
            boolean aPrimary = "predicted".equalsIgnoreCase(pa);
            boolean bPrimary = "predicted".equalsIgnoreCase(pb);
            if (aPrimary && !bPrimary) return -1;
            if (!aPrimary && bPrimary) return 1;
            return 0;
        });

        // Deduplicate by (path, opponent) so we don't produce identical branches
        Set<String> seen = new LinkedHashSet<>();

        for (String[] cols : sortedRows) {
            if (allPaths.size() >= DFS_CAP) break;

            String t1    = eloCalculator.extractTeamName(valueAt(cols, team1Idx));
            String t2    = eloCalculator.extractTeamName(valueAt(cols, team2Idx));
            boolean t1m  = t1.toLowerCase().contains(teamLower);
            boolean t2m  = t2.toLowerCase().contains(teamLower);
            if (!t1m && !t2m) continue;

            String opponent  = t1m ? t2 : t1;
            String rowPath   = valueAt(cols, pathIdx);
            if (primaryOnly && !"predicted".equalsIgnoreCase(rowPath)) continue;
            String dedupeKey = rowPath + "|" + opponent;
            if (!seen.add(dedupeKey)) continue;

            // Prune branches that can't satisfy a stage condition targeting the current stage.
            // If an AND-group has ALL conditions targeting this stage and none match the opponent,
            // no path through this row can satisfy that requirement — skip it.
            boolean pruned = false;
            for (List<PathCondition> orGroup : andConditions) {
                boolean allSameStage = true;
                for (PathCondition c : orGroup) {
                    if (!c.stage.equalsIgnoreCase(stage)) { allSameStage = false; break; }
                }
                if (allSameStage) {
                    boolean anyMatch = false;
                    for (PathCondition c : orGroup) {
                        if (c.matchesOpponent(stage, opponent)) { anyMatch = true; break; }
                    }
                    if (!anyMatch) { pruned = true; break; }
                }
            }
            if (pruned) continue;

            String pred    = valueAt(cols, predIdx);
            String winner  = eloCalculator.parseTeamFromPrediction(pred);
            int    pct     = eloCalculator.parsePctFromPrediction(pred);
            boolean teamWins = winner.toLowerCase().contains(teamLower);
            // Convert to the selected team's win probability (not the predicted winner's)
            int teamPct = teamWins ? pct : (100 - pct);
            boolean isPrimary = "predicted".equalsIgnoreCase(rowPath);

            // Determine the slot label (e.g. "L1", "L2") from the team column
            String teamDisplayCol = t1m ? valueAt(cols, team1Idx) : valueAt(cols, team2Idx);
            String slot = extractSlot(teamDisplayCol);

            // Prune at first step: if group/slot filter is set and slot doesn't match, skip
            if (!slotPatterns.isEmpty() && current.isEmpty()) {
                boolean slotMatch = false;
                for (String p : slotPatterns) {
                    if (PathCondition.wildcardMatch(slot.toLowerCase(), p)) { slotMatch = true; break; }
                }
                if (!slotMatch) continue;
            }

            // Skip if this opponent was already faced in the current path (can't play same team twice)
            String opponentLower = opponent.toLowerCase();
            boolean alreadyFaced = false;
            for (MatchStep prev : current) {
                if (prev.opponent.toLowerCase().equals(opponentLower)) { alreadyFaced = true; break; }
            }
            if (alreadyFaced) continue;

            MatchStep step = new MatchStep(matchId, stage, slot, opponent,
                consoleReporter.flagFor(opponent), teamWins, teamPct, isPrimary);
            current.add(step);

            if (!teamWins) {
                // Eliminated — leaf node
                allPaths.add(new ArrayList<>(current));
            } else {
                // Won — follow bracket to next match
                String numericPart = matchId.replaceAll("[^0-9]", "");
                String winnerSlot  = "W" + numericPart;
                String nextMatchId = winnerToNextMatch.get(winnerSlot);
                if (nextMatchId == null) {
                    // Won the final — leaf node
                    allPaths.add(new ArrayList<>(current));
                } else {
                    dfs(nextMatchId, teamLower, primaryOnly, andConditions, slotPatterns, matchRows, matchHeaders, matchToStage,
                        winnerToNextMatch, current, allPaths);
                }
            }

            current.remove(current.size() - 1);
        }
    }

    /**
     * Extracts the slot label (e.g. "L1", "L2", "W80") from a display like "L1(England)" or
     * "W80(L1(England))". Returns the outermost label before the first '('.
     */
    private String extractSlot(String display) {
        if (display == null) return "";
        int paren = display.indexOf('(');
        return paren > 0 ? display.substring(0, paren) : display;
    }

    private String tableBorder(char left, char mid, char right,
                                int slotW, Map<Integer, Integer> stageW, int numCols, int resultW, int diffW) {
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        sb.append(repeat('─', slotW + 2));
        for (int i = 0; i < numCols; i++) {
            sb.append(mid);
            sb.append(repeat('─', stageW.getOrDefault(i, 0) + 2));
        }
        sb.append(mid);
        sb.append(repeat('─', resultW + 2));
        sb.append(mid);
        sb.append(repeat('─', diffW + 2));
        sb.append(right);
        return ConsoleReporter.colorBorder(sb.toString());
    }

    private String tableHeaderRow(String groupLabel, Map<Integer, String> colStages,
                                   int slotW, Map<Integer, Integer> stageW, int numCols, int resultW, int diffW) {
        StringBuilder sb = new StringBuilder();
        sb.append("│ ").append(ConsoleReporter.boldHeader(padRight(groupLabel, slotW))).append(" │");
        for (int i = 0; i < numCols; i++) {
            String label = colStages.getOrDefault(i, "") + " (win %)";
            sb.append(" ").append(ConsoleReporter.boldHeader(padRight(label, stageW.getOrDefault(i, 0)))).append(" │");
        }
        sb.append(" ").append(ConsoleReporter.boldHeader(padRight("RESULT", resultW))).append(" │");
        sb.append(" ").append(ConsoleReporter.boldHeader(padRight("DIFFICULTY", diffW))).append(" │");
        return sb.toString();
    }

    private String tableDataRow(List<MatchStep> path, String displayTeam,
                                 int slotW, Map<Integer, Integer> stageW, int numCols, int resultW, int diffW) {
        if (path.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("│ ").append(padRight(path.get(0).slot, slotW)).append(" │");
        boolean teamWon = path.get(path.size() - 1).teamWins && "FINAL".equals(path.get(path.size() - 1).stage);
        for (int i = 0; i < numCols; i++) {
            sb.append(" ");
            if (i < path.size()) {
                MatchStep step = path.get(i);
                String text = step.opponentFlag + step.opponent + " (" + step.winPct + "%)";
                String padded = ConsoleReporter.padToDisplayWidth(text, stageW.getOrDefault(i, 0));
                sb.append(ConsoleReporter.colorMatchStep(padded, step.winPct));
            } else {
                sb.append(repeat(' ', stageW.getOrDefault(i, 0)));
            }
            sb.append(" │");
        }
        String resultPlain = teamWon
            ? consoleReporter.flagFor(displayTeam) + displayTeam + " \uD83C\uDFC6"
            : "\u2717";
        String resultPadded = ConsoleReporter.padToDisplayWidth(resultPlain, resultW);
        sb.append(" ").append(ConsoleReporter.colorResult(resultPadded, teamWon)).append(" │");
        int score = difficultyScore(path);
        String diffLabel = difficultyLabel(score);
        String diffPlain = diffLabel + " (" + score + "%)";
        String diffPadded = padRight(diffPlain, diffW);
        sb.append(" ").append(ConsoleReporter.colorDifficulty(diffPadded, diffLabel)).append(" │");
        return sb.toString();
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    /**
     * Formats a path as a table row. Each column is padded to its max width.
     * Shorter paths leave trailing columns empty. Result (✗ or 🏆) is appended at end.
     * @deprecated Use tableDataRow instead.
     */
    private String formatPath(List<MatchStep> path, String teamName, int slotWidth,
                               Map<Integer, Integer> colWidths, int numCols) {
        if (path.isEmpty()) return "(no data)";
        StringBuilder sb = new StringBuilder();
        sb.append(ConsoleReporter.padToDisplayWidth(path.get(0).slot, slotWidth));
        boolean teamWon = false;
        for (int i = 0; i < numCols; i++) {
            sb.append("    ");
            if (i < path.size()) {
                MatchStep step = path.get(i);
                String stepText = step.opponentFlag + step.opponent + " (" + step.winPct + "%)";
                sb.append(ConsoleReporter.padToDisplayWidth(stepText, colWidths.getOrDefault(i, 0)));
                if (i == path.size() - 1 && step.teamWins && "FINAL".equals(step.stage)) {
                    teamWon = true;
                }
            } else {
                int w = colWidths.getOrDefault(i, 0);
                for (int s = 0; s < w; s++) sb.append(' ');
            }
        }
        sb.append("    ");
        if (teamWon) {
            sb.append(ConsoleReporter.getFlag(teamName)).append(teamName).append("  (🏆 WINS!)");
        } else {
            sb.append("✗");
        }
        return sb.toString();
    }

    /** Resolves the display-form team name with proper title casing from the parsed team name. */
    private String resolveDisplayName(String teamLower, List<List<MatchStep>> allPaths) {
        if (teamLower.isEmpty()) return "";
        String[] words = teamLower.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0) sb.append(" ");
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    private static String stageLabel(String stage) {
        if (stage == null) return "";
        switch (stage.toUpperCase()) {
            case "LAST_32":  return "LAST 32";
            case "LAST_16":  return "LAST 16";
            case "QUARTER":  return "QUARTER-FINAL";
            case "SEMI":     return "SEMI-FINAL";
            case "FINAL":    return "FINAL";
            default:         return stage;
        }
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private static int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private static String valueAt(String[] cols, int idx) {
        return idx >= 0 && idx < cols.length ? cols[idx].trim() : "";
    }
}
