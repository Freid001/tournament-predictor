package com.tournamentpredictor.model.common;

import com.tournamentpredictor.services.calculation.EloBreakdown;
import com.tournamentpredictor.services.report.HtmlReporter;

import java.util.*;

import static com.tournamentpredictor.model.common.WebViewText.*;

public record CsvData(List<String> headers, List<Map<String, String>> rows) { }
