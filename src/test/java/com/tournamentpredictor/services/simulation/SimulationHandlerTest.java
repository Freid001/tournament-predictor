package com.tournamentpredictor.services.simulation;

import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.ExpectedGoalsCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationHandlerTest {
    @TempDir
    Path root;

    @Test
    void simulateLast32_countsReachRoundsAndChampion() {
        SimulationHandler handler = new SimulationHandler(new CsvLoader(root), root,
                new ExpectedGoalsCalculator(), new EloCalculator(), 20, 42L);
        List<String> last32 = List.of(
                "match_id,team1,team2,path,elo",
                "M73,A1(Alpha),B2(Delta),predicted,Alpha (99%),",
                "M74,C1(Beta),D2(Gamma),predicted,Beta (99%),",
                "M75,E1(Epsilon),F2(Zeta),predicted,Epsilon (99%),",
                "M76,G1(Eta),H2(Theta),predicted,Eta (99%),"
        );
        List<CsvLoader.BracketEntry> brackets = List.of(
                bracket("M89", "W73", "W74", "LAST_16"),
                bracket("M90", "W75", "W76", "LAST_16"),
                bracket("M97", "W89", "W90", "QUARTER"),
                bracket("M101", "W97", "W97", "SEMI"),
                bracket("M103", "W101", "W101", "FINAL")
        );
        Map<String, Integer> elo = Map.of(
                "Alpha", 3000,
                "Beta", 2500,
                "Gamma", 1000,
                "Delta", 900,
                "Epsilon", 800,
                "Zeta", 700,
                "Eta", 600,
                "Theta", 500
        );

        SimulationHandler.SimulationResult result = handler.simulateLast32(last32, brackets, elo);

        SimulationHandler.TeamCounts alpha = result.teamCounts().stream()
                .filter(counts -> counts.team.equals("Alpha"))
                .findFirst()
                .orElseThrow();
        assertEquals(20, alpha.reachLast16);
        assertTrue(alpha.reachLast8 > 0);
        assertTrue(alpha.champion > 0);
    }

    @Test
    void handleWritesRunAndSeedMetadataColumns() throws Exception {
        Path predictionDir = root.resolve("data/predictions/test");
        Files.createDirectories(predictionDir);
        Files.writeString(predictionDir.resolve("last_32.csv"), String.join("\n",
                "match_id,team1,team2,path,elo",
                "M73,A1(Alpha),B2(Delta),predicted,Alpha (99%),",
                "M74,C1(Beta),D2(Gamma),predicted,Beta (99%),",
                "M75,E1(Epsilon),F2(Zeta),predicted,Epsilon (99%),",
                "M76,G1(Eta),H2(Theta),predicted,Eta (99%),"
        ));
        Path bracketDir = root.resolve("data/bracket");
        Files.writeString(predictionDir.resolve("groups.csv"), String.join("\n",
                "group,team,elo_ranking",
                "A,Alpha,3000",
                "B,Beta,2500",
                "C,Gamma,1000",
                "D,Delta,900",
                "E,Epsilon,800",
                "F,Zeta,700",
                "G,Eta,600",
                "H,Theta,500"
        ));
        Files.createDirectories(bracketDir);
        Files.writeString(bracketDir.resolve("test.csv"), String.join("\n",
                "type,stage,match_id,team1,team2",
                "match,LAST_16,M89,W73,W74",
                "match,LAST_16,M90,W75,W76",
                "match,QUARTER,M97,W89,W90",
                "match,SEMI,M101,W97,W97",
                "match,FINAL,M103,W101,W101"
        ));
        Path eloDir = root.resolve("data/elo/snapshots/test");
        Files.createDirectories(eloDir);
        Files.writeString(eloDir.resolve("teams.csv"), String.join("\n",
                "rank,team_code,team_name,rating",
                "1,ALP,Alpha,3000",
                "2,BET,Beta,2500",
                "3,GAM,Gamma,1000",
                "4,DEL,Delta,900",
                "5,EPS,Epsilon,800",
                "6,ZET,Zeta,700",
                "7,ETA,Eta,600",
                "8,THE,Theta,500"
        ));

        SimulationHandler handler = new SimulationHandler(new CsvLoader(root), root,
                new ExpectedGoalsCalculator(), new EloCalculator(), 10, 99L);

        handler.handle("test");

        List<String> lines = Files.readAllLines(root.resolve("data/simulations/test").resolve("simulation_last_32.csv"));
        assertEquals("team,reach_last_16,reach_last_8,reach_last_4,reach_final,champion,predicted_finish,predicted_finish_pct,best_realistic_finish,best_realistic_pct,simulation_runs,simulation_seed", lines.get(0));
        assertTrue(lines.stream().skip(1).allMatch(line -> line.matches(".*,(Exit Last 32|Exit Last 16|Exit QF|Exit SF|Runner-up|Champion),[0-9.]+,(Exit Last 32|Exit Last 16|Exit QF|Exit SF|Runner-up|Champion),[0-9.]+,10,99")));

        List<String> pathLines = Files.readAllLines(root.resolve("data/simulations/test").resolve("simulation_paths_last_32.csv"));
        assertEquals("team,finish,path,count,percentage,simulation_runs,simulation_seed", pathLines.get(0));
        assertTrue(pathLines.stream().anyMatch(line -> line.contains("Alpha")));
        assertTrue(pathLines.stream().skip(1).allMatch(line -> line.matches(".*,[0-9]+,[0-9.]+,10,99")));

        List<String> scorelineLines = Files.readAllLines(root.resolve("data/simulations/test").resolve("simulation_scorelines_last_32.csv"));
        assertEquals("stage,match_id,team1,team2,scoreline,winner,count,scoreline_pct,matchup_runs,matchup_pct,simulation_runs,simulation_seed", scorelineLines.get(0));
        assertTrue(scorelineLines.stream().skip(1).anyMatch(line -> line.contains("last_32,M73,Alpha,Delta")));
        assertTrue(scorelineLines.stream().skip(1).allMatch(line -> line.matches("[^,]+,M[0-9]+,[^,]+,[^,]+,[0-9]+-[0-9]+,[^,]+,[0-9]+,[0-9.]+,[0-9]+,[0-9.]+,10,99")));
    }

    @Test
    void simulateFromLast16TreatsEntrantsAsQualifiedAndSkipsLast32() {
        SimulationHandler handler = new SimulationHandler(new CsvLoader(root), root,
                new ExpectedGoalsCalculator(), new EloCalculator(), 20, 42L);
        List<String> last16 = List.of(
                "match_id,team1,team2,path,elo,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                "M89,W77(Alpha),W74(Beta),predicted,Alpha (60%),120,40,Route A,Route B,",
                "M90,W75(Gamma),W73(Delta),predicted,Gamma (55%),80,20,Route C,Route D,"
        );
        List<CsvLoader.BracketEntry> brackets = List.of(
                bracket("M97", "W89", "W90", "QUARTER"),
                bracket("M101", "W97", "W97", "SEMI"),
                bracket("M103", "W101", "W101", "FINAL")
        );
        Map<String, Integer> elo = Map.of("Alpha", 2100, "Beta", 1900, "Gamma", 2000, "Delta", 1800);

        SimulationHandler.SimulationResult result = handler.simulateFromRound(
                SimulationHandler.SimulationStage.LAST_16, last16, brackets, elo, Map.of());

        assertEquals(4, result.teamCounts().size());
        assertTrue(result.teamCounts().stream().allMatch(counts -> counts.reachLast16 == 40));
        assertEquals(40, result.runs());
        assertTrue(result.scorelineCounts().stream().noneMatch(row -> "last_32".equals(row.stage())));
        assertTrue(result.scorelineCounts().stream().anyMatch(row -> "last_16".equals(row.stage())));
    }


    @Test
    void groupSimulationProducesPositionsQualifiersAndSavedRoutes() throws Exception {
        Path project = Path.of("").toAbsolutePath();
        CsvLoader loader = new CsvLoader(project);
        int runs = 20;
        SimulationHandler handler = new SimulationHandler(loader, project,
                new ExpectedGoalsCalculator(), new EloCalculator(), runs, 42L);

        SimulationHandler.GroupSimulationResult result = handler.simulateGroups(
                loader.loadGroups("world_cup_2026"),
                loader.loadBrackets("world_cup_2026"),
                loader.loadTournamentElo("world_cup_2026"),
                loader.loadTeamSnapshots("world_cup_2026"));

        assertEquals(48, result.counts().size());
        assertEquals(32 * runs, result.counts().values().stream().mapToInt(c -> c.reachKnockout).sum());
        assertEquals(12 * runs, result.counts().values().stream().mapToInt(c -> c.finishFirst).sum());
        assertEquals(12 * runs, result.counts().values().stream().mapToInt(c -> c.finishSecond).sum());
        assertEquals(12 * runs, result.counts().values().stream().mapToInt(c -> c.finishThird).sum());
        assertEquals(12 * runs, result.counts().values().stream().mapToInt(c -> c.finishFourth).sum());
        assertEquals(runs, result.routes().size());
        assertTrue(result.routes().stream().allMatch(route -> route.matches().size() == 16));
        assertEquals(72, result.scorelineCounts().stream()
                .map(row -> row.matchId() + "|" + row.team1() + "|" + row.team2()).distinct().count());
        assertEquals(72 * runs, result.scorelineCounts().stream().mapToInt(SimulationHandler.ScorelineCount::count).sum());

        SimulationHandler.SimulationResult knockout = handler.simulateKnockoutsFromGroupRoutes(
                result.routes(), loader.loadBrackets("world_cup_2026"),
                loader.loadTournamentElo("world_cup_2026"), loader.loadTeamSnapshots("world_cup_2026"));
        assertEquals(runs, knockout.teamCounts().stream().mapToInt(c -> c.champion).sum());
        assertTrue(result.counts().containsKey("Scotland"));
    }

    @Test
    void euroGroupSimulationStartsAtLast16WithFourBestThirds() throws Exception {
        String tournament = "euros_test";
        Path project = Path.of("").toAbsolutePath();
        Path predictionDir = root.resolve("data/predictions").resolve(tournament);
        Path snapshotDir = root.resolve("data/elo/snapshots").resolve(tournament);
        Path bracketDir = root.resolve("data/bracket");
        Files.createDirectories(predictionDir);
        Files.createDirectories(snapshotDir);
        Files.createDirectories(bracketDir);
        Files.write(bracketDir.resolve(tournament + ".csv"),
                Files.readAllBytes(project.resolve("data/bracket/euros_2024.csv")));
        Files.write(bracketDir.resolve("third_place_lookup_last_16.csv"),
                Files.readAllBytes(project.resolve("data/bracket/third_place_lookup_last_16.csv")));

        StringBuilder groups = new StringBuilder("group,team,base_elo,qual_bonus,squad_depth,attack_quality,defence_quality,elo_ranking\n");
        StringBuilder ratings = new StringBuilder("rank,team_code,team_name,rating\n");
        int rank = 1;
        for (char group = 'A'; group <= 'F'; group++) {
            for (int position = 1; position <= 4; position++) {
                String team = "Team " + group + position;
                int elo = 2100 - rank * 10;
                groups.append(group).append(',').append(team).append(',').append(elo)
                        .append(",0,0,0,0,").append(elo).append('\n');
                ratings.append(rank).append(',').append(group).append(position).append(',')
                        .append(team).append(',').append(elo).append('\n');
                rank++;
            }
        }
        Files.writeString(predictionDir.resolve("groups.csv"), groups);
        Files.writeString(snapshotDir.resolve("teams.csv"), ratings);

        CsvLoader loader = new CsvLoader(root);
        int runs = 20;
        SimulationHandler handler = new SimulationHandler(loader, root,
                new ExpectedGoalsCalculator(), new EloCalculator(), runs, 42L);

        SimulationHandler.GroupSimulationResult result = handler.simulateGroups(
                loader.loadGroups(tournament), loader.loadBrackets(tournament),
                loader.loadTournamentElo(tournament), loader.loadTeamSnapshots(tournament));

        assertEquals(24, result.counts().size());
        assertEquals(16 * runs, result.counts().values().stream().mapToInt(c -> c.reachKnockout).sum());
        assertEquals(runs, result.routes().size());
        assertTrue(result.routes().stream().allMatch(route -> route.matches().size() == 8));
        assertTrue(result.routes().stream().flatMap(route -> route.matches().values().stream())
                .allMatch(match -> match.team1() != null && match.team2() != null));

        SimulationHandler.SimulationResult knockout = handler.simulateKnockoutsFromGroupRoutes(
                result.routes(), loader.loadBrackets(tournament),
                loader.loadTournamentElo(tournament), loader.loadTeamSnapshots(tournament));
        assertEquals(runs, knockout.teamCounts().stream().mapToInt(c -> c.champion).sum());
        assertEquals(16 * runs, knockout.teamCounts().stream().mapToInt(c -> c.reachLast16).sum());
    }


    @Test
    void uefaGroupRankingUsesHeadToHeadBeforeOverallGoalDifference() {
        SimulationHandler.GroupStanding alpha = standing("A", "Alpha", 6, 10, 1, 2);
        SimulationHandler.GroupStanding beta = standing("A", "Beta", 6, 4, 3, 2);

        List<SimulationHandler.GroupStanding> ranked = SimulationHandler.rankUefaGroup(
                List.of(alpha, beta),
                List.of(new SimulationHandler.GroupMatch("Alpha", "Beta", 0, 1)),
                Map.of("Alpha", 2200, "Beta", 1800));

        assertEquals(List.of("Beta", "Alpha"), ranked.stream().map(row -> row.team).toList());
    }

    @Test
    void uefaGroupRankingFallsBackToOverallWinsBeforeElo() {
        SimulationHandler.GroupStanding alpha = standing("A", "Alpha", 4, 4, 3, 1);
        SimulationHandler.GroupStanding beta = standing("A", "Beta", 4, 4, 3, 2);

        List<SimulationHandler.GroupStanding> ranked = SimulationHandler.rankUefaGroup(
                List.of(alpha, beta),
                List.of(new SimulationHandler.GroupMatch("Alpha", "Beta", 1, 1)),
                Map.of("Alpha", 2200, "Beta", 1800));

        assertEquals(List.of("Beta", "Alpha"), ranked.stream().map(row -> row.team).toList());
    }

    private static SimulationHandler.GroupStanding standing(String group, String team, int points,
                                                              int goalsFor, int goalsAgainst, int wins) {
        SimulationHandler.GroupStanding standing = new SimulationHandler.GroupStanding(group, team);
        standing.points = points;
        standing.goalsFor = goalsFor;
        standing.goalsAgainst = goalsAgainst;
        standing.wins = wins;
        return standing;
    }

    private static CsvLoader.BracketEntry bracket(String matchId, String team1, String team2, String stage) {
        return new CsvLoader.BracketEntry(matchId, team1, team2, stage);
    }
}
