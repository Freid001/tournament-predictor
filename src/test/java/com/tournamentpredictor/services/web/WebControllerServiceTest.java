package com.tournamentpredictor.services.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class WebControllerServiceTest {

    @Test
    void ensureLiveSimulationExistsReturnsWhenRoundMissing() {
        WebControllerService service = new WebControllerService(null);

        assertDoesNotThrow(() -> service.ensureLiveSimulationExists("world_cup_2026", null));
    }
}
