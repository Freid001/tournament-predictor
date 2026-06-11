package com.tournamentpredictor.services.prediction.builder;

import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.bracket.DisplayBuilder;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PathCalculator;
import com.tournamentpredictor.services.calculation.PathFatigueCalculator;
import com.tournamentpredictor.services.calculation.SlotStatusEvaluator;
import com.tournamentpredictor.services.bracket.TokenResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Last16LineBuilderTest {

    private Last16LineBuilder builder;

    @BeforeEach
    void setUp() {
        EloCalculator eloCalculator = new EloCalculator();
        PathCalculator pathCalculator = new PathCalculator(new SlotStatusEvaluator(eloCalculator), eloCalculator);
        builder = new Last16LineBuilder(new DisplayBuilder(new TokenResolver()), pathCalculator,
                eloCalculator, new PathFatigueCalculator());
    }

    @Test
    void carriesGroupLoadFromLast32RowsIntoLast16Fatigue() {
        Map<String, String> groups = Map.of(
                "A1", "Alpha", "A2", "GroupA", "B1", "Gamma", "B2", "Beta",
                "C1", "Delta", "C2", "GroupC", "D1", "Zeta", "D2", "Epsilon");
        Map<String, String> groupWinner = Map.of("A1", "yes", "B1", "yes", "C1", "yes", "D1", "yes");
        Map<String, String> runnerUp = Map.of("A2", "yes", "B2", "yes", "C2", "yes", "D2", "yes");
        Map<String, String> thirdPlace = Map.of();
        Map<String, Integer> eloRatings = Map.of(
                "Alpha", 2000, "Beta", 1900, "Gamma", 1980, "Delta", 1970,
                "Epsilon", 1930, "Zeta", 1890, "GroupA", 1800, "GroupC", 1800);
        List<CsvLoader.BracketEntry> brackets = List.of(
                new CsvLoader.BracketEntry("M1", "A1", "B2", "LAST_32"),
                new CsvLoader.BracketEntry("M2", "C1", "D2", "LAST_32"),
                new CsvLoader.BracketEntry("M101", "W1", "W2", "LAST_16"));
        List<String> last32Rows = List.of(
                "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                "M1,A1(Alpha),B2(Beta),predicted,Alpha (55%),2000,0,1900,0,25,13,G|Gamma:-3,G|Alpha:-2",
                "M2,C1(Delta),D2(Epsilon),predicted,Delta (54%),1970,0,1930,0,20,10,G|Zeta:-2,G|Delta:-1");

        List<String> lines = builder.buildLast16Lines(groups, groupWinner, runnerUp, thirdPlace,
                eloRatings, brackets, last32Rows, Map.of());

        String row = lines.stream().filter(line -> line.startsWith("M101,")).findFirst().orElseThrow();
        String[] cols = row.split(",", -1);

        assertEquals("50", cols[5]);
        assertEquals("60", cols[6]);
        assertTrue(cols[7].contains("G|Gamma:-3"));
        assertTrue(cols[7].contains("Beta:-3"));
        assertTrue(cols[8].contains("G|Zeta:-2"));
        assertTrue(cols[8].contains("Epsilon:-5"));
    }
    @Test
    void weakLast32OpponentDoesNotReduceExistingGroupLoad() {
        Map<String, String> groups = Map.of(
                "A1", "France", "A2", "GroupA", "B1", "Germany", "B2", "GroupB");
        Map<String, String> groupWinner = Map.of("A1", "yes", "B1", "yes");
        Map<String, String> runnerUp = Map.of("A2", "yes", "B2", "yes");
        Map<String, String> thirdPlace = Map.of();
        Map<String, Integer> eloRatings = Map.of(
                "France", 2165, "United States", 1786, "Germany", 2017, "Scotland", 1732,
                "GroupA", 1800, "GroupB", 1800);
        List<CsvLoader.BracketEntry> brackets = List.of(
                new CsvLoader.BracketEntry("M77", "A1", "B2", "LAST_32"),
                new CsvLoader.BracketEntry("M74", "B1", "A2", "LAST_32"),
                new CsvLoader.BracketEntry("M89", "W77", "W74", "LAST_16"));
        List<String> last32Rows = List.of(
                "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                "M77,A1(France),B2(United States),predicted,France (90%),2081,73,1733,0,76,23,G|Norway:-5 > G|Senegal:-4,G|Turkey:-3",
                "M74,B1(Germany),A2(Scotland),predicted,Germany (84%),1925,69,1770,40,3,47,G|Ecuador:-3,G|Brazil:-3 > G|Morocco:-2");

        List<String> lines = builder.buildLast16Lines(groups, groupWinner, runnerUp, thirdPlace,
                eloRatings, brackets, last32Rows, Map.of());

        String row = lines.stream().filter(line -> line.startsWith("M89,")).findFirst().orElseThrow();
        String[] cols = row.split(",", -1);

        assertEquals("76", cols[5]);
        assertTrue(cols[7].contains("G|Norway:-5"));
        assertTrue(cols[7].contains("G|Senegal:-4"));
        assertTrue(cols[7].contains("United States:0"));
    }


    @Test
    void carriesAlternativeGroupPositionWinnerFromLast32IntoLast16() {
        Map<String, String> groups = Map.of(
                "H1", "Spain", "H2", "Uruguay", "J1", "Argentina", "J2", "Austria",
                "K1", "Portugal", "K2", "Colombia", "L1", "England", "L2", "Croatia");
        Map<String, String> groupWinner = Map.of(
                "H1", "yes", "J1", "yes", "K1", "yes", "K2", "maybe", "L1", "yes", "L2", "maybe");
        Map<String, String> runnerUp = Map.of(
                "H2", "yes", "J2", "yes", "K2", "yes", "K1", "maybe", "L2", "yes", "L1", "maybe");
        Map<String, String> thirdPlace = Map.of();
        Map<String, Integer> eloRatings = Map.of(
                "Spain", 2157, "Austria", 1830, "Colombia", 1982, "England", 2024,
                "Portugal", 1989, "Croatia", 1912, "Uruguay", 1892, "Argentina", 2115);
        List<CsvLoader.BracketEntry> brackets = List.of(
                new CsvLoader.BracketEntry("M83", "K2", "L2", "LAST_32"),
                new CsvLoader.BracketEntry("M84", "H1", "J2", "LAST_32"),
                new CsvLoader.BracketEntry("M93", "W84", "W83", "LAST_16"));
        List<String> last32Rows = List.of(
                "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                "M83,K2(Colombia),L2(Croatia),predicted,Colombia (67%),1982,0,1912,0,42,36,G|Portugal:-5,G|England:-4",
                "M83,K2(Colombia),L2(England),alt,England (52%),1982,0,2024,0,42,3,G|Portugal:-5,G|Croatia:0",
                "M84,H1(Spain),J2(Austria),predicted,Spain (83%),2157,0,1830,0,0,73,,G|Argentina:-9");

        List<String> lines = builder.buildLast16Lines(groups, groupWinner, runnerUp, thirdPlace,
                eloRatings, brackets, last32Rows, Map.of());

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("M93,W84(H1(Spain)),W83(L2(England)),alt,")),
                "Expected England's alternative L2 route from Last 32 to feed the Last 16 alternatives");
    }

}
