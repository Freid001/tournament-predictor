package com.tournamentpredictor.service.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlReporterTest {

    @Test
    void pathFatigueNotesShowPerOpponentEloContributions() {
        EloBreakdown breakdown = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                "", "", "", "", "", "",
                List.of(), List.of(),
                -19, "Hard", "Brazil:-5 > France:-5 > Argentina:-9");

        String html = HtmlReporter.buildTeamBreakdownHtml("England", breakdown);

        assertTrue(html.contains("Brazil"));
        assertTrue(html.contains("France"));
        assertTrue(html.contains("Argentina"));
        assertTrue(html.contains(">-5</span>"));
        assertTrue(html.contains(">-9</span>"));
        assertTrue(html.contains("Hard <span style=\"white-space:nowrap\">-19</span>"));
    }

    @Test
    void pathFatigueNotesSeparateGroupLoadFromKnockoutPath() {
        EloBreakdown breakdown = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                "", "", "", "", "", "",
                List.of(), List.of(),
                -14, "Hard", "G|Brazil:-3 > G|Morocco:-3 > Brazil:-8");

        String html = HtmlReporter.buildTeamBreakdownHtml("Scotland", breakdown, "C3");

        assertTrue(html.contains("G:</span>"));
        assertTrue(html.contains("KO:</span>"));
        assertTrue(html.indexOf("C3") < html.indexOf("Brazil"));
        assertTrue(html.contains(">-3</span>"));
        assertTrue(html.contains(">-8</span>"));
    }

    @Test
    void pathBreadcrumbShowsOriginSlotBeforePriorOpponents() {
        EloBreakdown breakdown = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                "", "", "", "", "", "",
                List.of(), List.of(),
                -10, "Hard", "Brazil:-5 > France:-5");

        String html = HtmlReporter.buildTeamBreakdownHtml("England", breakdown, "I3");

        assertTrue(html.contains("I3"));
        assertTrue(html.indexOf("I3") < html.indexOf("Brazil"));
    }
}
