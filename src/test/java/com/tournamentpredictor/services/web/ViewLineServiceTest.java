package com.tournamentpredictor.services.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewLineServiceTest {

    @Test
    void allStageTeamNamesExtractsSortedUniqueTeamNames() {
        List<String> teams = ViewLineService.allStageTeamNames(List.of(
                "match_id,team1,team2,path,prediction",
                "1,A1(France),B2(Spain),predicted,France (55%)",
                "2,C1(Brazil),A2(France),alt,Brazil (60%)"
        ));

        assertEquals(List.of("Brazil", "France", "Spain"), teams);
    }

    @Test
    void filterViewLinesTreatsResultsAsActualRows() {
        List<String> rows = ViewLineService.filterViewLines(List.of(
                "match_id,team1,team2,path,prediction",
                "1,France,Spain,predicted,France",
                "1,France,Spain,results,Spain",
                "2,Brazil,Germany,fixture,Brazil",
                "3,Morocco,Portugal,result_upset,Morocco"
        ), "results", "");

        assertEquals(4, rows.size());
        assertTrue(rows.get(1).contains("results"));
        assertTrue(rows.get(2).contains("fixture"));
        assertTrue(rows.get(3).contains("result_upset"));
    }

    @Test
    void filterViewLinesTreatsLiveAsPredictionRows() {
        List<String> rows = ViewLineService.filterViewLines(List.of(
                "match_id,team1,team2,path,prediction",
                "1,France,Spain,predicted,France",
                "2,Brazil,Germany,live,Brazil",
                "3,Morocco,Portugal,alt,Morocco"
        ), "prediction", "");

        assertEquals(3, rows.size());
        assertTrue(rows.get(1).contains("predicted"));
        assertTrue(rows.get(2).contains("live"));
    }

    @Test
    void paginateLinesKeepsHeaderAndRequestedPage() {
        List<String> rows = ViewLineService.paginateLines(List.of(
                "header", "one", "two", "three", "four"
        ), 2, 2);

        assertEquals(List.of("header", "three", "four"), rows);
    }
}
