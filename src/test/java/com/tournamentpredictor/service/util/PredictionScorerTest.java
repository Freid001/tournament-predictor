package com.tournamentpredictor.service.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PredictionScorerTest {

    @TempDir
    Path tempDir;

    private PredictionScorer scorer;

    @BeforeEach
    void setUp() {
        EloCalculator elo = new EloCalculator();
        HeadToHeadCalculator h2h = new HeadToHeadCalculator(tempDir);
        scorer = new PredictionScorer(elo, h2h);
    }

    @Test
    void emptyInput_returnsHeaderOnly() {
        List<String> result = scorer.scoreLines(List.of(), Map.of());
        assertEquals(1, result.size());
        assertEquals(PredictionScorer.OUTPUT_HEADER, result.get(0));
    }

    @Test
    void headerOnlyInput_returnsOutputHeaderOnly() {
        List<String> result = scorer.scoreLines(List.of("match_id,t1,t2,path,elo"), Map.of());
        assertEquals(1, result.size());
        assertEquals(PredictionScorer.OUTPUT_HEADER, result.get(0));
    }

    @Test
    void blankSeparatorLinesArePreserved() {
        List<String> input = List.of(
                "match_id,team1,team2,path,eloPrediction",
                "M1,🇩🇪B2(Germany),🇫🇷A1(France),primary,Germany (55%)",
                "",
                "M2,🏴󠁧󠁢󠁥󠁮󠁧󠁿C1(England),🇪🇸D2(Spain),primary,England (60%)"
        );
        List<String> result = scorer.scoreLines(input, Map.of());
        assertEquals(4, result.size()); // header + M1 + blank + M2
        assertTrue(result.get(2).trim().isEmpty());
    }

    @Test
    void shortRowsAreSilentlySkipped() {
        List<String> input = List.of(
                "match_id,team1,team2,path,eloPrediction",
                "M1,only,three,cols"
        );
        List<String> result = scorer.scoreLines(input, Map.of());
        assertEquals(1, result.size()); // header only
    }

    @Test
    void validRowProduces9Columns() {
        List<String> input = List.of(
                "match_id,team1,team2,path,eloPrediction",
                "M1,🇩🇪B2(Germany),🇫🇷A1(France),primary,Germany (55%)"
        );
        List<String> result = scorer.scoreLines(input, Map.of());
        assertEquals(2, result.size());
        String[] cols = result.get(1).split(",", -1);
        assertEquals(9, cols.length);
        assertEquals("M1", cols[0]);
        assertEquals("🇩🇪B2(Germany)", cols[1]);
        assertEquals("🇫🇷A1(France)", cols[2]);
        assertEquals("primary", cols[3]);
    }

    @Test
    void disagreeOverrideIsApplied() {
        List<String> input = List.of(
                "match_id,team1,team2,path,eloPrediction",
                "M1,🇩🇪B2(Germany),🇫🇷A1(France),primary,Germany (55%)"
        );
        String disagreeKey = "M1|🇩🇪B2(Germany)|🇫🇷A1(France)";
        List<String> result = scorer.scoreLines(input, Map.of(disagreeKey, "yes"));
        assertEquals(2, result.size());
        String[] cols = result.get(1).split(",", -1);
        // When disagree="yes" the predicted_winner (col 4) should be flipped to team2
        assertTrue(cols[4].contains("France"),
                "Expected France after disagree override, got: " + cols[4]);
    }

    @Test
    void disagreeKeyMustMatchExactly() {
        List<String> input = List.of(
                "match_id,team1,team2,path,eloPrediction",
                "M1,🇩🇪B2(Germany),🇫🇷A1(France),primary,Germany (55%)"
        );
        // Wrong key (clean team names instead of display strings) — no override should apply
        String wrongKey = "M1|Germany|France";
        List<String> result = scorer.scoreLines(input, Map.of(wrongKey, "yes"));
        String[] cols = result.get(1).split(",", -1);
        assertTrue(cols[4].contains("Germany"),
                "No override expected with wrong key, got: " + cols[4]);
    }

    @Test
    void multipleValidRowsAllScored() {
        List<String> input = List.of(
                "match_id,team1,team2,path,eloPrediction",
                "M1,🇩🇪B2(Germany),🇫🇷A1(France),primary,Germany (55%)",
                "M2,🏴󠁧󠁢󠁥󠁮󠁧󠁿C1(England),🇪🇸D2(Spain),alt,England (60%)"
        );
        List<String> result = scorer.scoreLines(input, Map.of());
        assertEquals(3, result.size()); // header + 2 rows
    }
}
