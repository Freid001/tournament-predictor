package com.tournamentpredictor.service.simulation;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.ExpectedGoalsCalculator;
import com.tournamentpredictor.service.util.PathFatigueCalculator;
import com.tournamentpredictor.service.util.TeamEloSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class SimulationHandler {
    public static final int DEFAULT_RUNS = 25_000;
    public static final long DEFAULT_SEED = 20260605L;
    private static final double BEST_REALISTIC_THRESHOLD_PCT = 1.0;
    private static final String OUTPUT_HEADER = "team,reach_last_16,reach_last_8,reach_last_4,reach_final,champion,predicted_finish,predicted_finish_pct,best_realistic_finish,best_realistic_pct,simulation_runs,simulation_seed";
    private static final String PATH_OUTPUT_HEADER = "team,finish,path,count,percentage,simulation_runs,simulation_seed";
    private static final String SCORELINE_OUTPUT_HEADER = "stage,match_id,team1,team2,scoreline,winner,count,scoreline_pct,matchup_runs,matchup_pct,simulation_runs,simulation_seed";

    private final CsvLoader loader;
    private final Path projectRoot;
    private final ExpectedGoalsCalculator expectedGoalsCalculator;
    private final EloCalculator eloCalculator;
    private final PathFatigueCalculator pathFatigueCalculator;
    private final int runs;
    private final long seed;

    public SimulationHandler(CsvLoader loader, Path projectRoot) {
        this(loader, projectRoot, new ExpectedGoalsCalculator(), new EloCalculator(), new PathFatigueCalculator(), DEFAULT_RUNS, DEFAULT_SEED);
    }

    public SimulationHandler(CsvLoader loader, Path projectRoot, ExpectedGoalsCalculator expectedGoalsCalculator,
                             EloCalculator eloCalculator, int runs, long seed) {
        this(loader, projectRoot, expectedGoalsCalculator, eloCalculator, new PathFatigueCalculator(), runs, seed);
    }

    public SimulationHandler(CsvLoader loader, Path projectRoot, ExpectedGoalsCalculator expectedGoalsCalculator,
                             EloCalculator eloCalculator, PathFatigueCalculator pathFatigueCalculator,
                             int runs, long seed) {
        this.loader = Objects.requireNonNull(loader);
        this.projectRoot = Objects.requireNonNull(projectRoot);
        this.expectedGoalsCalculator = Objects.requireNonNull(expectedGoalsCalculator);
        this.eloCalculator = Objects.requireNonNull(eloCalculator);
        this.pathFatigueCalculator = Objects.requireNonNull(pathFatigueCalculator);
        this.runs = runs;
        this.seed = seed;
    }

    public void handle(String tournament) throws IOException {
        Path last32 = projectRoot.resolve("data").resolve("predictions").resolve(tournament).resolve("last_32.csv");
        if (!Files.exists(last32)) {
            throw new IOException("last_32.csv not found: " + last32 + ". Run mode=groups first.");
        }
        Map<String, Integer> eloRatings = loader.loadTournamentElo(tournament);
        Map<String, TeamEloSnapshot> snapshots = loader.loadTeamSnapshots(tournament);
        List<CsvLoader.BracketEntry> brackets = loader.loadBrackets(tournament);
        SimulationResult result = simulateLast32(Files.readAllLines(last32), brackets, eloRatings, snapshots);
        writeOutput(tournament, result);
        writePathOutput(tournament, result);
        writeScorelineOutput(tournament, result);
        System.out.println("Simulation complete: " + runs + " runs from Last 32. Output: "
                + outputPath(tournament).toAbsolutePath());
    }

    SimulationResult simulateLast32(List<String> last32Rows, List<CsvLoader.BracketEntry> brackets,
                                    Map<String, Integer> eloRatings) {
        return simulateLast32(last32Rows, brackets, eloRatings, Map.of());
    }

    SimulationResult simulateLast32(List<String> last32Rows, List<CsvLoader.BracketEntry> brackets,
                                    Map<String, Integer> eloRatings,
                                    Map<String, TeamEloSnapshot> snapshots) {
        Map<String, MatchDefinition> matchDefinitions = loadMatchDefinitions(last32Rows, brackets);
        Map<String, TeamCounts> counts = new LinkedHashMap<>();
        for (MatchDefinition match : matchDefinitions.values()) {
            if ("LAST_32".equals(match.stage)) {
                counts.computeIfAbsent(match.team1, TeamCounts::new);
                counts.computeIfAbsent(match.team2, TeamCounts::new);
            }
        }

        Map<PathKey, Integer> pathCounts = new HashMap<>();
        Map<ScorelineKey, Integer> scorelineCounts = new HashMap<>();
        Random random = new Random(seed);
        for (int run = 0; run < runs; run++) {
            RunState runState = new RunState();
            playStage("LAST_32", "last_32", "last_16", matchDefinitions, runState, counts, eloRatings, snapshots, scorelineCounts, random);
            playStage("LAST_16", "last_16", "last_8", matchDefinitions, runState, counts, eloRatings, snapshots, scorelineCounts, random);
            playStage("QUARTER", "last_8", "last_4", matchDefinitions, runState, counts, eloRatings, snapshots, scorelineCounts, random);
            playStage("SEMI", "last_4", "final", matchDefinitions, runState, counts, eloRatings, snapshots, scorelineCounts, random);
            List<MatchDefinition> finals = matchDefinitions.values().stream()
                    .filter(match -> "FINAL".equals(match.stage))
                    .toList();
            if (finals.size() != 1) {
                throw new IllegalStateException("Expected one final match, found " + finals.size());
            }
            PlayedMatch finalResult = playMatch(finals.get(0), "final", runState, eloRatings, snapshots, scorelineCounts, random);
            runState.winners.put(finals.get(0).matchId, finalResult.winner);
            counts.computeIfAbsent(finalResult.winner, TeamCounts::new).champion++;
            recordRunPaths(counts.keySet(), finalResult.winner, runState, pathCounts);
        }
        return new SimulationResult(counts.values().stream()
                .sorted(Comparator.comparing((TeamCounts c) -> c.champion).reversed().thenComparing(c -> c.team))
                .toList(), sortedPathCounts(pathCounts), sortedScorelineCounts(scorelineCounts), runs);
    }

    private void playStage(String stage, String fatigueStage, String reachedRound,
                           Map<String, MatchDefinition> matchDefinitions,
                           RunState runState, Map<String, TeamCounts> counts,
                           Map<String, Integer> eloRatings, Map<String, TeamEloSnapshot> snapshots,
                           Map<ScorelineKey, Integer> scorelineCounts, Random random) {
        for (MatchDefinition match : matchDefinitions.values()) {
            if (!stage.equals(match.stage)) {
                continue;
            }
            PlayedMatch result = playMatch(match, fatigueStage, runState, eloRatings, snapshots, scorelineCounts, random);
            runState.winners.put(match.matchId, result.winner);
            TeamCounts teamCounts = counts.computeIfAbsent(result.winner, TeamCounts::new);
            teamCounts.increment(reachedRound);
        }
    }

    private PlayedMatch playMatch(MatchDefinition match, String fatigueStage, RunState runState,
                                  Map<String, Integer> eloRatings,
                                  Map<String, TeamEloSnapshot> snapshots,
                                  Map<ScorelineKey, Integer> scorelineCounts, Random random) {
        String team1 = resolve(match.team1, runState.winners);
        String team2 = resolve(match.team2, runState.winners);
        int team1BaseElo = eloRatings.getOrDefault(team1, 0);
        int team2BaseElo = eloRatings.getOrDefault(team2, 0);
        int team1Elo = adjustedElo(team1, team1BaseElo, runState, snapshots);
        int team2Elo = adjustedElo(team2, team2BaseElo, runState, snapshots);
        ExpectedGoalsCalculator.Projection projection = expectedGoalsCalculator.project(team1, team2, team1Elo, team2Elo);
        ExpectedGoalsCalculator.SampledScoreline scoreline = projection.sampleScoreline(random);
        boolean team1Wins = scoreline.team1Advances();
        String winner = team1Wins ? team1 : team2;
        scorelineCounts.merge(new ScorelineKey(fatigueStage, match.matchId, team1, team2, scoreline.scoreText(), winner), 1, Integer::sum);
        String loser = team1Wins ? team2 : team1;
        int loserElo = team1Wins ? team2BaseElo : team1BaseElo;
        runState.pathByTeam.computeIfAbsent(winner, ignored -> new ArrayList<>()).add(loser);
        if (!"final".equals(fatigueStage)) {
            addPathFatigue(winner, loserElo, fatigueStage, runState);
        }
        return new PlayedMatch(winner, loser);
    }

    private int adjustedElo(String team, int baseElo, RunState runState, Map<String, TeamEloSnapshot> snapshots) {
        int weightedTotal = runState.pathFatigueByTeam.getOrDefault(team, 0);
        int fatigue = pathFatigueCalculator.eloAdjustmentFromWeighted(weightedTotal);
        TeamEloSnapshot snapshot = snapshots != null ? snapshots.get(team) : null;
        int depthLevel = snapshot != null ? snapshot.squadDepthLevel() : 0;
        return baseElo + pathFatigueCalculator.applyDepthMultiplier(fatigue, depthLevel);
    }

    private void addPathFatigue(String winner, int loserElo, String fatigueStage, RunState runState) {
        int contribution = pathFatigueCalculator.knockoutWeightedContribution(loserElo, fatigueStage);
        runState.pathFatigueByTeam.merge(winner, contribution, Integer::sum);
    }

    private void recordRunPaths(Set<String> teams, String champion, RunState runState, Map<PathKey, Integer> pathCounts) {
        for (String team : teams) {
            List<String> path = runState.pathByTeam.getOrDefault(team, List.of());
            String finish = finishForPathLength(path.size(), champion.equals(team));
            String pathText = path.isEmpty() ? "None" : String.join(" > ", path);
            pathCounts.merge(new PathKey(team, finish, pathText), 1, Integer::sum);
        }
    }

    private static String finishForPathLength(int pathLength, boolean champion) {
        if (champion) return "Champion";
        return switch (pathLength) {
            case 0 -> "Exit Last 32";
            case 1 -> "Exit Last 16";
            case 2 -> "Exit QF";
            case 3 -> "Exit SF";
            case 4 -> "Runner-up";
            default -> "Champion";
        };
    }

    private String resolve(String tokenOrTeam, Map<String, String> winners) {
        if (tokenOrTeam != null && tokenOrTeam.matches("W\\d+")) {
            String matchId = "M" + tokenOrTeam.substring(1);
            String winner = winners.get(matchId);
            if (winner == null || winner.isBlank()) {
                throw new IllegalStateException("Winner not available for token " + tokenOrTeam);
            }
            return winner;
        }
        return tokenOrTeam;
    }

    private Map<String, MatchDefinition> loadMatchDefinitions(List<String> last32Rows,
                                                              List<CsvLoader.BracketEntry> brackets) {
        Map<String, MatchDefinition> definitions = new LinkedHashMap<>();
        for (String line : last32Rows) {
            if (line == null || line.isBlank() || line.startsWith("match_id")) {
                continue;
            }
            String[] cols = line.split(",", -1);
            if (cols.length < 5 || !"predicted".equalsIgnoreCase(cols[3].trim())) {
                continue;
            }
            String matchId = cols[0].trim();
            String team1 = eloCalculator.extractTeamName(cols[1].trim());
            String team2 = eloCalculator.extractTeamName(cols[2].trim());
            if (!matchId.isBlank() && !team1.isBlank() && !team2.isBlank()) {
                definitions.putIfAbsent(matchId, new MatchDefinition(matchId, "LAST_32", team1, team2));
            }
        }
        for (CsvLoader.BracketEntry bracket : brackets) {
            if (bracket.matchId == null || bracket.matchId.isBlank()) {
                continue;
            }
            if (!bracket.stage.matches("LAST_16|QUARTER|SEMI|FINAL")) {
                continue;
            }
            definitions.put(bracket.matchId, new MatchDefinition(bracket.matchId, bracket.stage, bracket.token1, bracket.token2));
        }
        return definitions;
    }

    private void writeOutput(String tournament, SimulationResult result) throws IOException {
        Path output = outputPath(tournament);
        Files.createDirectories(output.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(OUTPUT_HEADER);
        for (TeamCounts counts : result.teamCounts()) {
            FinishOutcome predictedFinish = predictedFinish(counts, result.runs());
            FinishOutcome bestRealisticFinish = bestRealisticFinish(counts, result.runs());
            lines.add(String.join(",",
                    csv(counts.team),
                    pct(counts.reachLast16, result.runs()),
                    pct(counts.reachLast8, result.runs()),
                    pct(counts.reachLast4, result.runs()),
                    pct(counts.reachFinal, result.runs()),
                    pct(counts.champion, result.runs()),
                    csv(predictedFinish.label()),
                    pct(predictedFinish.count(), result.runs()),
                    csv(bestRealisticFinish.label()),
                    pct(bestRealisticFinish.count(), result.runs()),
                    String.valueOf(result.runs()),
                    String.valueOf(seed)));
        }
        Files.write(output, lines);
    }

    private void writePathOutput(String tournament, SimulationResult result) throws IOException {
        Path output = pathOutputPath(tournament);
        Files.createDirectories(output.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(PATH_OUTPUT_HEADER);
        for (PathCount pathCount : result.pathCounts()) {
            lines.add(String.join(",",
                    csv(pathCount.team()),
                    csv(pathCount.finish()),
                    csv(pathCount.path()),
                    String.valueOf(pathCount.count()),
                    pct(pathCount.count(), result.runs()),
                    String.valueOf(result.runs()),
                    String.valueOf(seed)));
        }
        Files.write(output, lines);
    }

    private void writeScorelineOutput(String tournament, SimulationResult result) throws IOException {
        Path output = scorelineOutputPath(tournament);
        Files.createDirectories(output.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(SCORELINE_OUTPUT_HEADER);
        Map<MatchupKey, Integer> matchupRuns = matchupRuns(result.scorelineCounts());
        for (ScorelineCount scorelineCount : result.scorelineCounts()) {
            int matchupCount = matchupRuns.getOrDefault(new MatchupKey(scorelineCount.stage(), scorelineCount.matchId(), scorelineCount.team1(), scorelineCount.team2()), 0);
            lines.add(String.join(",",
                    csv(scorelineCount.stage()),
                    csv(scorelineCount.matchId()),
                    csv(scorelineCount.team1()),
                    csv(scorelineCount.team2()),
                    csv(scorelineCount.scoreline()),
                    csv(scorelineCount.winner()),
                    String.valueOf(scorelineCount.count()),
                    pct(scorelineCount.count(), Math.max(1, matchupCount)),
                    String.valueOf(matchupCount),
                    pct(matchupCount, result.runs()),
                    String.valueOf(result.runs()),
                    String.valueOf(seed)));
        }
        Files.write(output, lines);
    }

    private static Map<MatchupKey, Integer> matchupRuns(List<ScorelineCount> scorelineCounts) {
        Map<MatchupKey, Integer> totals = new HashMap<>();
        for (ScorelineCount scorelineCount : scorelineCounts) {
            MatchupKey key = new MatchupKey(scorelineCount.stage(), scorelineCount.matchId(), scorelineCount.team1(), scorelineCount.team2());
            totals.merge(key, scorelineCount.count(), Integer::sum);
        }
        return totals;
    }

    private Path outputPath(String tournament) {
        return projectRoot.resolve("data").resolve("simulations").resolve(tournament).resolve("simulation_last_32.csv");
    }

    private Path pathOutputPath(String tournament) {
        return projectRoot.resolve("data").resolve("simulations").resolve(tournament).resolve("simulation_paths_last_32.csv");
    }

    private Path scorelineOutputPath(String tournament) {
        return projectRoot.resolve("data").resolve("simulations").resolve(tournament).resolve("simulation_scorelines_last_32.csv");
    }

    private static List<PathCount> sortedPathCounts(Map<PathKey, Integer> pathCounts) {
        return pathCounts.entrySet().stream()
                .map(entry -> new PathCount(entry.getKey().team(), entry.getKey().finish(), entry.getKey().path(), entry.getValue()))
                .sorted(Comparator.comparing(PathCount::team)
                        .thenComparing(Comparator.comparingInt(PathCount::count).reversed())
                        .thenComparing(PathCount::finish)
                        .thenComparing(PathCount::path))
                .collect(Collectors.toList());
    }

    private static List<ScorelineCount> sortedScorelineCounts(Map<ScorelineKey, Integer> scorelineCounts) {
        return scorelineCounts.entrySet().stream()
                .map(entry -> new ScorelineCount(
                        entry.getKey().stage(),
                        entry.getKey().matchId(),
                        entry.getKey().team1(),
                        entry.getKey().team2(),
                        entry.getKey().scoreline(),
                        entry.getKey().winner(),
                        entry.getValue()))
                .sorted(Comparator.comparing(ScorelineCount::stage)
                        .thenComparing(ScorelineCount::matchId)
                        .thenComparing(ScorelineCount::team1)
                        .thenComparing(ScorelineCount::team2)
                        .thenComparing(Comparator.comparingInt(ScorelineCount::count).reversed())
                        .thenComparing(ScorelineCount::scoreline)
                        .thenComparing(ScorelineCount::winner))
                .collect(Collectors.toList());
    }

    private static FinishOutcome predictedFinish(TeamCounts counts, int runs) {
        return finishOutcomes(counts, runs).stream()
                .max(Comparator.comparingInt(FinishOutcome::count).thenComparingInt(FinishOutcome::rank))
                .orElse(new FinishOutcome("Exit Last 32", 0, 0));
    }

    private static FinishOutcome bestRealisticFinish(TeamCounts counts, int runs) {
        return finishOutcomes(counts, runs).stream()
                .filter(outcome -> (outcome.count() * 100.0) / runs >= BEST_REALISTIC_THRESHOLD_PCT)
                .max(Comparator.comparingInt(FinishOutcome::rank))
                .orElseGet(() -> predictedFinish(counts, runs));
    }

    private static List<FinishOutcome> finishOutcomes(TeamCounts counts, int runs) {
        return List.of(
                new FinishOutcome("Exit Last 32", runs - counts.reachLast16, 0),
                new FinishOutcome("Exit Last 16", counts.reachLast16 - counts.reachLast8, 1),
                new FinishOutcome("Exit QF", counts.reachLast8 - counts.reachLast4, 2),
                new FinishOutcome("Exit SF", counts.reachLast4 - counts.reachFinal, 3),
                new FinishOutcome("Runner-up", counts.reachFinal - counts.champion, 4),
                new FinishOutcome("Champion", counts.champion, 5)
        );
    }

    private static String pct(int count, int total) {
        return String.format(Locale.ROOT, "%.1f", (count * 100.0) / total);
    }

    private static String csv(String value) {
        if (value == null) return "";
        if (!value.contains(",") && !value.contains("\"") && !value.contains("\n") && !value.contains("\r")) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    record MatchDefinition(String matchId, String stage, String team1, String team2) {
    }

    record SimulationResult(List<TeamCounts> teamCounts, List<PathCount> pathCounts, List<ScorelineCount> scorelineCounts, int runs) {
    }

    private record FinishOutcome(String label, int count, int rank) {
    }

    private record PlayedMatch(String winner, String loser) {
    }

    record PathCount(String team, String finish, String path, int count) {
    }

    record ScorelineCount(String stage, String matchId, String team1, String team2, String scoreline, String winner, int count) {
    }

    private record PathKey(String team, String finish, String path) {
    }

    private record ScorelineKey(String stage, String matchId, String team1, String team2, String scoreline, String winner) {
    }

    private record MatchupKey(String stage, String matchId, String team1, String team2) {
    }

    private static final class RunState {
        final Map<String, String> winners = new HashMap<>();
        final Map<String, Integer> pathFatigueByTeam = new HashMap<>();
        final Map<String, List<String>> pathByTeam = new HashMap<>();
    }

    static final class TeamCounts {
        final String team;
        int reachLast16;
        int reachLast8;
        int reachLast4;
        int reachFinal;
        int champion;

        TeamCounts(String team) {
            this.team = team;
        }

        void increment(String reachedRound) {
            switch (reachedRound) {
                case "last_16" -> reachLast16++;
                case "last_8" -> reachLast8++;
                case "last_4" -> reachLast4++;
                case "final" -> reachFinal++;
                default -> throw new IllegalArgumentException("Unknown reached round: " + reachedRound);
            }
        }
    }
}
