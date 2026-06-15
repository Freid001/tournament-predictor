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
    void groupSlotIncludesSameGroupPositionAlternativesAsStructuredOptions() {
        Map<String, String> groups = new HashMap<>();
        groups.put("F1", "Belgium");
        groups.put("F2", "Croatia");
        groups.put("F3", "Morocco");
        groups.put("F4", "Canada");

        Map<String, String> runnerUp = new HashMap<>();
        runnerUp.put("F1", "maybe");
        runnerUp.put("F2", "yes");
        runnerUp.put("F3", "maybe");
        runnerUp.put("F4", "no");

        List<DisplayBuilder.RouteOption> options = displayBuilder.buildOptions("F2", groups, Map.of(), runnerUp, Map.of());

        assertEquals(List.of("Croatia", "Belgium", "Morocco", "Canada"), options.stream().map(DisplayBuilder.RouteOption::team).toList());
        assertTrue(options.stream().allMatch(option -> option.slot().equals("F2") && option.bracketSlot().equals("F2")));
    }

    @Test
    void groupSlotIncludesNoStatusTeamsAsAlternativeFinishPermutations() {
        Map<String, String> groups = new HashMap<>();
        groups.put("E1", "Germany");
        groups.put("E2", "Ivory Coast");
        groups.put("E3", "Ecuador");
        groups.put("E4", "Cape Verde");

        Map<String, String> groupWinner = Map.of("E1", "yes", "E2", "no", "E3", "maybe", "E4", "no");
        Map<String, String> runnerUp = Map.of("E1", "no", "E2", "no", "E3", "yes", "E4", "maybe");
        Map<String, String> thirdPlace = Map.of("E1", "no", "E2", "yes", "E3", "maybe", "E4", "no");

        List<DisplayBuilder.RouteOption> options = displayBuilder.buildOptions("E2", groups, groupWinner, runnerUp, thirdPlace);

        assertTrue(options.stream().anyMatch(option -> option.team().equals("Ivory Coast") && option.slot().equals("E2")),
                "A team marked no for runner-up should still be available as an alternative group-position permutation");
    }

    @Test
    void winnerOptionsCarryAlternativeCompositeRowsForwardFromStructuredMetadata() {
        List<String> rows = List.of(
                "match_id,team1,team2,path,prediction,team1_slot,team1_team,team1_source_match,team1_group_finish,team1_bracket_slot,team2_slot,team2_team,team2_source_match,team2_group_finish,team2_bracket_slot",
                "M80,England,Senegal,predicted,England (63%),L1,England,,L1,L1,EHIJK3,Senegal,,K3,EHIJK3",
                "M80,England,Ivory Coast,alt,England (84%),L1,England,,L1,L1,EHIJK3,Ivory Coast,,E3,EHIJK3");

        List<DisplayBuilder.RouteOption> options = displayBuilder.buildWinnerOptions("W80", Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(), rows);

        assertTrue(options.stream().anyMatch(option -> option.team().equals("Ivory Coast")
                && option.slot().equals("W80")
                && option.sourceMatchId().equals("M80")
                && option.groupFinish().equals("E3")
                && option.bracketSlot().equals("EHIJK3")));
    }

    @Test
    void routeOptionsKeepTeamAndSlotMetadataSeparate() {
        Map<String, String> groups = new HashMap<>();
        groups.put("F1", "Belgium");
        groups.put("F2", "Croatia");
        groups.put("F3", "Morocco");
        groups.put("F4", "Canada");

        Map<String, String> runnerUp = Map.of("F1", "maybe", "F2", "yes", "F3", "maybe", "F4", "no");

        List<DisplayBuilder.RouteOption> options = displayBuilder.buildOptions("F2", groups, Map.of(), runnerUp, Map.of());

        assertEquals("Croatia", options.get(0).team());
        assertEquals("F2", options.get(0).slot());
        assertEquals("F2", options.get(0).groupFinish());
        assertEquals("F2", options.get(0).bracketSlot());
    }
}
