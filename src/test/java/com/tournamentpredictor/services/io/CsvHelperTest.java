package com.tournamentpredictor.services.io;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvHelperTest {

    private CsvHelper csvHelper;

    @BeforeEach
    void setUp() {
        csvHelper = new CsvHelper();
    }

    // ---- filterPrimaryOnly ----

    @Test
    void filterPrimaryOnly_emptyInput_returnsEmpty() {
        assertTrue(csvHelper.filterPrimaryOnly(List.of()).isEmpty());
    }

    @Test
    void filterPrimaryOnly_headerOnlyInput_returnsHeader() {
        List<String> result = csvHelper.filterPrimaryOnly(List.of("h1,h2,h3,h4"));
        assertEquals(1, result.size());
        assertEquals("h1,h2,h3,h4", result.get(0));
    }

    @Test
    void filterPrimaryOnly_keepsPrimaryRows() {
        List<String> input = List.of(
                "match_id,team1,team2,path",
                "M1,A,B,predicted",
                "M2,C,D,alt"
        );
        List<String> result = csvHelper.filterPrimaryOnly(input);
        assertEquals(2, result.size()); // header + primary
        assertTrue(result.get(1).contains("M1"));
    }

    @Test
    void filterPrimaryOnly_stripsAltRows() {
        List<String> input = List.of(
                "match_id,team1,team2,path",
                "M1,A,B,predicted",
                "M2,C,D,alt",
                "M3,E,F,alt"
        );
        List<String> result = csvHelper.filterPrimaryOnly(input);
        assertEquals(2, result.size());
    }

    @Test
    void filterPrimaryOnly_stripsBlankLines() {
        List<String> input = List.of(
                "match_id,team1,team2,path",
                "M1,A,B,predicted",
                "",
                "M2,C,D,alt"
        );
        List<String> result = csvHelper.filterPrimaryOnly(input);
        assertEquals(2, result.size()); // header + M1 only
    }

    @Test
    void filterPrimaryOnly_shortRowsIgnored() {
        List<String> input = List.of(
                "match_id,team1,team2,path",
                "M1,A,B"  // only 3 cols, no path column
        );
        List<String> result = csvHelper.filterPrimaryOnly(input);
        assertEquals(1, result.size()); // header only
    }

    // ---- sortGroupsPrimaryFirst ----

    @Test
    void sortGroupsPrimaryFirst_emptyInput_returnsEmpty() {
        assertTrue(csvHelper.sortGroupsPrimaryFirst(List.of()).isEmpty());
    }

    @Test
    void sortGroupsPrimaryFirst_headerOnlyInput_returnsHeader() {
        List<String> input = List.of("h1,h2,h3,path");
        List<String> result = csvHelper.sortGroupsPrimaryFirst(input);
        assertEquals(1, result.size());
    }

    @Test
    void sortGroupsPrimaryFirst_primaryFirst_noChange() {
        List<String> input = List.of(
                "match_id,team1,team2,path",
                "M1,A,B,predicted",
                "M1,A,B,alt"
        );
        List<String> result = csvHelper.sortGroupsPrimaryFirst(input);
        assertEquals(3, result.size());
        assertTrue(result.get(1).contains("predicted"));
        assertTrue(result.get(2).contains("alt"));
    }

    @Test
    void sortGroupsPrimaryFirst_altBeforePrimary_primaryMovedFirst() {
        List<String> input = List.of(
                "match_id,team1,team2,path",
                "M1,A,B,alt",
                "M1,A,B,predicted"
        );
        List<String> result = csvHelper.sortGroupsPrimaryFirst(input);
        assertEquals(3, result.size());
        assertTrue(result.get(1).contains("predicted"));
        assertTrue(result.get(2).contains("alt"));
    }

    @Test
    void sortGroupsPrimaryFirst_blankSeparatorPreserved() {
        List<String> input = List.of(
                "match_id,team1,team2,path",
                "M1,A,B,predicted",
                "",
                "M2,C,D,predicted"
        );
        List<String> result = csvHelper.sortGroupsPrimaryFirst(input);
        assertEquals(4, result.size());
        assertTrue(result.get(2).trim().isEmpty());
    }
}
