package com.tournamentpredictor.services.web;

import org.springframework.web.util.HtmlUtils;

public final class WebText {
    private WebText() {
    }

    public static String outcome(double home, double draw, double away) {
        if (home >= draw && home >= away) return "Home";
        if (draw >= home && draw >= away) return "Draw";
        return "Away";
    }

    public static String percent(double probability) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", probability * 100.0);
    }

    public static String csvValue(String value) {
        if (value == null) return "";
        if (!value.contains(",") && !value.contains("\"") && !value.contains("\n") && !value.contains("\r")) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public static String webEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    public static String formatQualBonus(String raw) {
        String value = trim(raw);
        if (value.isBlank()) return "—";
        try {
            int n = Integer.parseInt(value);
            if (n == 0) return "Host";
            return n > 0 ? "+" + n : String.valueOf(n);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    public static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public static String sanitiseNote(String value) {
        return trim(value).replace("\r", " ").replace("\n", " ");
    }

    public static int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(trim(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static String extractTeamName(String value) {
        if (value == null) return "";
        int open = value.lastIndexOf('(');
        int close = value.endsWith(")") ? value.length() - 1 : -1;
        if (open >= 0 && close > open) return value.substring(open + 1, close);
        return value;
    }

    public static String escapeHtml(String input) {
        return HtmlUtils.htmlEscape(input == null ? "" : input);
    }

    public static String extractWinnerName(String value) {
        if (value == null) return "";
        int idx = value.lastIndexOf(" (");
        return idx > 0 ? value.substring(0, idx) : value;
    }
}
