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
                "M1,Germany,France,predicted,Germany (55%)",
                "",
                "M2,England,Spain,predicted,England (60%)"
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
                "match_id,team1,team2,path,eloPrediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,"
                        + "team1_slot,team1_team,team1_source_match,team1_group_finish,team1_bracket_slot,"
                        + "team2_slot,team2_team,team2_source_match,team2_group_finish,team2_bracket_slot",
                "M1,Germany,France,predicted,Germany (55%),0,0,,,B2,Germany,,B2,B2,A1,France,,A1,A1"
        );
        List<String> result = scorer.scoreLines(input);
        assertEquals(2, result.size());
        String[] cols = result.get(1).split(",", -1);
        assertEquals(25, cols.length);
        assertEquals("M1", cols[0]);
        assertEquals("Germany", cols[1]);
        assertEquals("France", cols[2]);
        assertEquals("predicted", cols[3]);
        assertEquals("0", cols[9]);
        assertEquals("0", cols[10]);
        assertEquals("", cols[11]); // team1_path_opponent
        assertEquals("", cols[12]); // team2_path_opponent
        assertEquals("Germany (55%)", cols[13]);
        assertEquals("model", cols[14]);
        assertEquals("B2", cols[15]);
        assertEquals("Germany", cols[16]);
        assertEquals("", cols[17]);
        assertEquals("B2", cols[18]);
        assertEquals("B2", cols[19]);
        assertEquals("A1", cols[20]);
        assertEquals("France", cols[21]);
        assertEquals("", cols[22]);
        assertEquals("A1", cols[23]);
        assertEquals("A1", cols[24]);
    }


    @Test
    void structuredInputKeepsRawTeamColumnsInScoredOutput() {
        List<String> input = List.of(
                "match_id,team1,team2,path,eloPrediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,"
                        + "team1_slot,team1_team,team1_source_match,team1_group_finish,team1_bracket_slot,"
                        + "team2_slot,team2_team,team2_source_match,team2_group_finish,team2_bracket_slot",
                "M80,England,Norway,alt,England (63%),0,0,,,L1,England,,L1,L1,EHIJK3,Norway,,I3,EHIJK3"
        );

        String[] cols = scorer.scoreLines(input).get(1).split(",", -1);

        assertEquals("England", cols[1]);
        assertEquals("Norway", cols[2]);
        assertFalse(cols[1].contains("("));
        assertFalse(cols[2].contains("("));
        assertEquals("L1", cols[15]);
        assertEquals("England", cols[16]);
        assertEquals("EHIJK3", cols[20]);
        assertEquals("Norway", cols[21]);
    }

    @Test
    void explicitRouteMetadataIsPreservedWhenPresent() {
        List<String> input = List.of(
                "match_id,team1,team2,path,eloPrediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,"
                        + "team1_slot,team1_team,team1_source_match,team1_group_finish,team1_bracket_slot,"
                        + "team2_slot,team2_team,team2_source_match,team2_group_finish,team2_bracket_slot",
                "M80,England,Norway,alt,England (63%),0,0,,,L1,England,,L1,L1,EHIJK3,Norway,,I3,EHIJK3"
        );

        String[] cols = scorer.scoreLines(input).get(1).split(",", -1);

        assertEquals("L1", cols[18]);
        assertEquals("L1", cols[19]);
        assertEquals("I3", cols[23]);
        assertEquals("EHIJK3", cols[24]);
    }

    @Test
    void multipleValidRowsAllScored() {
        List<String> input = List.of(
                "match_id,team1,team2,path,eloPrediction",
                "M1,Germany,France,predicted,Germany (55%)",
                "M2,England,Spain,alt,England (60%)"
        );
        List<String> result = scorer.scoreLines(input);
        assertEquals(3, result.size());
    }

    @Test
    void pathDifficultyColumnsArePassedThrough() {
        List<String> result = scorer.scoreLines(List.of(
                "match_id,team1,team2,path,eloPrediction,team1_path_fatigue,team2_path_fatigue",
                "M1,Germany,France,predicted,Germany (55%),100,-80"
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
                "match_id,team1,team2,path,eloPrediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,"
                        + "team1_slot,team1_team,team1_source_match,team1_group_finish,team1_bracket_slot,"
                        + "team2_slot,team2_team,team2_source_match,team2_group_finish,team2_bracket_slot",
                "M1,Germany,France,predicted,Germany (55%),0,0,,,B2,Germany,,B2,B2,A1,France,,A1,A1"
        ));
        String[] cols = result.get(1).split(",", -1);
        assertEquals("1900", cols[5]);
        assertEquals("12", cols[6]);
        assertEquals("1880", cols[7]);
        assertEquals("-4", cols[8]);
    }
}
