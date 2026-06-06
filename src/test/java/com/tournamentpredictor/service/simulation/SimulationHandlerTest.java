package com.tournamentpredictor.service.simulation;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.ExpectedGoalsCalculator;
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
                "match_id,team1,team2,path,elo,do_you_disagree",
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
                "match_id,team1,team2,path,elo,do_you_disagree",
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

    private static CsvLoader.BracketEntry bracket(String matchId, String team1, String team2, String stage) {
        return new CsvLoader.BracketEntry(matchId, team1, team2, stage);
    }
}
