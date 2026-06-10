package com.tournamentpredictor.services.calculation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PredictionScorerTest {

    private PredictionScorer scorer;

    @BeforeEach
    void setUp() {
        EloCalculator elo = new EloCalculator();
        scorer = new PredictionScorer(elo);
    }

    @Test
    void emptyInput_returnsHeaderOnly() {
        List<String> result = scorer.scoreLines(List.of());
        assertEquals(1, result.size());
        assertEquals(PredictionScorer.OUTPUT_HEADER, result.get(0));
    }

    @Test
    void headerOnlyInput_returnsOutputHeaderOnly() {
        List<String> result = scorer.scoreLines(List.of("match_id,t1,t2,path,elo"));
        assertEquals(1, result.size());
        assertEquals(PredictionScorer.OUTPUT_HEADER, result.get(0));
    }

    @Test
    void blankSeparatorLinesArePreserved() {
        List<String> input = List.of(
                "match_id,team1,team2,path,eloPrediction",
                "M1,🇩🇪B2(Germany),🇫🇷A1(France),predicted,Germany (55%)",
                "",
                "M2,🏴C1(England),🇪🇸D2(Spain),predicted,England (60%)"
        );
        List<String> result = scorer.scoreLines(input);
        assertEquals(4, result.size());
        assertTrue(result.get(2).trim().isEmpty());
    }

    @Test
    void shortRowsAreSilentlySkipped() {
        List<String> input = List.of(
                "match_id,team1,team2,path,eloPrediction",
                "M1,only,three,cols"
        );
        List<String> result = scorer.scoreLines(input);
        assertEquals(1, result.size());
    }

    @Test
    void validRowIncludesModelAndSelectionMetadata() {
        List<String> input = List.of(
                "match_id,team1,team2,path,eloPrediction",
                "M1,🇩🇪B2(Germany),🇫🇷A1(France),predicted,Germany (55%)"
        );
        List<String> result = scorer.scoreLines(input);
        assertEquals(2, result.size());
        String[] cols = result.get(1).split(",", -1);
        assertEquals(15, cols.length);
        assertEquals("M1", cols[0]);
        assertEquals("🇩🇪B2(Germany)", cols[1]);
        assertEquals("🇫🇷A1(France)", cols[2]);
        assertEquals("predicted", cols[3]);
        assertEquals("0", cols[9]);
        assertEquals("0", cols[10]);
        assertEquals("", cols[11]); // team1_path_opponent
        assertEquals("", cols[12]); // team2_path_opponent
        assertEquals("Germany (55%)", cols[13]);
        assertEquals("model", cols[14]);
    }

    @Test
    void multipleValidRowsAllScored() {
        List<String> input = List.of(
                "match_id,team1,team2,path,eloPrediction",
                "M1,🇩🇪B2(Germany),🇫🇷A1(France),predicted,Germany (55%)",
                "M2,🏴C1(England),🇪🇸D2(Spain),alt,England (60%)"
        );
        List<String> result = scorer.scoreLines(input);
        assertEquals(3, result.size());
    }

    @Test
    void pathDifficultyColumnsArePassedThrough() {
        List<String> result = scorer.scoreLines(List.of(
                "match_id,team1,team2,path,eloPrediction,team1_path_fatigue,team2_path_fatigue",
                "M1,🇩🇪B2(Germany),🇫🇷A1(France),predicted,Germany (55%),100,-80"
        ));
        String[] cols = result.get(1).split(",", -1);
        assertEquals("100", cols[9]);
        assertEquals("-80", cols[10]);
    }

    @Test
    void snapshotsAreAppendedWhenAvailable() {
        scorer.setSnapshots(Map.of(
                "Germany", new TeamEloSnapshot(1900, 12, 1912),
                "France", new TeamEloSnapshot(1880, -4, 1876)
        ));
        List<String> result = scorer.scoreLines(List.of(
                "match_id,team1,team2,path,eloPrediction",
                "M1,🇩🇪B2(Germany),🇫🇷A1(France),predicted,Germany (55%)"
        ));
        String[] cols = result.get(1).split(",", -1);
        assertEquals("1900", cols[5]);
        assertEquals("12", cols[6]);
        assertEquals("1880", cols[7]);
        assertEquals("-4", cols[8]);
    }
}
