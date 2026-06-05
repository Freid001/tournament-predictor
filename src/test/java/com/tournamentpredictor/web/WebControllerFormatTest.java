package com.tournamentpredictor.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebControllerFormatTest {

    @Test
    void positiveBonus_prefixedWithPlus() {
        assertEquals("+18", WebController.formatQualBonus("18"));
    }

    @Test
    void negativeBonus_showsMinusSign() {
        assertEquals("-5", WebController.formatQualBonus("-5"));
    }

    @Test
    void zeroBonus_showsHost() {
        assertEquals("Host", WebController.formatQualBonus("0"));
    }

    @Test
    void nullInput_showsDash() {
        assertEquals("—", WebController.formatQualBonus(null));
    }

    @Test
    void blankInput_showsDash() {
        assertEquals("—", WebController.formatQualBonus("   "));
    }

    @Test
    void emptyInput_showsDash() {
        assertEquals("—", WebController.formatQualBonus(""));
    }

    @Test
    void nonNumericInput_returnedAsIs() {
        assertEquals("N/A", WebController.formatQualBonus("N/A"));
    }

    @Test
    void maxBonus_prefixedWithPlus() {
        assertEquals("+50", WebController.formatQualBonus("50"));
    }

    @Test
    void maxNegativeBonus_showsMinusSign() {
        assertEquals("-50", WebController.formatQualBonus("-50"));
    }
}
