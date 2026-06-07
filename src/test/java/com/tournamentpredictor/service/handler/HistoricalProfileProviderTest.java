package com.tournamentpredictor.service.handler;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoricalProfileProviderTest {
    @Test
    void ageClassifiesYoungAndAgingSquadsFromPreTournamentBirthDates() {
        LocalDate kickoff = LocalDate.of(2022, 11, 20);
        assertEquals(1, HistoricalProfileProvider.age(births(kickoff, 24, 23), kickoff).level());
        assertEquals(2, HistoricalProfileProvider.age(births(kickoff, 30, 23), kickoff).level());
        assertEquals(0, HistoricalProfileProvider.age(births(kickoff, 27, 23), kickoff).level());
    }

    @Test
    void ageRequiresEnoughObservedPlayers() {
        LocalDate kickoff = LocalDate.of(2022, 11, 20);
        assertEquals(0, HistoricalProfileProvider.age(births(kickoff, 22, 14), kickoff).level());
    }

    @Test
    void qualityResidualThresholdsMapToSignedLevels() {
        assertEquals(2, HistoricalProfileProvider.level(0.45));
        assertEquals(1, HistoricalProfileProvider.level(0.20));
        assertEquals(0, HistoricalProfileProvider.level(0.19));
        assertEquals(-1, HistoricalProfileProvider.level(-0.20));
        assertEquals(-2, HistoricalProfileProvider.level(-0.45));
    }

    @Test
    void heatProxyOnlyAppliesToConfiguredHotWorldCups() {
        assertEquals(3, HistoricalProfileProvider.heat("world_cup_2022", "Qatar"));
        assertEquals(2, HistoricalProfileProvider.heat("world_cup_2014", "Brazil"));
        assertEquals(0, HistoricalProfileProvider.heat("euros_2024", "Turkey"));
        assertEquals(0, HistoricalProfileProvider.heat("world_cup_2018", "Nigeria"));
    }

    private static List<LocalDate> births(LocalDate kickoff, int age, int count) {
        List<LocalDate> births = new ArrayList<>();
        for (int i = 0; i < count; i++) births.add(kickoff.minusYears(age).minusDays(i));
        return births;
    }
}
