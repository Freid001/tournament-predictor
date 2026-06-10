package com.tournamentpredictor.services.web;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class WebNavigationService {
    public static final List<String> VIEW_ROUND_SEQUENCE = List.of(
            "start", "groups", "last_32_match", "last_16_match", "last_8_match", "last_4_match", "final_match"
    );

    private WebNavigationService() {
    }

    public static String nextViewRound(String round) {
        int idx = VIEW_ROUND_SEQUENCE.indexOf(round);
        return (idx >= 0 && idx + 1 < VIEW_ROUND_SEQUENCE.size()) ? VIEW_ROUND_SEQUENCE.get(idx + 1) : null;
    }

    public static String prevViewRound(String round) {
        int idx = VIEW_ROUND_SEQUENCE.indexOf(round);
        return (idx > 0) ? VIEW_ROUND_SEQUENCE.get(idx - 1) : null;
    }

    public static String editPrevViewRound(String round) {
        return switch (round) {
            case "groups" -> "start";
            case "last_32" -> "groups";
            case "last_16" -> "last_32_match";
            case "last_8" -> "last_16_match";
            case "last_4" -> "last_8_match";
            case "final" -> "last_4_match";
            default -> null;
        };
    }

    public static String viewRoundForEdit(String round) {
        return switch (round) {
            case "groups" -> "groups";
            case "last_32" -> "last_32_match";
            case "last_16" -> "last_16_match";
            case "last_8" -> "last_8_match";
            case "last_4" -> "last_4_match";
            case "final" -> "final_match";
            default -> null;
        };
    }

    public static String nextRunPrereqForView(String round) {
        return switch (round) {
            case "groups" -> "last_32.csv";
            case "last_32_match" -> "last_16.csv";
            case "last_16_match" -> "last_8.csv";
            case "last_8_match" -> "last_4.csv";
            case "last_4_match" -> "final.csv";
            default -> null;
        };
    }

    public static String nextRunModeForView(String round) {
        return switch (round) {
            case "groups" -> "tournament";
            default -> null;
        };
    }

    public static String displayMode(String mode) {
        return switch (mode) {
            case "snapshot-refresh" -> "Pre-Tournament Snapshot";
            case "start" -> "Team Setup";
            case "groups_match" -> "Group Rankings";
            case "groups" -> "Group Picks";
            case "group-simulation" -> "Group Stage";
            case "last_32", "last_32_match" -> "Last 32";
            case "last_16", "last_16_match" -> "Last 16";
            case "last_8", "last_8_match" -> "Quarter Finals";
            case "last_4", "last_4_match" -> "Semi Finals";
            case "final", "final_match" -> "Final";
            case "simulate", "simulation" -> "Monte Carlo";
            case "tournament-snapshot-refresh" -> "Tournament Results";
            case "tournament" -> "Tournament";
            default -> mode;
        };
    }

    public static String displayTournament(String name) {
        if (name == null || name.isEmpty()) return name;
        return Arrays.stream(name.split("_"))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    public static String oddsColumnForRound(String round) {
        return switch (round) {
            case "last_32_match" -> "last_16";
            case "last_16_match" -> "last_8";
            case "last_8_match" -> "last_4";
            case "last_4_match" -> "final";
            case "final_match" -> "final";
            default -> null;
        };
    }

    public static String displayViewMode(String round) {
        if ("groups".equals(round) || "groups_match".equals(round)) {
            return "Group Rankings";
        }
        if ("last_32_match".equals(round)) {
            return "Last 32";
        }
        return displayMode(round);
    }
}
