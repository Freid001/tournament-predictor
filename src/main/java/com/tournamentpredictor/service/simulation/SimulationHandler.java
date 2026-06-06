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
import java.util.TreeMap;
import java.util.stream.Collectors;

public class SimulationHandler {
    public static final int DEFAULT_RUNS = 25_000;
    public static final long DEFAULT_SEED = 20260605L;
    private static final double BEST_REALISTIC_THRESHOLD_PCT = 1.0;
    private static final String OUTPUT_HEADER = "team,reach_last_16,reach_last_8,reach_last_4,reach_final,champion,predicted_finish,predicted_finish_pct,best_realistic_finish,best_realistic_pct,simulation_runs,simulation_seed";
    private static final String GROUP_OUTPUT_HEADER = "team,reach_last_32,reach_last_16,reach_last_8,reach_last_4,reach_final,champion,simulation_runs,simulation_seed";
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
        handle(tournament, "last_32");
    }

    public void handle(String tournament, String startRound) throws IOException {
        if ("groups".equalsIgnoreCase(startRound)) {
            handleGroups(tournament);
            return;
        }
        SimulationStage startStage = SimulationStage.forRound(startRound);
        Path input = projectRoot.resolve("data").resolve("predictions").resolve(tournament)
                .resolve(startStage.round + ".csv");
        if (!Files.exists(input)) {
            throw new IOException(startStage.round + ".csv not found: " + input
                    + ". Run the previous tournament stage first.");
        }
        Map<String, Integer> eloRatings = loader.loadTournamentElo(tournament);
        Map<String, TeamEloSnapshot> snapshots = loader.loadTeamSnapshots(tournament);
        List<CsvLoader.BracketEntry> brackets = loader.loadBrackets(tournament);
        SimulationResult result = simulateFromRound(startStage, Files.readAllLines(input), brackets, eloRatings, snapshots);
        writeOutput(tournament, startStage, result);
        writePathOutput(tournament, startStage, result);
        writeScorelineOutput(tournament, startStage, result);
        System.out.println("Simulation complete: " + runs + " runs from " + startStage.label + ". Output: "
                + outputPath(tournament, startStage).toAbsolutePath());
    }


    /**
     * Runs the unconditional tournament forecast: each run samples groups and then carries the
     * actual knockout winners, opponents, and fatigue from that run through the final.
     * This is independent of the primary-only prediction files used by the staged UI workflow.
     */
    public void handleGroups(String tournament) throws IOException {
        GroupSimulationResult result = simulateGroups(loader.loadGroups(tournament),
                loader.loadBrackets(tournament), loader.loadTournamentElo(tournament),
                loader.loadTeamSnapshots(tournament));
        Path output = projectRoot.resolve("data/simulations").resolve(tournament).resolve("simulation_groups.csv");
        Files.createDirectories(output.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(GROUP_OUTPUT_HEADER);
        result.counts().values().stream()
                .sorted(Comparator.comparingInt((GroupTeamCounts c) -> c.champion).reversed().thenComparing(c -> c.team))
                .forEach(c -> lines.add(String.join(",", csv(c.team), pct(c.reachLast32, runs),
                        pct(c.reachLast16, runs), pct(c.reachLast8, runs), pct(c.reachLast4, runs),
                        pct(c.reachFinal, runs), pct(c.champion, runs), String.valueOf(runs), String.valueOf(seed))));
        Files.write(output, lines);
        System.out.println("Group simulation complete: " + runs + " runs. Output: " + output.toAbsolutePath());
    }

    GroupSimulationResult simulateGroups(Map<String, String> positions, List<CsvLoader.BracketEntry> brackets,
                                          Map<String, Integer> eloRatings,
                                          Map<String, TeamEloSnapshot> snapshots) throws IOException {
        Map<String, List<String>> groups = new TreeMap<>();
        positions.forEach((position, team) ->
                groups.computeIfAbsent(position.substring(0, 1), ignored -> new ArrayList<>()).add(team));
        Map<String, GroupTeamCounts> counts = new LinkedHashMap<>();
        positions.values().forEach(team -> counts.putIfAbsent(team, new GroupTeamCounts(team)));
        Map<String, Map<String, String>> thirdPlaceLookup = loadThirdPlaceLookup(brackets);
        Random random = new Random(seed);
        for (int run = 0; run < runs; run++) {
            Map<String, String> qualifiers = new HashMap<>();
            List<GroupStanding> thirds = new ArrayList<>();
            for (Map.Entry<String, List<String>> group : groups.entrySet()) {
                List<GroupStanding> standings = playGroup(group.getKey(), group.getValue(), eloRatings, snapshots, random);
                qualifiers.put(group.getKey() + "1", standings.get(0).team);
                qualifiers.put(group.getKey() + "2", standings.get(1).team);
                thirds.add(standings.get(2));
            }
            thirds.sort(groupStandingComparator(eloRatings));
            List<GroupStanding> bestThirds = thirds.subList(0, Math.min(8, thirds.size()));
            String lookupKey = bestThirds.stream().map(s -> s.group).sorted().collect(Collectors.joining());
            Map<String, String> assignment = thirdPlaceLookup.getOrDefault(lookupKey, Map.of());
            Map<String, String> thirdByGroup = bestThirds.stream()
                    .collect(Collectors.toMap(s -> s.group, s -> s.team));
            Map<String, MatchDefinition> definitions = new LinkedHashMap<>();
            for (CsvLoader.BracketEntry bracket : brackets) {
                SimulationStage stage = SimulationStage.forBracketStage(bracket.stage);
                if (stage == null) continue;
                String team1 = resolveGroupToken(bracket.token1, bracket.matchId, qualifiers, assignment, thirdByGroup);
                String team2 = resolveGroupToken(bracket.token2, bracket.matchId, qualifiers, assignment, thirdByGroup);
                definitions.put(bracket.matchId, new MatchDefinition(bracket.matchId, bracket.stage, team1, team2));
            }
            qualifiers.values().forEach(team -> counts.get(team).reachLast32++);
            bestThirds.forEach(s -> counts.get(s.team).reachLast32++);
            RunState state = new RunState();
            Map<ScorelineKey, Integer> ignoredScorelines = new HashMap<>();
            Map<String, TeamCounts> knockoutCounts = new HashMap<>();
            for (SimulationStage stage : SimulationStage.values()) {
                if (stage == SimulationStage.FINAL) {
                    MatchDefinition match = definitions.values().stream()
                            .filter(m -> stage.bracketStage.equals(m.stage)).findFirst().orElseThrow();
                    PlayedMatch played = playMatch(match, stage.round, state, eloRatings, snapshots, ignoredScorelines, random);
                    state.winners.put(match.matchId, played.winner);
                    counts.get(played.winner).champion++;
                } else {
                    playStage(stage.bracketStage, stage.round, stage.reachedRound, definitions, state, knockoutCounts,
                            eloRatings, snapshots, ignoredScorelines, random);
                }
            }
            knockoutCounts.forEach((team, c) -> {
                GroupTeamCounts total = counts.get(team);
                total.reachLast16 += c.reachLast16;
                total.reachLast8 += c.reachLast8;
                total.reachLast4 += c.reachLast4;
                total.reachFinal += c.reachFinal;
            });
        }
        return new GroupSimulationResult(counts);
    }

    private List<GroupStanding> playGroup(String group, List<String> teams,
                                           Map<String, Integer> eloRatings,
                                           Map<String, TeamEloSnapshot> snapshots, Random random) {
        Map<String, GroupStanding> table = new HashMap<>();
        teams.forEach(team -> table.put(team, new GroupStanding(group, team)));
        for (int i = 0; i < teams.size(); i++) {
            for (int j = i + 1; j < teams.size(); j++) {
                String team1 = teams.get(i);
                String team2 = teams.get(j);
                TeamEloSnapshot profile1 = snapshots.get(team1);
                TeamEloSnapshot profile2 = snapshots.get(team2);
                ExpectedGoalsCalculator.SampledScoreline score = expectedGoalsCalculator
                        .project(team1, team2, eloRatings.getOrDefault(team1, 0), eloRatings.getOrDefault(team2, 0),
                                profile1 != null ? profile1.attackQuality() : 0,
                                profile1 != null ? profile1.defenceQuality() : 0,
                                profile2 != null ? profile2.attackQuality() : 0,
                                profile2 != null ? profile2.defenceQuality() : 0)
                        .sampleScoreline(random);
                GroupStanding a = table.get(team1);
                GroupStanding b = table.get(team2);
                a.goalsFor += score.team1Goals();
                a.goalsAgainst += score.team2Goals();
                b.goalsFor += score.team2Goals();
                b.goalsAgainst += score.team1Goals();
                if (score.team1Goals() > score.team2Goals()) a.points += 3;
                else if (score.team1Goals() < score.team2Goals()) b.points += 3;
                else {
                    a.points++;
                    b.points++;
                }
            }
        }
        return table.values().stream().sorted(groupStandingComparator(eloRatings)).toList();
    }

    private static Comparator<GroupStanding> groupStandingComparator(Map<String, Integer> eloRatings) {
        return Comparator.comparingInt((GroupStanding s) -> s.points).reversed()
                .thenComparing(Comparator.comparingInt(GroupStanding::goalDifference).reversed())
                .thenComparing(Comparator.comparingInt((GroupStanding s) -> s.goalsFor).reversed())
                .thenComparing(Comparator.comparingInt((GroupStanding s) ->
                        eloRatings.getOrDefault(s.team, 0)).reversed())
                .thenComparing(s -> s.team);
    }

    private static String resolveGroupToken(String token, String matchId, Map<String, String> qualifiers,
                                            Map<String, String> assignment, Map<String, String> thirdByGroup) {
        if (token == null || token.startsWith("W")) return token;
        if (token.matches("[A-L][12]")) return qualifiers.get(token);
        if (token.matches("[A-L]+3")) return thirdByGroup.get(assignment.get(matchId));
        return token;
    }

    private Map<String, Map<String, String>> loadThirdPlaceLookup(List<CsvLoader.BracketEntry> brackets)
            throws IOException {
        Map<String, String> columnToMatch = new HashMap<>();
        for (CsvLoader.BracketEntry bracket : brackets) {
            if (!"LAST_32".equalsIgnoreCase(bracket.stage)) continue;
            String winnerToken = bracket.token1 != null && bracket.token1.matches("[A-L]1")
                    ? bracket.token1 : bracket.token2;
            String thirdToken = bracket.token1 != null && bracket.token1.matches("[A-L]+3")
                    ? bracket.token1 : bracket.token2;
            if (winnerToken != null && winnerToken.matches("[A-L]1")
                    && thirdToken != null && thirdToken.matches("[A-L]+3")) {
                columnToMatch.put("1" + winnerToken.charAt(0), bracket.matchId);
            }
        }
        List<String> lines = Files.readAllLines(projectRoot.resolve("data/bracket/third_place_lookup.csv"));
        String[] headers = lines.get(0).split(",", -1);
        Map<String, Map<String, String>> result = new HashMap<>();
        for (int row = 1; row < lines.size(); row++) {
            String[] cols = lines.get(row).split(",", -1);
            Map<String, String> assignments = new HashMap<>();
            for (int i = 1; i < Math.min(headers.length, cols.length); i++) {
                String matchId = columnToMatch.get(headers[i]);
                if (matchId != null) assignments.put(matchId, cols[i]);
            }
            result.put(cols[0], assignments);
        }
        return result;
    }

    SimulationResult simulateLast32(List<String> last32Rows, List<CsvLoader.BracketEntry> brackets,
                                    Map<String, Integer> eloRatings) {
        return simulateLast32(last32Rows, brackets, eloRatings, Map.of());
    }

    SimulationResult simulateLast32(List<String> last32Rows, List<CsvLoader.BracketEntry> brackets,
                                    Map<String, Integer> eloRatings,
                                    Map<String, TeamEloSnapshot> snapshots) {
        return simulateFromRound(SimulationStage.LAST_32, last32Rows, brackets, eloRatings, snapshots);
    }

    SimulationResult simulateFromRound(SimulationStage startStage, List<String> startRows,
                                       List<CsvLoader.BracketEntry> brackets,
                                       Map<String, Integer> eloRatings,
                                       Map<String, TeamEloSnapshot> snapshots) {
        StartData startData = loadStartData(startStage, startRows, brackets);
        Map<String, MatchDefinition> matchDefinitions = startData.matchDefinitions();
        Map<String, TeamCounts> counts = new LinkedHashMap<>();
        for (MatchDefinition match : matchDefinitions.values()) {
            if (startStage.bracketStage.equals(match.stage)) {
                initialiseCounts(counts.computeIfAbsent(match.team1, TeamCounts::new), startStage);
                initialiseCounts(counts.computeIfAbsent(match.team2, TeamCounts::new), startStage);
            }
        }

        Map<PathKey, Integer> pathCounts = new HashMap<>();
        Map<ScorelineKey, Integer> scorelineCounts = new HashMap<>();
        Random random = new Random(seed);
        for (int run = 0; run < runs; run++) {
            RunState runState = new RunState();
            runState.pathFatigueByTeam.putAll(startData.pathFatigueByTeam());
            for (SimulationStage stage : SimulationStage.values()) {
                if (stage.ordinal() < startStage.ordinal()) continue;
                if (stage == SimulationStage.FINAL) {
                    List<MatchDefinition> finals = matchDefinitions.values().stream()
                            .filter(match -> stage.bracketStage.equals(match.stage))
                            .toList();
                    if (finals.size() != 1) {
                        throw new IllegalStateException("Expected one final match, found " + finals.size());
                    }
                    PlayedMatch finalResult = playMatch(finals.get(0), stage.round, runState,
                            eloRatings, snapshots, scorelineCounts, random);
                    runState.winners.put(finals.get(0).matchId, finalResult.winner);
                    counts.computeIfAbsent(finalResult.winner, TeamCounts::new).champion++;
                    recordRunPaths(counts.keySet(), finalResult.winner, runState, pathCounts, startStage);
                } else {
                    playStage(stage.bracketStage, stage.round, stage.reachedRound, matchDefinitions,
                            runState, counts, eloRatings, snapshots, scorelineCounts, random);
                }
            }
        }
        return new SimulationResult(counts.values().stream()
                .sorted(Comparator.comparing((TeamCounts c) -> c.champion).reversed().thenComparing(c -> c.team))
                .toList(), sortedPathCounts(pathCounts), sortedScorelineCounts(scorelineCounts), runs);
    }

    private void initialiseCounts(TeamCounts counts, SimulationStage startStage) {
        if (startStage.ordinal() >= SimulationStage.LAST_16.ordinal()) counts.reachLast16 = runs;
        if (startStage.ordinal() >= SimulationStage.LAST_8.ordinal()) counts.reachLast8 = runs;
        if (startStage.ordinal() >= SimulationStage.LAST_4.ordinal()) counts.reachLast4 = runs;
        if (startStage.ordinal() >= SimulationStage.FINAL.ordinal()) counts.reachFinal = runs;
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

    private void recordRunPaths(Set<String> teams, String champion, RunState runState,
                                Map<PathKey, Integer> pathCounts, SimulationStage startStage) {
        for (String team : teams) {
            List<String> path = runState.pathByTeam.getOrDefault(team, List.of());
            String finish = finishForPathLength(startStage, path.size(), champion.equals(team));
            String pathText = path.isEmpty() ? "None" : String.join(" > ", path);
            pathCounts.merge(new PathKey(team, finish, pathText), 1, Integer::sum);
        }
    }

    private static String finishForPathLength(SimulationStage startStage, int pathLength, boolean champion) {
        if (champion) return "Champion";
        int exitStage = startStage.ordinal() + pathLength;
        if (exitStage == SimulationStage.LAST_32.ordinal()) return "Exit Last 32";
        if (exitStage == SimulationStage.LAST_16.ordinal()) return "Exit Last 16";
        if (exitStage == SimulationStage.LAST_8.ordinal()) return "Exit QF";
        if (exitStage == SimulationStage.LAST_4.ordinal()) return "Exit SF";
        return "Runner-up";
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

    private StartData loadStartData(SimulationStage startStage, List<String> startRows,
                                    List<CsvLoader.BracketEntry> brackets) {
        Map<String, MatchDefinition> definitions = new LinkedHashMap<>();
        Map<String, Integer> fatigueByTeam = new HashMap<>();
        Map<String, Integer> header = Map.of();
        for (String line : startRows) {
            if (line == null || line.isBlank() || line.startsWith("match_id")) {
                if (line != null && line.startsWith("match_id")) {
                    String[] columns = line.split(",", -1);
                    Map<String, Integer> indexes = new HashMap<>();
                    for (int i = 0; i < columns.length; i++) indexes.put(columns[i].trim(), i);
                    header = indexes;
                }
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
                definitions.putIfAbsent(matchId, new MatchDefinition(matchId, startStage.bracketStage, team1, team2));
                fatigueByTeam.put(team1, intValue(cols, header.get("team1_path_fatigue")));
                fatigueByTeam.put(team2, intValue(cols, header.get("team2_path_fatigue")));
            }
        }
        for (CsvLoader.BracketEntry bracket : brackets) {
            if (bracket.matchId == null || bracket.matchId.isBlank()) {
                continue;
            }
            SimulationStage bracketStage = SimulationStage.forBracketStage(bracket.stage);
            if (bracketStage == null || bracketStage.ordinal() <= startStage.ordinal()) {
                continue;
            }
            definitions.put(bracket.matchId, new MatchDefinition(bracket.matchId, bracket.stage, bracket.token1, bracket.token2));
        }
        return new StartData(definitions, fatigueByTeam);
    }

    private static int intValue(String[] cols, Integer index) {
        if (index == null || index < 0 || index >= cols.length) return 0;
        try {
            return Integer.parseInt(cols[index].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void writeOutput(String tournament, SimulationStage startStage, SimulationResult result) throws IOException {
        Path output = outputPath(tournament, startStage);
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

    private void writePathOutput(String tournament, SimulationStage startStage, SimulationResult result) throws IOException {
        Path output = pathOutputPath(tournament, startStage);
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

    private void writeScorelineOutput(String tournament, SimulationStage startStage, SimulationResult result) throws IOException {
        Path output = scorelineOutputPath(tournament, startStage);
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

    private Path outputPath(String tournament, SimulationStage startStage) {
        return projectRoot.resolve("data").resolve("simulations").resolve(tournament)
                .resolve("simulation_" + startStage.round + ".csv");
    }

    private Path pathOutputPath(String tournament, SimulationStage startStage) {
        return projectRoot.resolve("data").resolve("simulations").resolve(tournament)
                .resolve("simulation_paths_" + startStage.round + ".csv");
    }

    private Path scorelineOutputPath(String tournament, SimulationStage startStage) {
        return projectRoot.resolve("data").resolve("simulations").resolve(tournament)
                .resolve("simulation_scorelines_" + startStage.round + ".csv");
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

    record StartData(Map<String, MatchDefinition> matchDefinitions, Map<String, Integer> pathFatigueByTeam) {
    }

    enum SimulationStage {
        LAST_32("last_32", "LAST_32", "last_16", "Last 32"),
        LAST_16("last_16", "LAST_16", "last_8", "Last 16"),
        LAST_8("last_8", "QUARTER", "last_4", "Quarter-final"),
        LAST_4("last_4", "SEMI", "final", "Semi-final"),
        FINAL("final", "FINAL", null, "Final");

        final String round;
        final String bracketStage;
        final String reachedRound;
        final String label;

        SimulationStage(String round, String bracketStage, String reachedRound, String label) {
            this.round = round;
            this.bracketStage = bracketStage;
            this.reachedRound = reachedRound;
            this.label = label;
        }

        static SimulationStage forRound(String round) {
            for (SimulationStage stage : values()) {
                if (stage.round.equalsIgnoreCase(round)) return stage;
            }
            throw new IllegalArgumentException("Unsupported simulation start round: " + round);
        }

        static SimulationStage forBracketStage(String bracketStage) {
            for (SimulationStage stage : values()) {
                if (stage.bracketStage.equalsIgnoreCase(bracketStage)) return stage;
            }
            return null;
        }
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

    static class TeamCounts {
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
    record GroupSimulationResult(Map<String, GroupTeamCounts> counts) {
    }

    static final class GroupTeamCounts extends TeamCounts {
        int reachLast32;

        GroupTeamCounts(String team) {
            super(team);
        }
    }

    static final class GroupStanding {
        final String group;
        final String team;
        int points;
        int goalsFor;
        int goalsAgainst;

        GroupStanding(String group, String team) {
            this.group = group;
            this.team = team;
        }

        int goalDifference() {
            return goalsFor - goalsAgainst;
        }
    }

}