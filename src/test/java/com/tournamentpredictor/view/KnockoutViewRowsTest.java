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
    void buildFixtureRowsUsesResultWinnerWhenResultExists() {
        List<String> rows = KnockoutViewRows.buildFixtureRows(List.of(
                "match_id,team1,team2,path,prediction,home_score,away_score",
                "M2,Brazil,Germany,predicted,Brazil (52%),,"
        ), List.of(new ResultEntryRow("last_8", 0, "M2", "Brazil", "Germany", "Germany", "1", "2", false)),
                Map.of(KnockoutViewRows.matchKey("Brazil", "Germany"), "Germany"));

        assertEquals(2, rows.size());
        assertEquals("M2,Brazil,Germany,fixture,Germany,1,2", rows.get(1));
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
