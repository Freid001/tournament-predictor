package com.tournamentpredictor.services.bracket;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayBuilderTest {

    private final DisplayBuilder displayBuilder = new DisplayBuilder(new TokenResolver());

    @Test
    void groupSlotIncludesSameGroupPositionAlternatives() {
        Map<String, String> groups = new HashMap<>();
        groups.put("F1", "Belgium");
        groups.put("F2", "Croatia");
        groups.put("F3", "Morocco");
        groups.put("F4", "Canada");

        Map<String, String> groupWinner = new HashMap<>();
        groupWinner.put("F1", "yes");
        groupWinner.put("F2", "no");
        groupWinner.put("F3", "no");
        groupWinner.put("F4", "no");

        Map<String, String> runnerUp = new HashMap<>();
        runnerUp.put("F1", "maybe");
        runnerUp.put("F2", "yes");
        runnerUp.put("F3", "maybe");
        runnerUp.put("F4", "no");

        Map<String, String> thirdPlace = new HashMap<>();
        thirdPlace.put("F1", "no");
        thirdPlace.put("F2", "maybe");
        thirdPlace.put("F3", "yes");
        thirdPlace.put("F4", "no");

        List<String> displays = displayBuilder.buildDisplays("F2", groups, groupWinner, runnerUp, thirdPlace);

        assertEquals(List.of("F2(Croatia)", "F2(Belgium)", "F2(Morocco)"), displays);
    }

    @Test
    void winnerDisplaysCarryAlternativeCompositeRowsForward() {
        List<String> rows = List.of(
                "match_id,team1,team2,path,prediction",
                "M80,L1(England),EHIJK3(Senegal),predicted,England (63%)",
                "M80,L1(England),EHIJK3(Ivory Coast),alt,England (84%)");

        List<String> displays = displayBuilder.buildWinnerDisplays("W80", Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(), rows);

        assertTrue(displays.contains("W80(EHIJK3(Ivory Coast))"));
    }
}
