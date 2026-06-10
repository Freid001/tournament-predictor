package com.tournamentpredictor.services.calculation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotStatusEvaluatorTest {
    private SlotStatusEvaluator slotStatusEvaluator;
    private Map<String, String> teamGW;
    private Map<String, String> teamRU;
    private Map<String, String> teamTP;

    @BeforeEach
    void setUp() {
        slotStatusEvaluator = new SlotStatusEvaluator(new EloCalculator());

        teamGW = new HashMap<>();
        teamGW.put("Mexico", "yes");
        teamGW.put("Germany", "yes");
        teamGW.put("Canada", "maybe");
        teamGW.put("Switzerland", "maybe");
        teamGW.put("Qatar", "no");

        teamRU = new HashMap<>();
        teamRU.put("Canada", "yes");
        teamRU.put("Ecuador", "yes");
        teamRU.put("SouthKorea", "maybe");
        teamRU.put("Mexico", "maybe");
        teamRU.put("Switzerland", "maybe");
        teamRU.put("Qatar", "no");

        teamTP = new HashMap<>();
        teamTP.put("Scotland", "yes");
        teamTP.put("Bosnia", "yes");
        teamTP.put("SaudiArabia", "maybe");
        teamTP.put("Haiti", "maybe");
        teamTP.put("Paraguay", "no");
    }

    @Test
    void x1SlotGwYes_isPredicted() {
        assertTrue(slotStatusEvaluator.isDisplayPredicted("A1", "A1(Mexico)", teamGW, teamRU, teamTP));
    }

    @Test
    void x1SlotGwMaybe_isNotPredicted() {
        assertFalse(slotStatusEvaluator.isDisplayPredicted("A1", "A1(Canada)", teamGW, teamRU, teamTP));
    }

    @Test
    void x1SlotGwNo_isNotPredicted() {
        assertFalse(slotStatusEvaluator.isDisplayPredicted("A1", "A1(Qatar)", teamGW, teamRU, teamTP));
    }

    @Test
    void x2SlotRuYes_isPredicted() {
        assertTrue(slotStatusEvaluator.isDisplayPredicted("B2", "B2(Canada)", teamGW, teamRU, teamTP));
    }

    @Test
    void x2SlotRuMaybe_isNotPredicted() {
        assertFalse(slotStatusEvaluator.isDisplayPredicted("B2", "B2(Mexico)", teamGW, teamRU, teamTP));
    }

    @Test
    void x2SlotRuNo_isNotPredicted() {
        assertFalse(slotStatusEvaluator.isDisplayPredicted("B2", "B2(Qatar)", teamGW, teamRU, teamTP));
    }

    @Test
    void compositeSlotTpYes_isPredicted() {
        assertTrue(slotStatusEvaluator.isDisplayPredicted("ABCDF3", "ABCDF3(Scotland)", teamGW, teamRU, teamTP));
    }

    @Test
    void compositeSlotTpMaybe_isNotPredicted() {
        assertFalse(slotStatusEvaluator.isDisplayPredicted("CEFHI3", "CEFHI3(SaudiArabia)", teamGW, teamRU, teamTP));
    }

    @Test
    void compositeSlotTpNo_isNotPredicted() {
        assertFalse(slotStatusEvaluator.isDisplayPredicted("ABCDF3", "ABCDF3(Paraguay)", teamGW, teamRU, teamTP));
    }

    @Test
    void x1SlotGwMaybe_isMaybe() {
        assertTrue(slotStatusEvaluator.isDisplayMaybe("A1", "A1(Canada)", teamGW, teamRU, teamTP));
    }

    @Test
    void x1SlotGwYes_isNotMaybe() {
        assertFalse(slotStatusEvaluator.isDisplayMaybe("A1", "A1(Mexico)", teamGW, teamRU, teamTP));
    }

    @Test
    void x2SlotRuMaybe_isMaybe() {
        assertTrue(slotStatusEvaluator.isDisplayMaybe("B2", "B2(Mexico)", teamGW, teamRU, teamTP));
    }

    @Test
    void x2SlotRuYes_isNotMaybe() {
        assertFalse(slotStatusEvaluator.isDisplayMaybe("B2", "B2(Canada)", teamGW, teamRU, teamTP));
    }

    @Test
    void compositeSlotTpMaybe_isMaybe() {
        assertTrue(slotStatusEvaluator.isDisplayMaybe("CEFHI3", "CEFHI3(SaudiArabia)", teamGW, teamRU, teamTP));
    }

    @Test
    void compositeSlotTpYes_isNotMaybe() {
        assertFalse(slotStatusEvaluator.isDisplayMaybe("ABCDF3", "ABCDF3(Scotland)", teamGW, teamRU, teamTP));
    }
}
