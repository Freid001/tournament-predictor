package com.tournamentpredictor.model.common;

public final class WebViewText {
    private WebViewText() {
    }

    public static String extractTeamName(String value) {
        if (value == null) return "";
        int open = value.lastIndexOf('(');
        int close = open >= 0 ? value.indexOf(')', open) : -1;
        if (open >= 0 && close > open) {
            return value.substring(open + 1, close).trim();
        }
        return value;
    }

    public static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String extractWinnerName(String value) {
        String trimmed = value == null ? "" : value.trim();
        int percentStart = trimmed.lastIndexOf(" (");
        if (percentStart > 0 && trimmed.endsWith(")")) {
            return trimmed.substring(0, percentStart).trim();
        }
        return extractTeamName(trimmed);
    }
}
