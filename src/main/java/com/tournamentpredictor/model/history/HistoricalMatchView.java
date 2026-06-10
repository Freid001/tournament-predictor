package com.tournamentpredictor.model.history;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public record HistoricalMatchView(String team1, String team2, String score, String predictedOutcome,
                                      String actualOutcome, String homeProbability, String drawProbability,
                                      String awayProbability, String expectedGoals, boolean correct) {}
