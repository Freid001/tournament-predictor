package com.tournamentpredictor.model.group;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public record GroupStandingRow(String group, String team, int points, int goalDifference, int goalsFor, int goalsAgainst) {}
