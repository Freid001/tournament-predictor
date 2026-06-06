package com.tournamentpredictor.service.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

        reporter.withSimulationAdvance(Map.of("Spain", "78.4", "Uruguay", "21.6"));
        reporter.printMatchups("Round of 32", lines, new EloCalculator(), null, Map.of("Spain", "5/2", "Uruguay", "10/1"), breakdowns);

        String html = reporter.getHtml();
        assertTrue(html.contains("Most Likely Winner"));
        assertFalse(html.contains("<th>Winner<"));
        assertTrue(html.contains("78.4%"));
        assertTrue(html.contains("21.6%"));
        assertFalse(html.contains("Odds to Reach Last 16"));
        assertFalse(html.contains("Net Winnings (Bet=£10)"));
        assertTrue(html.contains("Score model"));
        assertTrue(html.contains("xG"));
        assertTrue(html.contains("Spain"));
        assertTrue(html.contains("Uruguay"));
        assertTrue(html.contains("1.55"));
        assertTrue(html.contains("1.05"));
        assertTrue(html.contains("90-min W"));
        assertTrue(html.contains("Advance"));
        assertTrue(html.contains("90-min draw"));
        assertTrue(html.contains("Spain after ET/pens"));
        assertTrue(html.contains("Score model preview. Simulations sample 90-minute goals; level games are resolved by the Elo-based ET/pens tiebreak."));
    }

    
    @Test
    void matchupRowsShowSpecificMatchSimulationForAltRoutes() {
        HtmlReporter reporter = new HtmlReporter();
        List<String> lines = List.of(
                "match_id,team1,team2,path,elo,do_you_disagree",
                "M1,A1(Spain),B2(Uruguay),predicted,Spain (62%),",
                "M2,A1(Spain),C2(Canada),alt,Spain (62%),"
        );
        reporter.printMatchups("Last 32", lines, new EloCalculator(), null, Map.of(), Map.of(
                "Spain", breakdown(1900),
                "Uruguay", breakdown(1700),
                "Canada", breakdown(1800)));

        String html = reporter.getHtml();
        assertTrue(html.contains("Most Likely Winner"));
        assertFalse(html.contains("match sim"));
        assertFalse(html.contains("fw-semibold text-primary"));
        assertTrue(html.contains("Canada"));
        assertFalse(html.contains("Canada 21.6%"));
        assertFalse(html.contains("all routes"));
        assertFalse(html.contains("No simulation data"));
    }

    
    @Test
    void matchupRowsShowExactRouteLikelihood() {
        HtmlReporter reporter = new HtmlReporter()
                .withMatchupLikelihood(Map.of("M1|Spain|Uruguay", "42.0"));
        List<String> lines = List.of(
                "match_id,team1,team2,path,elo,do_you_disagree",
                "M1,A1(Spain),B2(Uruguay),predicted,Spain (62%),"
        );

        reporter.printMatchups("Last 32", lines, new EloCalculator(), null, Map.of(), Map.of(
                "Spain", breakdown(1900),
                "Uruguay", breakdown(1700)));

        String html = reporter.getHtml();
        assertTrue(html.contains("Match Likelihood"));
        assertTrue(html.contains("42.0%"));
    }

    @Test
    void matchupRowsDefaultToAllHighlightPredictedAndSortByLikelihood() {
        HtmlReporter reporter = new HtmlReporter()
                .withMatchupLikelihood(Map.of(
                        "M1|Spain|Uruguay", "25.0",
                        "M2|Spain|Canada", "60.0"));
        List<String> lines = List.of(
                "match_id,team1,team2,path,elo,do_you_disagree",
                "M1,A1(Spain),B2(Uruguay),predicted,Spain (62%),",
                "M2,A1(Spain),C2(Canada),alt,Spain (55%),"
        );

        reporter.printMatchups("Last 32", lines, new EloCalculator(), null, Map.of(), Map.of(
                "Spain", breakdown(1900),
                "Uruguay", breakdown(1700),
                "Canada", breakdown(1800)));

        String html = reporter.getHtml();
        assertTrue(html.contains("active path-btn\" data-path=\"both"));
        assertTrue(html.indexOf(">All</button>") < html.indexOf(">Alternative</button>"));
        assertTrue(html.indexOf(">Alternative</button>") < html.indexOf(">Predicted</button>"));
        assertTrue(html.contains("class=\"table-primary\" data-path=\"predicted"));
        assertTrue(html.contains("<th>Match Likelihood</th>"));
        assertFalse(html.contains("<th class=\"text-end\">Match Likelihood</th>"));
        assertTrue(html.indexOf("Match Likelihood</th>") < html.indexOf("<th>Path</th>"));
        assertTrue(html.contains(">Predicted</span>"));
        assertTrue(html.contains(">Alternative</span>"));
        assertFalse(html.contains(">Runner-up</span>"));
        assertFalse(html.contains(">Alt</span>"));
        assertTrue(html.contains("class=\"expand-icon me-1\" aria-hidden=\"true\"") );
        assertTrue(html.contains("icon.classList.toggle('expanded',isHidden)"));
        assertFalse(html.contains("▶"));
        assertFalse(html.contains("▼"));
        assertTrue(html.indexOf("60.0%") < html.indexOf("25.0%"));
    }

    private static EloBreakdown breakdown(int elo) {
        return new EloBreakdown(elo, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                "", "", "", "", "", "",
                List.of(), List.of(),
                0, "");
    }
}
