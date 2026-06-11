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
                "M53,E1(Spain),F2(Croatia),predicted,Spain (69%),34,39,G|Germany:-4,G|Belgium:-5"
        ));

        assertEquals(2, rows.size());
        assertEquals("M53,E1(Spain),F2(Croatia),predicted,Spain (64%),34,39,G|Germany:-4,G|Belgium:-5,12.6,3152", rows.get(1));
    }


    @Test
    void enrichPathContextFallsBackToRawPairWhenPathLabelsDiffer() {
        List<String> rows = KnockoutViewRows.enrichPathContext(List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,matchup_pct",
                "M53,Spain,Croatia,alt,Spain (64%),0,0,Group stage,Group stage,12.6"
        ), List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                "M53,E1(Spain),F2(Croatia),predicted,Spain (69%),34,39,G|Germany:-4,G|Belgium:-5"
        ));

        assertEquals("M53,E1(Spain),F2(Croatia),alt,Spain (64%),34,39,G|Germany:-4,G|Belgium:-5,12.6", rows.get(1));
    }

    @Test
    void enrichPathContextDoesNotOverwriteUnmatchedRowsWithDifferentMatchId() {
        List<String> rows = KnockoutViewRows.enrichPathContext(List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,matchup_pct",
                "M99,Spain,Croatia,predicted,Spain (64%),0,0,Group stage,Group stage,12.6"
        ), List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                "M53,E1(Spain),F2(Croatia),predicted,Spain (69%),34,39,G|Germany:-4,G|Belgium:-5"
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
                        "M53,E1(Spain),F2(Croatia),predicted,Spain (69%),34,39,G|Germany:-4,G|Belgium:-5"
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
                        "M53,E1(Spain),F2(Croatia),predicted,Spain (69%),34,39,G|Germany:-4,G|Belgium:-5"
                ),
                Map.of("M53", new String[]{"E1", "F2"}));

        assertEquals("M53,E1(Japan),F2(Croatia),fixture,Croatia,0,39,Group stage,G|Belgium:-5,1,1", rows.get(1));
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
                        "M49,A1(Netherlands),B2(Iran),predicted,Netherlands (81%),1,28,G|Qatar:0,G|England:-3"
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
