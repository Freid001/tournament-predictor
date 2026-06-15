package com.tournamentpredictor.services.route;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutePathValidatorTest {

    @Test
    void detectsRepeatedTeamsInOpponentChain() {
        assertTrue(RoutePathValidator.hasRepeatedPathTeam(
                "D4|Bosnia and Herzegovina:0 > K@M82|Czechia:-4 > K@M98|Bosnia and Herzegovina:-8"));
    }

    @Test
    void detectsRepeatedTeamsInRouteHistory() {
        assertTrue(RoutePathValidator.hasRepeatedPathTeam(
                "D4:M82|Bosnia and Herzegovina|alt|W > M98|Czechia|alt|W > M103|Bosnia and Herzegovina|alt|W"));
    }

    @Test
    void invalidMatchupWhenNextOpponentAlreadyAppearsInPath() {
        assertTrue(RoutePathValidator.invalidMatchupRoute(
                "Canada", "Australia", "K@M80|Australia:0 > K@M92|Panama:-4", "K@M83|Czechia:-1"));
    }

    @Test
    void allowsDistinctOpponentPath() {
        assertFalse(RoutePathValidator.invalidMatchupRoute(
                "Canada", "Australia", "K@M80|Norway:0 > K@M92|Panama:-4", "K@M83|Czechia:-1"));
    }
}
