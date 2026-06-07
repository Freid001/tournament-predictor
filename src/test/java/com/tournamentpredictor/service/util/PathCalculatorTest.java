package com.tournamentpredictor.service.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PathCalculatorTest {
    private PathCalculator pathCalculator;
    private Map<String, String> teamGW;
    private Map<String, String> teamRU;
    private Map<String, String> teamTP;

    @BeforeEach
    void setUp() {
        EloCalculator eloCalculator = new EloCalculator();
        pathCalculator = new PathCalculator(new SlotStatusEvaluator(eloCalculator), eloCalculator);

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
    void x1YesVsX2Yes_predictedYes() {
        assertEquals("predicted", pathCalculator.computePredictedMatch(
                "E1", "E1(Germany)", "B2", "B2(Canada)", teamGW, teamRU, teamTP));
    }

    @Test
    void x1YesVsX2Maybe_predictedMaybe() {
        assertEquals("alt", pathCalculator.computePredictedMatch(
                "E1", "E1(Germany)", "B2", "B2(Mexico)", teamGW, teamRU, teamTP));
    }

    @Test
    void x1MaybeVsX2Yes_predictedMaybe() {
        assertEquals("alt", pathCalculator.computePredictedMatch(
                "A1", "A1(Canada)", "B2", "B2(Canada)", teamGW, teamRU, teamTP));
    }

    @Test
    void x1MaybeVsX2Maybe_predictedMaybe() {
        assertEquals("alt", pathCalculator.computePredictedMatch(
                "A1", "A1(Canada)", "B2", "B2(Switzerland)", teamGW, teamRU, teamTP));
    }

    @Test
    void x1YesVsX3Yes_predictedYes() {
        assertEquals("predicted", pathCalculator.computePredictedMatch(
                "E1", "E1(Germany)", "ABCDF3", "ABCDF3(Scotland)", teamGW, teamRU, teamTP));
    }

    @Test
    void x1YesVsX3Maybe_predictedMaybe() {
        assertEquals("alt", pathCalculator.computePredictedMatch(
                "E1", "E1(Germany)", "ABCDF3", "ABCDF3(Haiti)", teamGW, teamRU, teamTP));
    }

    @Test
    void x1MaybeVsX3Yes_predictedMaybe() {
        assertEquals("alt", pathCalculator.computePredictedMatch(
                "A1", "A1(Canada)", "ABCDF3", "ABCDF3(Scotland)", teamGW, teamRU, teamTP));
    }

    @Test
    void x1MaybeVsX3Maybe_predictedMaybe() {
        assertEquals("alt", pathCalculator.computePredictedMatch(
                "A1", "A1(Canada)", "ABCDF3", "ABCDF3(Haiti)", teamGW, teamRU, teamTP));
    }

    @Test
    void x2YesVsX2Yes_predictedYes() {
        assertEquals("predicted", pathCalculator.computePredictedMatch(
                "A2", "A2(Ecuador)", "B2", "B2(Canada)", teamGW, teamRU, teamTP));
    }

    @Test
    void x2YesVsX2Maybe_predictedMaybe() {
        assertEquals("alt", pathCalculator.computePredictedMatch(
                "A2", "A2(Ecuador)", "B2", "B2(Mexico)", teamGW, teamRU, teamTP));
    }

    @Test
    void x2MaybeVsX2Yes_predictedMaybe() {
        assertEquals("alt", pathCalculator.computePredictedMatch(
                "A2", "A2(Mexico)", "B2", "B2(Canada)", teamGW, teamRU, teamTP));
    }

    @Test
    void x2MaybeVsX2Maybe_predictedMaybe() {
        assertEquals("alt", pathCalculator.computePredictedMatch(
                "A2", "A2(Mexico)", "B2", "B2(Switzerland)", teamGW, teamRU, teamTP));
    }

    @Test
    void x2YesVsX3Yes_predictedYes() {
        assertEquals("predicted", pathCalculator.computePredictedMatch(
                "B2", "B2(Canada)", "ABCDF3", "ABCDF3(Scotland)", teamGW, teamRU, teamTP));
    }

    @Test
    void x2YesVsX3Maybe_predictedMaybe() {
        assertEquals("alt", pathCalculator.computePredictedMatch(
                "B2", "B2(Canada)", "ABCDF3", "ABCDF3(Haiti)", teamGW, teamRU, teamTP));
    }

    @Test
    void x2MaybeVsX3Yes_predictedMaybe() {
        assertEquals("alt", pathCalculator.computePredictedMatch(
                "B2", "B2(Mexico)", "ABCDF3", "ABCDF3(Scotland)", teamGW, teamRU, teamTP));
    }

    @Test
    void x2MaybeVsX3Maybe_predictedMaybe() {
        assertEquals("alt", pathCalculator.computePredictedMatch(
                "B2", "B2(Mexico)", "ABCDF3", "ABCDF3(Haiti)", teamGW, teamRU, teamTP));
    }


    @Test
    void completedRouteUsesActualUpsetHistory() {
        assertEquals("alt", pathCalculator.classifyCompletedRoute("upset",
                "K@M84|Austria:-1 > K@M101|France:-43",
                "K@M87|Panama:0 > K@M102|England:-19"));
        assertEquals("upset", pathCalculator.classifyCompletedRoute("alt",
                "K@M84|Austria:-1", "U@M102|England:-24"));
        assertEquals("predicted", pathCalculator.classifyCompletedRoute("predicted",
                "K@M84|Austria:-1", "K@M86|Uruguay:0"));
    }

    @Test
    void upsetParticipantMakesLaterMatchAnUpsetRoute() {
        assertEquals("upset", pathCalculator.computePathFromSlots("predicted", "upset"));
        assertEquals("upset", pathCalculator.computePathFromSlots("alt", "upset"));
    }
}
