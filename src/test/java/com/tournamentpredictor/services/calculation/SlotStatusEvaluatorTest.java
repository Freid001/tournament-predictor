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
    void teamNameStatusChecksAvoidSlotWrappedDisplayInput() {
        assertTrue(slotStatusEvaluator.isTeamPredicted("A1", "Mexico", teamGW, teamRU, teamTP));
        assertTrue(slotStatusEvaluator.isTeamMaybe("A1", "Canada", teamGW, teamRU, teamTP));
        assertFalse(slotStatusEvaluator.isTeamPredicted("A1", "Canada", teamGW, teamRU, teamTP));
    }

    @Test
    void x1SlotGwYes_isPredicted() {
        assertTrue(slotStatusEvaluator.isTeamPredicted("A1", "Mexico", teamGW, teamRU, teamTP));
    }

    @Test
    void x1SlotGwMaybe_isNotPredicted() {
        assertFalse(slotStatusEvaluator.isTeamPredicted("A1", "Canada", teamGW, teamRU, teamTP));
    }

    @Test
    void x1SlotGwNo_isNotPredicted() {
        assertFalse(slotStatusEvaluator.isTeamPredicted("A1", "Qatar", teamGW, teamRU, teamTP));
    }

    @Test
    void x2SlotRuYes_isPredicted() {
        assertTrue(slotStatusEvaluator.isTeamPredicted("B2", "Canada", teamGW, teamRU, teamTP));
    }

    @Test
    void x2SlotRuMaybe_isNotPredicted() {
        assertFalse(slotStatusEvaluator.isTeamPredicted("B2", "Mexico", teamGW, teamRU, teamTP));
    }

    @Test
    void x2SlotRuNo_isNotPredicted() {
        assertFalse(slotStatusEvaluator.isTeamPredicted("B2", "Qatar", teamGW, teamRU, teamTP));
    }

    @Test
    void compositeSlotTpYes_isPredicted() {
        assertTrue(slotStatusEvaluator.isTeamPredicted("ABCDF3", "Scotland", teamGW, teamRU, teamTP));
    }

    @Test
    void compositeSlotTpMaybe_isNotPredicted() {
        assertFalse(slotStatusEvaluator.isTeamPredicted("CEFHI3", "SaudiArabia", teamGW, teamRU, teamTP));
    }

    @Test
    void compositeSlotTpNo_isNotPredicted() {
        assertFalse(slotStatusEvaluator.isTeamPredicted("ABCDF3", "Paraguay", teamGW, teamRU, teamTP));
    }

    @Test
    void x1SlotGwMaybe_isMaybe() {
        assertTrue(slotStatusEvaluator.isTeamMaybe("A1", "Canada", teamGW, teamRU, teamTP));
    }

    @Test
    void x1SlotGwYes_isNotMaybe() {
        assertFalse(slotStatusEvaluator.isTeamMaybe("A1", "Mexico", teamGW, teamRU, teamTP));
    }

    @Test
    void x2SlotRuMaybe_isMaybe() {
        assertTrue(slotStatusEvaluator.isTeamMaybe("B2", "Mexico", teamGW, teamRU, teamTP));
    }

    @Test
    void x2SlotRuYes_isNotMaybe() {
        assertFalse(slotStatusEvaluator.isTeamMaybe("B2", "Canada", teamGW, teamRU, teamTP));
    }

    @Test
    void compositeSlotTpMaybe_isMaybe() {
        assertTrue(slotStatusEvaluator.isTeamMaybe("CEFHI3", "SaudiArabia", teamGW, teamRU, teamTP));
    }

    @Test
    void compositeSlotTpYes_isNotMaybe() {
        assertFalse(slotStatusEvaluator.isTeamMaybe("ABCDF3", "Scotland", teamGW, teamRU, teamTP));
    }
}
