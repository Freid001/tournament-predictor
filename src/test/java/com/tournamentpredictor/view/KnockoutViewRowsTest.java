package com.tournamentpredictor.view;

import com.tournamentpredictor.model.results.ResultEntryRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnockoutViewRowsTest {

    @Test
    void buildFixtureRowsShowsKnownFixtureBeforeResultExists() {
        List<String> rows = KnockoutViewRows.buildFixtureRows(List.of(
                "match_id,team1,team2,path,prediction",
                "M1,France,Spain,predicted,France (55%)",
                "M1,France,Germany,alt,France (52%)"
        ), List.of(new ResultEntryRow("last_16", 0, "M1", "France", "Germany", "", "", "", false)), Map.of());

        assertEquals(2, rows.size());
        assertEquals("M1,France,Germany,fixture,France (52%)", rows.get(1));
    }

    @Test
    void buildFixtureRowsUsesResultWinnerWhenPredictionWasCorrect() {
        List<String> rows = KnockoutViewRows.buildFixtureRows(List.of(
                "match_id,team1,team2,path,prediction,home_score,away_score",
                "M2,Brazil,Germany,predicted,Brazil (52%),,"
        ), List.of(new ResultEntryRow("last_8", 0, "M2", "Brazil", "Germany", "Brazil", "2", "1", false)),
                Map.of(KnockoutViewRows.matchKey("Brazil", "Germany"), "Brazil"));

        assertEquals(2, rows.size());
        assertEquals("M2,Brazil,Germany,fixture,Brazil,2,1", rows.get(1));
    }

    @Test
    void buildFixtureRowsMarksActualUpsetResult() {
        List<String> rows = KnockoutViewRows.buildFixtureRows(List.of(
                "match_id,team1,team2,path,prediction,home_score,away_score",
                "M2,Brazil,Germany,predicted,Brazil (52%),,"
        ), List.of(new ResultEntryRow("last_8", 0, "M2", "Brazil", "Germany", "Germany", "1", "2", false)),
                Map.of(KnockoutViewRows.matchKey("Brazil", "Germany"), "Germany"));

        assertEquals(2, rows.size());
        assertEquals("M2,Brazil,Germany,result_upset,Germany,1,2", rows.get(1));
    }


    @Test
    void buildFixtureRowsShowsActualFixtureEvenWhenPredictedPairingWasWrong() {
        List<String> rows = KnockoutViewRows.buildFixtureRows(List.of(
                "match_id,team1,team2,path,prediction,home_score,away_score",
                "M55,Belgium,Germany,predicted,Belgium (54%),,"
        ), List.of(new ResultEntryRow("last_16", 0, "M55", "Morocco", "Spain", "Morocco", "0", "0", true)),
                Map.of(KnockoutViewRows.matchKey("Morocco", "Spain"), "Morocco"));

        assertEquals(2, rows.size());
        assertEquals("M55,Morocco,Spain,fixture,Morocco,0,0", rows.get(1));
    }



    @Test
    void enrichPathContextCopiesGroupFinishSlotsFromRawMatchupFile() {
        List<String> rows = KnockoutViewRows.enrichPathContext(List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,matchup_pct,matchup_runs",
                "M53,Spain,Croatia,predicted,Spain (64%),0,0,Group stage,Group stage,12.6,3152"
        ), List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                "M53,Spain,Croatia,predicted,Spain (69%),34,39,G|Germany:-4,G|Belgium:-5"
        ));

        assertEquals(2, rows.size());
        assertEquals("M53,Spain,Croatia,predicted,Spain (64%),34,39,G|Germany:-4,G|Belgium:-5,12.6,3152", rows.get(1));
    }


    @Test
    void enrichPathContextFallsBackToRawPairWhenPathLabelsDiffer() {
        List<String> rows = KnockoutViewRows.enrichPathContext(List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,matchup_pct",
                "M53,Spain,Croatia,alt,Spain (64%),0,0,Group stage,Group stage,12.6"
        ), List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                "M53,Spain,Croatia,predicted,Spain (69%),34,39,G|Germany:-4,G|Belgium:-5"
        ));

        assertEquals("M53,Spain,Croatia,alt,Spain (64%),34,39,G|Germany:-4,G|Belgium:-5,12.6", rows.get(1));
    }


    @Test
    void enrichPathContextFallsBackToGroupSlotsForSimulatedAlternativeRows() {
        List<String> rows = KnockoutViewRows.enrichPathContext(List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,matchup_pct",
                "M51,United States,Ecuador,upset,Ecuador (56%),0,0,Group stage,Group stage,5.2"
        ), List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                "M51,England,Qatar,predicted,England (66%),0,52,,G|Netherlands:-6"
        ), List.of(
                "group,team,predicted_position",
                "A,Ecuador,3 (46%)",
                "B,United States,3 (41%)"
        ));

        String[] cols = rows.get(1).split(",", -1);
        assertEquals("M51", cols[0]);
        assertTrue(cols[1].startsWith("B3") && cols[1].contains("United States"));
        assertTrue(cols[2].startsWith("A3") && cols[2].contains("Ecuador"));
        assertEquals("upset", cols[3]);
        assertEquals("Ecuador (56%)", cols[4]);
    }

    @Test
    void enrichPathContextUsesBracketSlotBeforePredictedGroupSlotForDirectLast16Rows() {
        List<String> rows = KnockoutViewRows.enrichPathContext(List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,matchup_pct",
                "M49,Netherlands,England,upset,Netherlands (60%),0,0,Group stage,Group stage,13.5"
        ), List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                "M51,England,Qatar,predicted,England (66%),0,52,,G|Netherlands:-6"
        ), List.of(
                "group,team,predicted_position",
                "A,Netherlands,1 (81%)",
                "B,England,1 (73%)"
        ), Map.of("M49", new String[]{"A1", "B2"}));

        String[] cols = rows.get(1).split(",", -1);
        assertEquals("M49", cols[0]);
        assertTrue(cols[1].startsWith("A1") && cols[1].contains("Netherlands"));
        assertTrue(cols[2].startsWith("B2") && cols[2].contains("England"));
        assertEquals("upset", cols[3]);
        assertEquals("Netherlands (60%)", cols[4]);
    }


    @Test
    void enrichPathContextUsesActualGroupThirdPlaceForCompositeBracketSlot() {
        List<String> rows = KnockoutViewRows.enrichPathContext(List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,matchup_pct",
                "M74,Germany,Scotland,alt,Germany (69%),0,0,Group stage,Group stage,4.8"
        ), List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                "M74,Germany,Japan,alt,Germany (70%),0,0,G|Ecuador:-3,Group stage"
        ), List.of(
                "group,team,predicted_position",
                "E,Germany,1 (55%)",
                "C,Scotland,2 (35%)"
        ), Map.of("M74", new String[]{"E1", "ABCDF3"}));

        String[] cols = rows.get(1).split(",", -1);
        assertEquals("M74", cols[0]);
        assertTrue(cols[1].startsWith("E1") && cols[1].contains("Germany"));
        assertTrue(cols[2].startsWith("C3") && cols[2].contains("Scotland"));
        assertEquals("alt", cols[3]);
        assertEquals("Germany (69%)", cols[4]);
    }


    @Test
    void enrichPathContextNormalizesCompositeThirdPlaceSlotFromExactContextRows() {
        List<String> rows = KnockoutViewRows.enrichPathContext(List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,matchup_pct",
                "M74,Germany,Scotland,alt,Germany (69%),0,0,Group stage,Group stage,4.8"
        ), List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                "M74,Germany,Scotland,alt,Germany (70%),0,0,G|Ecuador:-3,Group stage"
        ), List.of(
                "group,team,predicted_position",
                "E,Germany,1 (55%)",
                "C,Scotland,2 (35%)"
        ), Map.of("M74", new String[]{"E1", "ABCDF3"}));

        assertEquals("M74,Germany,Scotland,alt,Germany (69%),0,0,G|Ecuador:-3,Group stage,4.8", rows.get(1));
    }

    @Test
    void enrichPathContextDoesNotOverwriteUnmatchedRowsWithDifferentMatchId() {
        List<String> rows = KnockoutViewRows.enrichPathContext(List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,matchup_pct",
                "M99,Spain,Croatia,predicted,Spain (64%),0,0,Group stage,Group stage,12.6"
        ), List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                "M53,Spain,Croatia,predicted,Spain (69%),34,39,G|Germany:-4,G|Belgium:-5"
        ));

        assertEquals("M99,Spain,Croatia,predicted,Spain (64%),0,0,Group stage,Group stage,12.6", rows.get(1));
    }

    @Test
    void buildFixtureRowsKeepsKnownOpponentContextForBothTeamsWhenAvailable() {
        List<String> rows = KnockoutViewRows.buildFixtureRows(List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                "M53,Spain,Croatia,predicted,Spain (64%),0,0,Group stage,Group stage"
        ), List.of(new ResultEntryRow("last_16", 0, "M53", "Spain", "Croatia", "Croatia", "1", "1", true)),
                Map.of(KnockoutViewRows.matchKey("Spain", "Croatia"), "Croatia"),
                List.of(
                        "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                        "M53,Spain,Croatia,predicted,Spain (69%),34,39,G|Germany:-4,G|Belgium:-5"
                ));

        assertEquals("M53,Spain,Croatia,result_upset,Croatia,34,39,G|Germany:-4,G|Belgium:-5", rows.get(1));
    }


    @Test
    void buildFixtureRowsWrapsActualTeamsWithBracketSlotsForTournamentPath() {
        List<String> rows = KnockoutViewRows.buildFixtureRows(List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,home_score,away_score",
                "M53,Spain,Croatia,predicted,Spain (64%),0,0,Group stage,Group stage,,"
        ), List.of(new ResultEntryRow("last_16", 0, "M53", "Japan", "Croatia", "Croatia", "1", "1", true)),
                Map.of(KnockoutViewRows.matchKey("Japan", "Croatia"), "Croatia"),
                List.of(
                        "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                        "M53,Spain,Croatia,predicted,Spain (69%),34,39,G|Germany:-4,G|Belgium:-5"
                ),
                Map.of("M53", new String[]{"E1", "F2"}));

        String[] cols = rows.get(1).split(",", -1);
        assertEquals("M53", cols[0]);
        assertTrue(cols[1].startsWith("E1") && cols[1].contains("Japan"));
        assertTrue(cols[2].startsWith("F2") && cols[2].contains("Croatia"));
        assertEquals("fixture", cols[3]);
        assertEquals("Croatia", cols[4]);
        assertEquals("G|Belgium:-5", cols[8]);
    }

    @Test
    void buildFixtureRowsUsesRouteContextFromRicherMatchupFile() {
        List<String> rows = KnockoutViewRows.buildFixtureRows(List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                "M49,Netherlands,United States,alt,Netherlands (74%),0,0,Group stage,Group stage"
        ), List.of(new ResultEntryRow("last_16", 0, "M49", "Netherlands", "United States", "Netherlands", "3", "1", false)),
                Map.of(KnockoutViewRows.matchKey("Netherlands", "United States"), "Netherlands"),
                List.of(
                        "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                        "M49,Netherlands,Iran,predicted,Netherlands (81%),1,28,G|Qatar:0,G|England:-3"
                ));

        assertEquals(2, rows.size());
        assertEquals("M49,Netherlands,United States,fixture,Netherlands,1,0,G|Qatar:0,Group stage", rows.get(1));
    }

    @Test
    void relabelPredictedRowsAsLiveLeavesOtherPathsAlone() {
        List<String> rows = KnockoutViewRows.relabelPredictedRowsAsLive(List.of(
                "match_id,team1,team2,path,prediction",
                "M1,France,Spain,predicted,France (55%)",
                "M1,France,Germany,upset,Germany (51%)",
                "M2,Brazil,Argentina,alt,Brazil (52%)"
        ));

        assertEquals("M1,France,Spain,live,France (55%)", rows.get(1));
        assertEquals("M1,France,Germany,upset,Germany (51%)", rows.get(2));
        assertEquals("M2,Brazil,Argentina,alt,Brazil (52%)", rows.get(3));
    }


    @Test
    void buildResultRowsUsesMatchIdWhenSameTeamsAppearInDifferentSlots() {
        List<String> rows = KnockoutViewRows.buildResultRows(List.of(
                "match_id,team1,team2,path,prediction,home_score,away_score",
                "M49,Senegal,England,upset,England (75%),,",
                "M51,England,Senegal,upset,England (76%),,"
        ), List.of(Map.of(
                "match_id", "M51",
                "team1", "England",
                "team2", "Senegal",
                "winner", "England",
                "home_score", "3",
                "away_score", "0"
        )), Map.of(KnockoutViewRows.matchKey("England", "Senegal"), "England"));

        assertEquals(2, rows.size());
        assertEquals("M51,England,Senegal,results,England,3,0", rows.get(1));
    }

    @Test
    void mergePlacesFixtureAfterMatchingPredictedOrAlternativeRow() {
        List<String> merged = KnockoutViewRows.merge(List.of(
                "match_id,team1,team2,path,prediction",
                "M1,France,Germany,alt,France (52%)"
        ), List.of(
                "match_id,team1,team2,path,prediction",
                "M1,France,Germany,fixture,France (52%)"
        ));

        assertTrue(merged.get(1).contains("alt"));
        assertTrue(merged.get(2).contains("fixture"));
    }
}
