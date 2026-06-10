package com.tournamentpredictor.services.web;

import com.tournamentpredictor.view.KnockoutViewRows;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultLineServiceTest {

    @Test
    void predictedWinnersByMatchParsesWinnerFromPredictionText() {
        Map<String, String> winners = ResultLineService.predictedWinnersByMatch(List.of(
                "match_id,team1,team2,path,prediction",
                "1,A1(France),B2(Spain),predicted,France (55%)"
        ));

        assertEquals("France", winners.get(KnockoutViewRows.matchKey("France", "Spain")));
    }

    @Test
    void buildActualOnlyRowsCopiesPredictedRowsWithActualWinner() {
        List<String> rows = ResultLineService.buildActualOnlyRows(List.of(
                "match_id,team1,team2,path,prediction",
                "1,France,Spain,predicted,France (55%)",
                "1,France,Spain,alt,Spain (45%)"
        ), Map.of(KnockoutViewRows.matchKey("France", "Spain"), "Spain"));

        assertEquals(List.of(
                "match_id,team1,team2,path,prediction",
                "1,France,Spain,results,Spain"
        ), rows);
    }

    @Test
    void mergeViewLinesPlacesOverlayAfterMatchingBaseRow() {
        List<String> rows = ResultLineService.mergeViewLines(List.of(
                "match_id,team1,team2,path,prediction",
                "1,France,Spain,predicted,France",
                "2,Brazil,Germany,predicted,Brazil"
        ), List.of(
                "match_id,team1,team2,path,prediction",
                "2,Brazil,Germany,results,Germany"
        ));

        assertEquals("2,Brazil,Germany,predicted,Brazil", rows.get(2));
        assertEquals("2,Brazil,Germany,results,Germany", rows.get(3));
    }

    @Test
    void actualAdvanceTeamsReturnsOnlyNonDrawWinners() {
        Set<String> teams = ResultLineService.actualAdvanceTeams(List.of(
                "match_id,team1,team2,path,prediction",
                "1,France,Spain,results,Spain",
                "2,Brazil,Germany,actual,Draw"
        ));

        assertEquals(Set.of("Spain"), teams);
        assertTrue(teams.contains("Spain"));
    }
}
