package com.tournamentpredictor.services.report;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleReporterTest {

    private ConsoleReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new ConsoleReporter();
    }

    // ---- displayWidth ----

    @Test
    void displayWidth_asciiString_equalToLength() {
        assertEquals(7, ConsoleReporter.displayWidth("England"));
    }

    @Test
    void displayWidth_emptyString_returnsZero() {
        assertEquals(0, ConsoleReporter.displayWidth(""));
    }

    @Test
    void displayWidth_flagEmoji_twoRegionalIndicators_returnsTwo() {
        // 🇩🇪 = U+1F1E9 U+1F1EA — two regional indicators counted as 1 each = 2
        assertEquals(2, ConsoleReporter.displayWidth("🇩🇪"));
    }

    @Test
    void displayWidth_flagEmojiPlusText_combinedWidth() {
        // "🇩🇪 Germany" = 2 (flag) + 1 (space) + 7 (Germany) = 10
        assertEquals(10, ConsoleReporter.displayWidth("🇩🇪 Germany"));
    }

    @Test
    void displayWidth_zvjVariationSelector_notCounted() {
        // Variation selector U+FE0F should not add to width
        String withVariant = "1\uFE0F"; // digit + variation selector
        // variation selector contributes 0, digit contributes 1
        assertEquals(1, ConsoleReporter.displayWidth(withVariant));
    }

    // ---- padToDisplayWidth ----

    @Test
    void padToDisplayWidth_shorterString_padded() {
        String result = ConsoleReporter.padToDisplayWidth("Hi", 5);
        assertEquals("Hi   ", result);
    }

    @Test
    void padToDisplayWidth_exactWidth_noChange() {
        String result = ConsoleReporter.padToDisplayWidth("Hello", 5);
        assertEquals("Hello", result);
    }

    @Test
    void padToDisplayWidth_longerString_noTruncation() {
        String result = ConsoleReporter.padToDisplayWidth("TooLong", 3);
        assertEquals("TooLong", result); // no truncation
    }

    @Test
    void padToDisplayWidth_withFlag_paddedByDisplayWidth() {
        // "🇩🇪" has display width 2; target 5 → should add 3 spaces
        String result = ConsoleReporter.padToDisplayWidth("🇩🇪", 5);
        assertEquals("🇩🇪   ", result);
    }

    // ---- getFlag ----

    @Test
    void getFlag_knownTeam_returnsFlag() {
        assertEquals("🇩🇪", ConsoleReporter.getFlag("Germany"));
    }

    @Test
    void getFlag_anotherKnownTeam() {
        assertEquals("🇫🇷", ConsoleReporter.getFlag("France"));
    }

    @Test
    void getFlag_unknownTeam_returnsPlaceholder() {
        String result = ConsoleReporter.getFlag("UnknownTeamXYZ");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void getFlag_england_returnsUKFlag() {
        assertEquals("🇬🇧", ConsoleReporter.getFlag("England"));
    }
}
