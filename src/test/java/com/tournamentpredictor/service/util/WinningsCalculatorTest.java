package com.tournamentpredictor.service.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WinningsCalculatorTest {

    private WinningsCalculator calc;

    @BeforeEach
    void setUp() {
        calc = new WinningsCalculator();
    }

    // ─── Valid odds ───────────────────────────────────────────────────────────

    @Test
    void evens_returnsDoubleStake() {
        // 1/1 at £10 → £10 profit + £10 stake = £20.00
        assertEquals("20.00", calc.calculateWinnings("1/1", 10.0));
    }

    @Test
    void threeToOne_returnsCorrectPayout() {
        // 3/1 at £10 → £30 profit + £10 stake = £40.00
        assertEquals("40.00", calc.calculateWinnings("3/1", 10.0));
    }

    @Test
    void fractionalOdds_returnsCorrectPayout() {
        // 5/2 at £10 → £25 profit + £10 stake = £35.00
        assertEquals("35.00", calc.calculateWinnings("5/2", 10.0));
    }

    @Test
    void shortOdds_returnsCorrectPayout() {
        // 1/4 at £20 → £5 profit + £20 stake = £25.00
        assertEquals("25.00", calc.calculateWinnings("1/4", 20.0));
    }

    @Test
    void decimalNumerator_returnsCorrectPayout() {
        // 3.5/1 at £10 → £35 profit + £10 stake = £45.00
        assertEquals("45.00", calc.calculateWinnings("3.5/1", 10.0));
    }

    @Test
    void spacesAroundSlash_handledGracefully() {
        // " 5 / 2 " at £10 → same as 5/2
        assertEquals("35.00", calc.calculateWinnings(" 5 / 2 ", 10.0));
    }

    @Test
    void smallStake_paysoutCorrectly() {
        // 2/1 at £1.50 → £3 profit + £1.50 stake = £4.50
        assertEquals("4.50", calc.calculateWinnings("2/1", 1.5));
    }

    // ─── Invalid / edge-case inputs ───────────────────────────────────────────

    @Test
    void nullOdds_returnsEmpty() {
        assertEquals("", calc.calculateWinnings(null, 10.0));
    }

    @Test
    void emptyOdds_returnsEmpty() {
        assertEquals("", calc.calculateWinnings("", 10.0));
    }

    @Test
    void noSlash_returnsEmpty() {
        assertEquals("", calc.calculateWinnings("3-1", 10.0));
    }

    @Test
    void zeroDenominator_returnsEmpty() {
        assertEquals("", calc.calculateWinnings("3/0", 10.0));
    }

    @Test
    void nonNumericNumerator_returnsEmpty() {
        assertEquals("", calc.calculateWinnings("abc/2", 10.0));
    }

    @Test
    void nonNumericDenominator_returnsEmpty() {
        assertEquals("", calc.calculateWinnings("3/abc", 10.0));
    }
}
