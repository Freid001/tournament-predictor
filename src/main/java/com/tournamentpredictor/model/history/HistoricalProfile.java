package com.tournamentpredictor.model.history;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public record HistoricalProfile(int elo, int attack, int defence) {}
