package com.tournamentpredictor.service.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
        assertTrue(html.contains("Knockout ELO -5"));
        assertTrue(html.contains("Knockout ELO -9"));
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

        assertTrue(html.contains("Group ELO -3"));
        assertTrue(html.contains("Knockout ELO -8"));
        assertTrue(html.indexOf("C3") < html.indexOf("Brazil"));
        assertTrue(html.contains("G|Brazil:-3") || html.contains("Group ELO -3"));
        assertTrue(html.contains("Knockout ELO -8"));
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


    @Test
    void formDotsRenderInsideEloTableWithContributionTooltip() {
        EloBreakdown breakdown = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 12,
                List.<String[]>of(new String[]{"W", "Norway", "3-0", "9"}));

        String html = HtmlReporter.buildTeamBreakdownHtml("Spain", breakdown);

        assertTrue(html.contains("Qualification Form"));
        assertTrue(html.contains("border-radius:50%"));
        assertTrue(html.contains("Spain vs"));
        assertTrue(html.contains("Norway"));
        assertTrue(html.contains("3-0"));
        assertTrue(html.contains("ELO +9"));
        assertTrue(html.indexOf("Qualification Form") < html.indexOf("border-radius:50%"));
    }


    @Test
    void matchupDetailsShowExpectedGoalsScoreModel() {
        HtmlReporter reporter = new HtmlReporter();
        List<String> lines = List.of(
                "match_id,team1,team2,path,elo,do_you_disagree",
                "M1,A1(Spain),B2(Uruguay),predicted,Spain (62%),"
        );
        Map<String, EloBreakdown> breakdowns = Map.of(
                "Spain", new EloBreakdown(1900, false, 0,
                        0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                        "", "", "", "", "", "",
                        List.of(), List.of(),
                        0, ""),
                "Uruguay", new EloBreakdown(1700, false, 0,
                        0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                        "", "", "", "", "", "",
                        List.of(), List.of(),
                        0, "")
        );

        reporter.printMatchups("Last 32", lines, new EloCalculator(), null, Map.of(), breakdowns);

        String html = reporter.getHtml();
        assertTrue(html.contains("Score model"));
        assertTrue(html.contains("xG"));
        assertTrue(html.contains("Spain"));
        assertTrue(html.contains("Uruguay"));
        assertTrue(html.contains("1.55"));
        assertTrue(html.contains("1.05"));
        assertTrue(html.contains("90-min W"));
        assertTrue(html.contains("Advance"));
        assertTrue(html.contains("Score model preview. Match picks still use the existing Elo winner rule."));
    }
}
