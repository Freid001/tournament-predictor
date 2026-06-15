package com.tournamentpredictor.services.report;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.calculation.EloCalculator;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
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
    void tournamentPathShowsOriginSlotInsteadOfGroupStagePlaceholder() {
        EloBreakdown breakdown = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                "", "", "", "", "", "",
                List.of(), List.of(),
                0, "Medium", "Group stage");

        String html = HtmlReporter.buildTeamBreakdownHtml("Japan", breakdown, "E1");

        assertTrue(html.contains("E1"));
        assertFalse(html.contains("Group stage</a>"));
        assertFalse(html.contains(">Group stage<"));
    }


    @Test
    void tournamentPathShowsWhenFatigueLabelIsNeutralButPathExists() {
        EloBreakdown breakdown = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0,
                "", "", "", "", "", "",
                List.of(), List.of(),
                0, "", "K@M51|Qatar:0");

        String html = HtmlReporter.buildTeamBreakdownHtml("England", breakdown, "B1");

        assertTrue(html.contains("Tournament Path"));
        assertTrue(html.contains("B1"));
        assertTrue(html.contains("Qatar"));
        assertFalse(html.contains(">Group stage<"));
    }

    @Test
    void formDotsRenderInsideEloTableWithContributionTooltip() {
        EloBreakdown breakdown = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 12,
                0, 0, 0, 0,
                "", "", "", "",
                List.of(), List.<String[]>of(new String[]{"W", "Norway", "3-0", "12", "2026-06-04"}));

        String html = HtmlReporter.buildTeamBreakdownHtml("Spain", breakdown);

        assertTrue(html.contains("Pre-tournament Form"));
        assertFalse(html.contains("Qualification Form"));
        assertTrue(html.contains("border-radius:50%"));
        assertTrue(html.contains("Spain vs"));
        assertTrue(html.contains("Norway"));
        assertTrue(html.contains("3-0"));
        assertTrue(html.contains("ELO +12"));
        assertTrue(html.contains("ELO +12<br>2026-06-04"));
        assertTrue(html.indexOf("ELO +12") < html.indexOf("2026-06-04"));
        assertTrue(html.indexOf("Pre-tournament Form") < html.indexOf("border-radius:50%"));
    }


    @Test
    void formTooltipStillSupportsLegacyEntriesWithoutDate() {
        EloBreakdown breakdown = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, -8,
                0, 0, 0, 0,
                "", "", "", "",
                List.of(), List.<String[]>of(new String[]{"L", "Portugal", "0-2", "-8"}));

        String html = HtmlReporter.buildTeamBreakdownHtml("Spain", breakdown);

        assertTrue(html.contains("ELO -8"));
        assertTrue(html.contains("Portugal"));
    }

    @Test
    void eloCalculationUsesStyledRowsAndReadableNegativePathColour() {
        EloBreakdown breakdown = new EloBreakdown(1900, false, 0,
                0, 0, 0, 0, 0, 0, 0, 12,
                0, 0, 0, 0,
                0, 0, 0, 0,
                "", "", "", "", "", "",
                List.of(), List.<String[]>of(new String[]{"W", "Norway", "3-0", "12"}),
                -10, "Hard", "Brazil:-5");

        String html = HtmlReporter.buildTeamBreakdownHtml("Spain", breakdown);

        assertTrue(html.contains("elo-calc-card"));
        assertTrue(html.contains("elo-base-pill"));
        assertTrue(html.contains("table-danger elo-negative"));
        assertTrue(html.contains("<div class=\"elo-signal-label\">Path Fatigue</div>"));
    }

    @Test
    void tournamentPathOpponentLinksOpenInNewWindow() {
        HtmlReporter reporter = new HtmlReporter().withPathNavigation("world_cup_2026", "last_16");
        List<String> lines = List.of(
                "match_id,team1,team2,path,elo,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent",
                "M1,Spain,Uruguay,predicted,Spain (62%),0,0,K@M9|France:-5,"
        );

        reporter.printMatchups("Last 16", lines, new EloCalculator(), null, Map.of(), Map.of(
                "Spain", breakdown(1900),
                "Uruguay", breakdown(1700)));

        String html = reporter.getHtml();
        assertTrue(html.contains("target='_blank' rel='noopener'"));
        assertTrue(html.contains("/view/path-game?tournament=world_cup_2026"));
    }


    @Test
    void matchupDetailsShowExpectedGoalsScoreModel() {
        HtmlReporter reporter = new HtmlReporter();
        List<String> lines = List.of(
                "match_id,team1,team2,path,elo",
                "M1,Spain,Uruguay,predicted,Spain (62%),"
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
        assertFalse(html.contains("78.4%"));
        assertFalse(html.contains("21.6%"));
        assertTrue(html.matches("(?s).*Spain \\([0-9]+%\\).*"));
        assertFalse(html.contains("head-to-head"));
        assertFalse(html.contains("tournament routes"));
        assertFalse(html.contains("Odds to Reach Last 16"));
        assertFalse(html.contains("Net Winnings"));
        assertTrue(html.contains("Score model"));
        assertTrue(html.contains("Goal model inputs"));
        assertTrue(html.contains("Excluded from Adjusted ELO"));
        assertTrue(html.contains("ELO calculation"));
        assertTrue(html.indexOf("Goal model inputs") < html.indexOf("ELO calculation"));

        assertTrue(html.contains("xG"));
        assertTrue(html.contains("Spain"));
        assertTrue(html.contains("Uruguay"));
        assertTrue(html.contains("1.55"));
        assertTrue(html.contains("1.05"));
        assertTrue(html.contains("90-min W"));
        assertTrue(html.contains("Advance"));
        assertTrue(html.contains("Draw after 90 min"));
        assertTrue(html.contains("Spain advances"));
        assertTrue(html.contains("Most likely score"));
        assertTrue(html.contains("exact-score likelihood"));
        assertTrue(html.contains("Exact-score probabilities are individually low"));
        assertTrue(html.contains("not a confident result forecast"));
    }


    @Test
    void matchupRowsShowSpecificMatchSimulationForAltRoutes() {
        HtmlReporter reporter = new HtmlReporter();
        List<String> lines = List.of(
                "match_id,team1,team2,path,elo",
                "M1,Spain,Uruguay,predicted,Spain (62%),",
                "M2,Spain,Canada,alt,Spain (62%),"
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
                .withMatchupLikelihood(Map.of("M1|Spain|Uruguay", "42.0"))
                .withMatchupSimulationRuns(Map.of("M1|Spain|Uruguay", "10500"));
        List<String> lines = List.of(
                "match_id,team1,team2,path,elo",
                "M1,Spain,Uruguay,predicted,Spain (62%),"
        );

        reporter.printMatchups("Last 32", lines, new EloCalculator(), null, Map.of(), Map.of(
                "Spain", breakdown(1900),
                "Uruguay", breakdown(1700)));

        String html = reporter.getHtml();
        assertTrue(html.contains("Match Likelihood"));
        assertTrue(html.contains("42.0%"));
        assertTrue(html.contains("10,500 simulations for this matchup"));

        HtmlReporter reversedReporter = new HtmlReporter()
                .withMatchupSimulationRuns(Map.of("M1|Spain|Uruguay", "10500"));
        reversedReporter.printMatchups("Last 32", List.of(
                "match_id,team1,team2,path,elo",
                "M1,Uruguay,Spain,predicted,Spain (62%),"),
                new EloCalculator(), null, Map.of(), Map.of(
                        "Spain", breakdown(1900), "Uruguay", breakdown(1700)));
        assertTrue(reversedReporter.getHtml().contains("10,500 simulations for this matchup"));
    }

    @Test
    void predictedOnlyGroupTableDoesNotShowEmptyAlternativeFilter() {
        HtmlReporter reporter = new HtmlReporter()
                .withSimulationAdvance(Map.of("Mexico", "91.3", "South Korea", "76.3"));
        List<String> lines = List.of(
                "match_id,team1,team2,path,elo",
                "A1,Mexico,South Korea,predicted,Mexico (67%),"
        );

        reporter.printMatchups("Group matches", lines, new EloCalculator(), null, Map.of(), Map.of(
                "Mexico", breakdown(1900),
                "South Korea", breakdown(1800)));

        String html = reporter.getHtml();
        assertFalse(html.contains("<button type=\"button\" class=\"btn btn-outline-secondary path-btn\" data-path=\"alt\""));
        assertFalse(html.contains("<button type=\"button\" class=\"btn btn-outline-secondary path-btn\" data-path=\"upset\""));
        assertTrue(html.contains("<button type=\"button\" class=\"btn btn-outline-secondary path-btn"));
        assertTrue(html.contains("data-path=\"predicted\""));
    }

    @Test
    void matchupRowsDefaultToAllHighlightPredictedAndSortByPathPriority() {
        HtmlReporter reporter = new HtmlReporter()
                .withMatchupLikelihood(Map.of(
                        "M1|Spain|Uruguay", "25.0",
                        "M2|Spain|Canada", "60.0"));
        List<String> lines = List.of(
                "match_id,team1,team2,path,elo",
                "M1,Spain,Uruguay,predicted,Spain (62%),",
                "M2,Spain,Canada,alt,Spain (55%),"
        );

        reporter.printMatchups("Last 32", lines, new EloCalculator(), null, Map.of(), Map.of(
                "Spain", breakdown(1900),
                "Uruguay", breakdown(1700),
                "Canada", breakdown(1800)));

        String html = reporter.getHtml();
        assertTrue(html.contains("path-btn active\" data-path=\"all"));
        assertTrue(html.indexOf(">All</button>") < html.indexOf(">Predicted Matchups</button>"));
        assertTrue(html.indexOf(">Predicted Matchups</button>") < html.indexOf(">Alternative Matchups</button>"));
        assertTrue(html.contains("class=\"table-primary\" data-path=\"predicted"));
        assertTrue(html.contains("<th>Match Likelihood</th>"));
        assertFalse(html.contains("<th class=\"text-end\">Match Likelihood</th>"));
        assertTrue(html.indexOf("Match Likelihood</th>") < html.indexOf("<th>Path</th>"));
        assertTrue(html.contains(">Predicted Matchup</span>"));
        assertFalse(html.contains(">Runner-up</span>"));
        assertFalse(html.contains(">Alt</span>"));
        assertTrue(html.contains("class=\"expand-icon\" aria-hidden=\"true\"") );
        assertTrue(html.contains("<th style=\"width:28px\" aria-label=\"Details\"></th><th>Match</th>"));
        assertTrue(html.contains("</span></td><td>M2</td>"));
        assertTrue(html.contains("icon.classList.toggle('expanded',isHidden)"));
        assertTrue(html.contains("localStorage.removeItem('predictor_team')"));
        assertTrue(html.contains("if(teamSel)teamSel.value=''"));
        assertTrue(html.contains("table-no-results"));
        assertTrue(html.contains("No results."));
        assertTrue(html.contains("table-pagination"));
        assertTrue(html.contains("const pageSize=50"));
        assertTrue(html.contains("function changeTablePage"));
        assertFalse(html.contains("▶"));
        assertFalse(html.contains("▼"));
        assertTrue(html.indexOf("25.0%") < html.indexOf("60.0%"));
    }

    @Test
    void knockoutMatchTableKeepsFullPathFiltersEvenWhenOnlyPredictedRowsAreVisible() {
        HtmlReporter reporter = new HtmlReporter()
                .withSimulationAdvance(Map.of("France", "72.0", "Spain", "68.0"));
        List<String> lines = List.of(
                "match_id,team1,team2,path,elo",
                "M1,France,Spain,predicted,France (55%),"
        );

        reporter.printMatchups("Last 16", lines, new EloCalculator(), null, Map.of(), Map.of(
                "France", breakdown(1900),
                "Spain", breakdown(1850)));

        String html = reporter.getHtml();
        assertTrue(html.contains("data-path=\"results\""));
        assertTrue(html.contains("data-path=\"prediction\""));
        assertTrue(html.contains(">Predicted Matchups</button>"));
        assertTrue(html.contains("data-path=\"alt\""));
        assertFalse(html.contains("data-path=\"upset\""));
    }

    @Test
    void laterKnockoutRoundsUseLast32TableConfiguration() {
        HtmlReporter reporter = new HtmlReporter()
                .withSimulationAdvance(Map.of("Spain", "72.0", "France", "68.0", "Brazil", "55.0"))
                .withMatchupLikelihood(Map.of(
                        "M89|Spain|France", "100.0",
                        "M89|Spain|Brazil", "35.0",
                        "M90|Germany|Canada", "0.00042"));
        List<String> lines = List.of(
                "match_id,team1,team2,path,elo",
                "M89,Spain,France,predicted,Spain (55%),",
                "M89,Spain,Brazil,alt,Spain (60%),",
                "M90,Germany,Canada,predicted,Germany (58%),",
                "M91,Italy,Japan,alt,Italy (51%),",
                "M92,England,DR Congo,upset,DR Congo (17%),"
        );

        reporter.printMatchups("Last 16", lines, new EloCalculator(), null,
                Map.of("Spain", "2/1", "France", "3/1", "Brazil", "4/1"),
                Map.of("Spain", breakdown(1900), "France", breakdown(1880), "Brazil", breakdown(1850), "England", breakdown(2020), "DR Congo", breakdown(1655)));

        String html = reporter.getHtml();
        assertTrue(html.contains("path-btn active\" data-path=\"all"));
        assertTrue(html.contains("<th>Most Likely Winner</th>") || html.contains("<th>Winner / Result</th>"));
        assertTrue(html.contains("<th>Match Likelihood</th>"));
        assertTrue(html.contains("<th>Path</th>"));
        assertTrue(html.contains(">Predicted Matchup</span>"));
        assertTrue(html.contains("data-path=\"upset\""));
        assertTrue(html.contains(">Alternative Matchups</button>"));
        assertTrue(html.contains("<span class=\"badge text-bg-secondary\">Alternative Matchup</span>"));
        assertFalse(html.contains(">Upset</button>"));
        assertFalse(html.contains(">Upset</span>"));
        assertTrue(html.contains("&lt;0.1%"));
        assertTrue(html.contains("Not observed"));
        assertFalse(html.contains(">0.0%"));
        assertFalse(html.contains(">Alt</span>"));
        assertFalse(html.contains("Odds to Reach QF"));
        assertFalse(html.contains("Net Winnings"));
        assertTrue(html.indexOf("Match Likelihood</th>") < html.indexOf("<th>Path</th>"));
    }


    void userOverrideIsTheOnlyWinnerShownInMatchTable() {
        HtmlReporter reporter = new HtmlReporter()
                .withSimulationAdvance(Map.of("Germany", "60.0", "France", "40.0"));
        reporter.printMatchups("Last 16", List.of(
                "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,model_prediction,selection_source",
                "M89,Germany,France,predicted,France (45%),1900,0,1880,0,0,0,,,Germany (55%),model"
        ), new EloCalculator(), null, Map.of(), Map.of());

        String html = reporter.getHtml();
        assertTrue(html.contains("France"));
        assertTrue(html.contains("User Pick"));
        assertFalse(html.contains("Model: "));
        assertFalse(html.contains("Simulation: "));
    }

    @Test
    void tournamentPathLinksToEarlierUpsetMatch() {
        HtmlReporter reporter = new HtmlReporter()
                .withPathNavigation("world_cup_2026", "last_16_match");
        reporter.printMatchups("Last 16", List.of(
                "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,model_prediction,selection_source",
                "M91,Japan,Senegal,upset,Senegal (52%),1906,0,1867,0,59,123,G|Netherlands:-2 > U@M76|Brazil:-5,G|France:-7 > Ecuador:-6,Senegal (52%),model"
        ), new EloCalculator(), null, Map.of(), Map.of(
                "Japan", breakdown(1906), "Senegal", breakdown(1867)));

        String html = reporter.getHtml();
        assertTrue(html.contains("Brazil (Upset)</a>"));
        assertTrue(html.contains("/view/path-game?tournament=world_cup_2026&amp;round=last_16_match&amp;team=Japan&amp;opponent=Brazil&amp;match=M76"));
        assertTrue(html.contains("data-match=\"M91\""));
        assertTrue(html.contains("focusMatch"));
        assertTrue(html.contains("path-focus-row"));
    }

    @Test
    void serverPagedMatchupsPreserveActiveFiltersAndToggleSnapshotTiles() {
        HtmlReporter reporter = new HtmlReporter()
                .withServerPagination("/view/last_8_match?tournament=world_cup_2022&path=alt&team=Portugal", 2, 3, 50)
                .withActualMode(true)
                .withActiveFilters("upset", "Portugal")
                .withTeamNames(List.of("Argentina", "Portugal"));
        reporter.printMatchups("Quarter Finals", List.of(
                "match_id,team1,team2,path,prediction,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,model_prediction,selection_source,matchup_pct,matchup_runs",
                "M1,Portugal,Spain,upset,Portugal (55%),0,0,,,Portugal (55%),simulation,42.0,1000"
        ), new EloCalculator(), null, Map.of(), Map.of(
                "Portugal", breakdown(1800),
                "Spain", breakdown(1750)));

        String html = reporter.getHtml();
        assertTrue(html.contains("path-btn active\" data-path=\"alt\""));
        assertTrue(html.contains("<option value=\"Portugal\" selected>Portugal</option>"));
        assertTrue(html.contains("href=\"/view/last_8_match?tournament=world_cup_2022&path=alt&team=Portugal&page=1\""));
        assertTrue(html.contains("href=\"/view/last_8_match?tournament=world_cup_2022&path=alt&team=Portugal&page=3\""));
        assertTrue(html.contains("const next=active?'':team;"));
        assertTrue(html.contains("navigateWithFilters(section,{team:next,page:1});"));
        assertTrue(html.contains("url.searchParams.set('results',actualValue?'true':'false')"));
        assertTrue(html.contains("data-path=\"all\" data-actual=\"true\""));
    }



    @Test
    void predictedRowRendersWrongLabelWhenActualWinnerDiffers() {
        HtmlReporter reporter = new HtmlReporter()
                .withActualMode(true)
                .withActualResultLabels(Map.of("France|Spain", "Spain"));

        reporter.printMatchups("Last 32", List.of(
                "match_id,team1,team2,path,prediction",
                "M1,France,Spain,predicted,France (55%)"
        ), new EloCalculator(), null, Map.of(), Map.of(
                "France", breakdown(1900),
                "Spain", breakdown(1850)));

        assertTrue(reporter.getHtml().contains("<span class=\"badge text-bg-danger\">Predicted Matchup</span>"));
        assertFalse(reporter.getHtml().contains("Wrong Prediction"));
    }

    @Test
    void predictedRowStaysPredictedWhenActualWinnerMatches() {
        HtmlReporter reporter = new HtmlReporter()
                .withActualMode(true)
                .withActualResultLabels(Map.of("France|Spain", "France"));

        reporter.printMatchups("Last 32", List.of(
                "match_id,team1,team2,path,prediction",
                "M1,France,Spain,predicted,France (55%)"
        ), new EloCalculator(), null, Map.of(), Map.of(
                "France", breakdown(1900),
                "Spain", breakdown(1850)));

        assertTrue(reporter.getHtml().contains(">Predicted Matchup</span>"));
    }

    @Test
    void resultUpsetRowRendersResultLabel() {
        HtmlReporter reporter = new HtmlReporter().withActualMode(true);

        reporter.printMatchups("Last 32", List.of(
                "match_id,team1,team2,path,prediction",
                "M1,France,Spain,result_upset,Spain"
        ), new EloCalculator(), null, Map.of(), Map.of(
                "France", breakdown(1900),
                "Spain", breakdown(1850)));

        assertTrue(reporter.getHtml().contains(">Result</span>"));
        assertFalse(reporter.getHtml().contains("Result / Upset"));
    }


    @Test
    void alternativeMatchupDoesNotShowActualResultPanelInResultsMode() {
        HtmlReporter reporter = new HtmlReporter()
                .withActualMode(true)
                .withActualResultLabels(Map.of("Argentina|Tunisia", "Argentina"));

        reporter.printMatchups("Last 16", List.of(
                "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus",
                "M50,Argentina,Tunisia,upset,Argentina (84%),2100,0,1800,0"
        ), new EloCalculator(), null, Map.of(), Map.of(
                "Argentina", breakdown(2100),
                "Tunisia", breakdown(1800)));

        String html = reporter.getHtml();
        assertTrue(html.contains("Alternative Matchup"));
        assertTrue(html.contains("Most likely score"));
        assertFalse(html.contains("Score unavailable"));
        assertFalse(html.contains("Played result from the tournament."));
    }

    @Test
    void resultRowShowsActualWinnerAndScoreInWinnerResultColumnAndDetails() {
        HtmlReporter reporter = new HtmlReporter()
                .withActualMode(true)
                .withSimulationAdvance(Map.of("Croatia", "52.0", "Brazil", "48.0"))
                .withActualResultLabels(Map.of("Brazil|Croatia", "Croatia"));

        reporter.printMatchups("Quarter Finals", List.of(
                "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,home_score,away_score",
                "M58,Croatia,Brazil,results,Prediction Placeholder,1880,0,1870,0,1,1"
        ), new EloCalculator(), null, Map.of(), Map.of(
                "Croatia", breakdown(1880),
                "Brazil", breakdown(1870)));

        String html = reporter.getHtml();
        assertTrue(html.contains("<th>Winner / Result</th>"));
        assertTrue(html.contains("Croatia (1-1)</span>"));
        assertTrue(html.contains(">Result</span>"));
        assertTrue(html.contains("Actual result"));
        assertTrue(html.contains("Winner: <span class=\"fi fi-hr\"></span> Croatia"));
        assertFalse(html.contains("Prediction Placeholder"));
    }

    @Test
    void predictedMatchupBadgeTurnsGreenOrRedWhenOutcomeKnown() {
        HtmlReporter reporter = new HtmlReporter()
                .withActualMode(true)
                .withActualResultLabels(Map.of(
                        "France|Spain", "France",
                        "Brazil|Germany", "Germany"));

        reporter.printMatchups("Last 16", List.of(
                "match_id,team1,team2,path,prediction",
                "M1,France,Spain,predicted,France (55%)",
                "M2,Brazil,Germany,predicted,Brazil (52%)"
        ), new EloCalculator(), null, Map.of(), Map.of(
                "France", breakdown(1900),
                "Spain", breakdown(1850),
                "Brazil", breakdown(1880),
                "Germany", breakdown(1870)));

        String html = reporter.getHtml();
        assertTrue(html.contains("class=\"table-success\" data-path=\"predicted\" data-match=\"M1\""));
        assertTrue(html.contains("class=\"table-danger\" data-path=\"predicted\" data-match=\"M2\""));
        assertTrue(html.contains("<span class=\"badge text-bg-success\">Predicted Matchup</span>"));
        assertTrue(html.contains("<span class=\"badge text-bg-danger\">Predicted Matchup</span>"));
        assertFalse(html.contains("Wrong Prediction"));
    }

    @Test
    void fixtureRowRendersResultsLabelWhenResultExists() {
        HtmlReporter reporter = new HtmlReporter()
                .withActualMode(true)
                .withActualResultLabels(Map.of("Brazil|Germany", "Germany"));

        reporter.printMatchups("Last 16", List.of(
                "match_id,team1,team2,path,prediction",
                "M2,Brazil,Germany,fixture,Brazil (52%)"
        ), new EloCalculator(), null, Map.of(), Map.of(
                "Brazil", breakdown(1880),
                "Germany", breakdown(1870)));

        assertTrue(reporter.getHtml().contains(">Result</span>"));
        assertFalse(reporter.getHtml().contains(">Fixture / Results</span>"));
    }

    @Test
    void resultRowCanUseActualScoreLookupWhenCsvDoesNotCarryScores() {
        HtmlReporter reporter = new HtmlReporter()
                .withActualMode(true)
                .withSimulationAdvance(Map.of("Croatia", "52.0", "Brazil", "48.0"))
                .withActualResultLabels(Map.of("Brazil|Croatia", "Croatia"))
                .withActualResultScores(Map.of("Brazil|Croatia", "1 - 1"));

        reporter.printMatchups("Quarter Finals", List.of(
                "match_id,team1,team2,path,prediction",
                "M58,Croatia,Brazil,results,Brazil (61%)"
        ), new EloCalculator(), null, Map.of(), Map.of(
                "Croatia", breakdown(1880),
                "Brazil", breakdown(1870)));

        String html = reporter.getHtml();
        assertTrue(html.contains("Croatia (1-1)</span>"));
        assertTrue(html.contains("<div class=\"display-6 fw-bold lh-1 my-2\">1 - 1</div>"));
        assertFalse(html.contains("Brazil (61%)</span>"));
    }

    @Test
    void predictedMatchupStaysBlueBeforeOutcomeKnown() {
        HtmlReporter reporter = new HtmlReporter();

        reporter.printMatchups("Last 16", List.of(
                "match_id,team1,team2,path,prediction",
                "M1,France,Spain,predicted,France (55%)"
        ), new EloCalculator(), null, Map.of(), Map.of(
                "France", breakdown(1900),
                "Spain", breakdown(1850)));

        String html = reporter.getHtml();
        assertTrue(html.contains("class=\"table-primary\" data-path=\"predicted\" data-match=\"M1\""));
        assertTrue(html.contains("<span class=\"badge text-bg-primary\">Predicted Matchup</span>"));
        assertFalse(html.contains("text-bg-success\">Predicted Matchup"));
        assertFalse(html.contains("text-bg-danger\">Predicted Matchup"));
    }

    @Test
    void resultsFilterButtonUsesFixtureOrResultsWordingForCurrentRows() {
        HtmlReporter fixtureReporter = new HtmlReporter();
        fixtureReporter.printMatchups("Last 16", List.of(
                "match_id,team1,team2,path,prediction",
                "M1,France,Spain,fixture,France (55%)"
        ), new EloCalculator(), null, Map.of(), Map.of(
                "France", breakdown(1900),
                "Spain", breakdown(1850)));

        String fixtureHtml = fixtureReporter.getHtml();
        assertTrue(fixtureHtml.contains(">Fixtures</button>"));
        assertFalse(fixtureHtml.contains(">Fixtures / Results</button>"));

        HtmlReporter resultReporter = new HtmlReporter().withActualMode(true);
        resultReporter.printMatchups("Last 16", List.of(
                "match_id,team1,team2,path,prediction",
                "M1,France,Spain,results,France"
        ), new EloCalculator(), null, Map.of(), Map.of(
                "France", breakdown(1900),
                "Spain", breakdown(1850)));

        String resultHtml = resultReporter.getHtml();
        assertTrue(resultHtml.contains(">Results</button>"));
        assertFalse(resultHtml.contains(">Fixtures / Results</button>"));
    }


    @Test
    void fixtureResultRowShowsActualScoreInTableAndDetailsWithCertainMatchLikelihood() {
        HtmlReporter reporter = new HtmlReporter()
                .withActualMode(true)
                .withActualResultLabels(Map.of("Netherlands|United States", "Netherlands"));

        reporter.printMatchups("Last 16", List.of(
                "match_id,team1,team2,path,prediction,team1_base_elo,team1_qual_bonus,team2_base_elo,team2_qual_bonus,home_score,away_score",
                "M49,Netherlands,United States,fixture,Netherlands (74%),2040,0,1800,0,3,1"
        ), new EloCalculator(), null, Map.of(), Map.of(
                "Netherlands", breakdown(2040),
                "United States", breakdown(1800)));

        String html = reporter.getHtml();
        assertTrue(html.contains("Netherlands (3-1)</span>"));
        assertTrue(html.contains("<div class=\"display-6 fw-bold lh-1 my-2\">3 - 1</div>"));
        assertTrue(html.contains("100.0%"));
        assertFalse(html.contains("Netherlands (74%)</span>"));
    }

    @Test
    void predictedMatchupTurnsRedWhenActualFixtureForMatchIdWasDifferent() {
        HtmlReporter reporter = new HtmlReporter()
                .withActualMode(true)
                .withActualResultLabels(Map.of("Netherlands|United States", "Netherlands"));

        reporter.printMatchups("Last 16", List.of(
                "match_id,team1,team2,path,prediction",
                "M49,Netherlands,Iran,predicted,Netherlands (74%)",
                "M49,Netherlands,United States,fixture,Netherlands"
        ), new EloCalculator(), null, Map.of(), Map.of(
                "Netherlands", breakdown(2040),
                "Iran", breakdown(1750),
                "United States", breakdown(1800)));

        String html = reporter.getHtml();
        assertTrue(html.contains("class=\"table-danger\" data-path=\"predicted\" data-match=\"M49\""));
        assertTrue(html.contains("<span class=\"badge text-bg-danger\">Predicted Matchup</span>"));
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
