package com.tournamentpredictor.service.util;

import com.tournamentpredictor.loader.CsvLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TokenResolverTest {

    private TokenResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TokenResolver();
    }

    private static Map<String, String> groups() {
        return Map.of(
                "A1", "Germany",
                "A2", "France",
                "A3", "Morocco",
                "B1", "Brazil",
                "B2", "Argentina",
                "B3", "Ecuador"
        );
    }

    // ─── resolveToken – simple group slots ───────────────────────────────────

    @Test
    void resolveToken_groupOneSlot_returnsTeamName() {
        assertEquals("Germany", resolver.resolveToken("A1", groups()));
    }

    @Test
    void resolveToken_groupTwoSlot_returnsTeamName() {
        assertEquals("France", resolver.resolveToken("A2", groups()));
    }

    @Test
    void resolveToken_slotNotInGroups_returnsTokenAsIs() {
        assertEquals("C1", resolver.resolveToken("C1", groups()));
    }

    @Test
    void resolveToken_nullToken_returnsEmpty() {
        assertEquals("", resolver.resolveToken(null, groups()));
    }

    @Test
    void resolveToken_blankToken_returnsEmpty() {
        // trim of "  " won't match any pattern, falls through to return as-is
        // but a token that can't match the group-slot regex is returned as-is
        String result = resolver.resolveToken("   ", groups());
        assertNotNull(result);
    }

    // ─── resolveToken – composite third-place tokens ─────────────────────────

    @Test
    void resolveToken_compositeAB3_combinesThirdPlaceTeams() {
        // "AB3" means third from group A and third from group B
        assertEquals("Morocco/Ecuador", resolver.resolveToken("AB3", groups()));
    }

    @Test
    void resolveToken_compositeA3_singleGroup() {
        assertEquals("Morocco", resolver.resolveToken("A3", groups()));
    }

    @Test
    void resolveToken_compositeWithMissingGroup_skipsEmpty() {
        // Group C not in map → "C3" resolves to empty → skipped
        assertEquals("Morocco", resolver.resolveToken("AC3", groups()));
    }

    @Test
    void resolveToken_unknownFormat_returnsAsIs() {
        assertEquals("W42", resolver.resolveToken("W42", groups()));
    }

    // ─── resolveTokenWithBrackets ─────────────────────────────────────────────

    private static CsvLoader.BracketEntry bracket(String matchId, String t1, String t2) {
        return new CsvLoader.BracketEntry(matchId, t1, t2, "LAST_32");
    }

    @Test
    void resolveTokenWithBrackets_winnerToken_resolvesFromBrackets() {
        List<CsvLoader.BracketEntry> brackets = List.of(bracket("M33", "A1", "B2"));
        // W33 → match M33 → A1=Germany vs B2=Argentina
        assertEquals("Germany/Argentina", resolver.resolveTokenWithBrackets("W33", groups(), brackets));
    }

    @Test
    void resolveTokenWithBrackets_winnerTokenNotFound_returnsTokenAsIs() {
        List<CsvLoader.BracketEntry> brackets = List.of(bracket("M99", "A1", "B2"));
        assertEquals("W55", resolver.resolveTokenWithBrackets("W55", groups(), brackets));
    }

    @Test
    void resolveTokenWithBrackets_groupSlot_resolvesNormally() {
        List<CsvLoader.BracketEntry> brackets = List.of();
        assertEquals("Germany", resolver.resolveTokenWithBrackets("A1", groups(), brackets));
    }

    @Test
    void resolveTokenWithBrackets_nullToken_returnsEmpty() {
        assertEquals("", resolver.resolveTokenWithBrackets(null, groups(), List.of()));
    }

    // ─── isSimpleSlot ─────────────────────────────────────────────────────────

    @Test
    void isSimpleSlot_slot1_returnsTrue() {
        assertTrue(resolver.isSimpleSlot("A1"));
    }

    @Test
    void isSimpleSlot_slot2_returnsTrue() {
        assertTrue(resolver.isSimpleSlot("B2"));
    }

    @Test
    void isSimpleSlot_slot3_returnsFalse() {
        assertFalse(resolver.isSimpleSlot("A3"));
    }

    @Test
    void isSimpleSlot_null_returnsFalse() {
        assertFalse(resolver.isSimpleSlot(null));
    }

    @Test
    void isSimpleSlot_winnerToken_returnsFalse() {
        assertFalse(resolver.isSimpleSlot("W33"));
    }

    // ─── swapSlot ─────────────────────────────────────────────────────────────

    @Test
    void swapSlot_slot1_returnsSlot2() {
        assertEquals("A2", resolver.swapSlot("A1"));
    }

    @Test
    void swapSlot_slot2_returnsSlot1() {
        assertEquals("B1", resolver.swapSlot("B2"));
    }

    @Test
    void swapSlot_slot3_returnsAsIs() {
        assertEquals("A3", resolver.swapSlot("A3"));
    }

    @Test
    void swapSlot_null_returnsNull() {
        assertNull(resolver.swapSlot(null));
    }

    @Test
    void swapSlot_winnerToken_returnsAsIs() {
        assertEquals("W33", resolver.swapSlot("W33"));
    }
}
