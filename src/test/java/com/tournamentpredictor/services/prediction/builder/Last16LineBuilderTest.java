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
                "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,team1_slot,team1_team,team1_source_match,team1_group_finish,team1_bracket_slot,team2_slot,team2_team,team2_source_match,team2_group_finish,team2_bracket_slot",
                "M1,Alpha,Beta,predicted,Alpha (55%),2000,0,1900,0,25,13,G|Gamma:-3,G|Alpha:-2,A1,Alpha,,A1,A1,B2,Beta,,B2,B2",
                "M2,Delta,Epsilon,predicted,Delta (54%),1970,0,1930,0,20,10,G|Zeta:-2,G|Delta:-1,C1,Delta,,C1,C1,D2,Epsilon,,D2,D2");

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
                "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,team1_slot,team1_team,team1_source_match,team1_group_finish,team1_bracket_slot,team2_slot,team2_team,team2_source_match,team2_group_finish,team2_bracket_slot",
                "M77,France,United States,predicted,France (90%),2081,73,1733,0,76,23,G|Norway:-5 > G|Senegal:-4,G|Turkey:-3,A1,France,,A1,A1,B2,United States,,B2,B2",
                "M74,Germany,Scotland,predicted,Germany (84%),1925,69,1770,40,3,47,G|Ecuador:-3,G|Brazil:-3 > G|Morocco:-2,B1,Germany,,B1,B1,A2,Scotland,,A2,A2");

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
                "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,team1_slot,team1_team,team1_source_match,team1_group_finish,team1_bracket_slot,team2_slot,team2_team,team2_source_match,team2_group_finish,team2_bracket_slot",
                "M83,Colombia,Croatia,predicted,Colombia (67%),1982,0,1912,0,42,36,G|Portugal:-5,G|England:-4,K2,Colombia,,K2,K2,L2,Croatia,,L2,L2",
                "M83,Colombia,England,alt,England (52%),1982,0,2024,0,42,3,G|Portugal:-5,G|Croatia:0,K2,Colombia,,K2,K2,L2,England,,L1,L2",
                "M84,Spain,Austria,predicted,Spain (83%),2157,0,1830,0,0,73,,G|Argentina:-9,H1,Spain,,H1,H1,J2,Austria,,J2,J2");

        List<String> lines = builder.buildLast16Lines(groups, groupWinner, runnerUp, thirdPlace,
                eloRatings, brackets, last32Rows, Map.of());

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("M93,Spain,England,")
                        && line.contains(",W84,Spain,M84,H1,H1,W83,England,M83,L1,L2")),
                "Expected England's alternative L2 route from Last 32 to feed the Last 16 alternatives via metadata columns");
    }

    @Test
    void forwardsPredictedLoserFromLast32AsUpsetRouteWithCorrectOpponent() {
        Map<String, String> groups = Map.of(
                "F1", "Japan", "F2", "Netherlands", "C2", "Morocco", "C1", "Brazil",
                "B2", "Canada", "B1", "Switzerland", "A2", "South Korea", "A1", "Mexico");
        Map<String, String> groupWinner = Map.of("F1", "yes", "B1", "yes", "A1", "yes", "C1", "yes");
        Map<String, String> runnerUp = Map.of("F2", "yes", "B2", "yes", "A2", "yes", "C2", "yes");
        Map<String, String> thirdPlace = Map.of();
        Map<String, Integer> eloRatings = Map.of(
                "Japan", 1900, "Morocco", 1880, "Canada", 1860, "South Korea", 1810,
                "Netherlands", 1890, "Brazil", 2000, "Switzerland", 1870, "Mexico", 1850);
        List<CsvLoader.BracketEntry> brackets = List.of(
                new CsvLoader.BracketEntry("M75", "F1", "C2", "LAST_32"),
                new CsvLoader.BracketEntry("M73", "A2", "B2", "LAST_32"),
                new CsvLoader.BracketEntry("M90", "W75", "W73", "LAST_16"));
        List<String> last32Rows = List.of(
                "match_id,team1,team2,path,elo,prediction,team1_slot,team1_team,team1_source_match,team1_group_finish,team1_bracket_slot,team2_slot,team2_team,team2_source_match,team2_group_finish,team2_bracket_slot",
                "M75,Japan,Morocco,predicted,Japan (60%),Japan (60%),F1,Japan,,F1,F1,C2,Morocco,,C2,C2",
                "M73,South Korea,Canada,predicted,Canada (55%),Canada (55%),A2,South Korea,,A2,A2,B2,Canada,,B2,B2");

        List<String> lines = builder.buildLast16Lines(groups, groupWinner, runnerUp, thirdPlace,
                eloRatings, brackets, last32Rows, Map.of());

        String moroccoCanada = lines.stream()
                .filter(line -> line.startsWith("M90,Morocco,Canada,"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected Morocco upset route into M90 vs Canada: " + lines));

        String[] cols = moroccoCanada.split(",", -1);
        assertEquals("alt", cols[3]);
        assertEquals("U@M75|Japan", cols[7].substring(0, "U@M75|Japan".length()),
                "Morocco should advance from M75 as the upset winner over Japan: " + moroccoCanada);
        assertTrue(moroccoCanada.contains(",W75,Morocco,M75,C2,C2,W73,Canada,M73,B2,B2"),
                "Metadata should keep Morocco on the W75 branch into M90: " + moroccoCanada);
    }

    @Test
    void skipsLast16RowsWhenLast32PathWouldReplayOpponent() {
        Map<String, String> groups = Map.of(
                "A1", "Alpha", "A2", "Beta", "B1", "Gamma", "B2", "Delta",
                "C1", "Epsilon", "C2", "Zeta", "D1", "Eta", "D2", "Theta");
        Map<String, String> groupWinner = Map.of("A1", "yes", "B1", "yes", "C1", "yes", "D1", "yes");
        Map<String, String> runnerUp = Map.of("A2", "yes", "B2", "yes", "C2", "yes", "D2", "yes");
        Map<String, String> thirdPlace = Map.of();
        Map<String, Integer> eloRatings = Map.of(
                "Alpha", 2000, "Beta", 1900, "Gamma", 1980, "Delta", 1970,
                "Epsilon", 1930, "Zeta", 1890, "Eta", 1880, "Theta", 1870);
        List<CsvLoader.BracketEntry> brackets = List.of(
                new CsvLoader.BracketEntry("M1", "A1", "B2", "LAST_32"),
                new CsvLoader.BracketEntry("M2", "C1", "D2", "LAST_32"),
                new CsvLoader.BracketEntry("M101", "W1", "W2", "LAST_16"));
        List<String> last32Rows = List.of(
                "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,team1_slot,team1_team,team1_source_match,team1_group_finish,team1_bracket_slot,team2_slot,team2_team,team2_source_match,team2_group_finish,team2_bracket_slot",
                "M1,Alpha,Beta,predicted,Alpha (55%),2000,0,1900,0,25,13,G|Gamma:-3,G|Alpha:-2,A1,Alpha,,A1,A1,B2,Beta,,B2,B2",
                "M2,Epsilon,Zeta,predicted,Epsilon (54%),1970,0,1930,0,20,10,G|Alpha:-2,G|Epsilon:-1,C1,Epsilon,,C1,C1,D2,Zeta,,D2,D2");

        List<String> lines = builder.buildLast16Lines(groups, groupWinner, runnerUp, thirdPlace,
                eloRatings, brackets, last32Rows, Map.of());

        assertTrue(lines.stream().noneMatch(line -> line.startsWith("M101,Alpha,Epsilon,")),
                "Alpha/Epsilon should not be generated because Alpha already has Epsilon as the next opponent path via M2");
    }

}
