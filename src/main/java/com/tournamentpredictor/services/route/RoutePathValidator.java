package com.tournamentpredictor.services.route;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RoutePathValidator {
    private RoutePathValidator() {
    }

    public static boolean hasRepeatedPathTeam(String path) {
        Set<String> seen = new LinkedHashSet<>();
        for (String team : pathTeams(path)) {
            if (!team.isBlank() && !seen.add(team)) {
                return true;
            }
        }
        return false;
    }

    public static boolean pathContainsTeam(String path, String team) {
        String target = canonical(team);
        if (target.isBlank()) {
            return false;
        }
        for (String pathTeam : pathTeams(path)) {
            if (target.equals(pathTeam)) {
                return true;
            }
        }
        return false;
    }

    public static boolean invalidMatchupRoute(String team1, String team2, String team1OpponentPath, String team2OpponentPath) {
        return hasRepeatedPathTeam(team1OpponentPath)
                || hasRepeatedPathTeam(team2OpponentPath)
                || pathContainsTeam(team1OpponentPath, team2)
                || pathContainsTeam(team2OpponentPath, team1);
    }

    private static List<String> pathTeams(String path) {
        List<String> teams = new ArrayList<>();
        for (String segment : trim(path).split(" > ", -1)) {
            String team = teamFromSegment(segment);
            if (!team.isBlank()) {
                teams.add(team);
            }
        }
        return teams;
    }

    private static String teamFromSegment(String segment) {
        String step = trim(segment);
        if (step.isBlank()) {
            return "";
        }
        int historySeedDelimiter = step.indexOf(':');
        if (historySeedDelimiter >= 0 && step.indexOf('|') > historySeedDelimiter) {
            step = step.substring(historySeedDelimiter + 1);
        }
        String[] parts = step.split("\\|", -1);
        String team = parts.length >= 2 ? parts[1] : step;
        int scoreDelimiter = team.lastIndexOf(':');
        if (scoreDelimiter >= 0) {
            team = team.substring(0, scoreDelimiter);
        }
        return canonical(team);
    }

    private static String canonical(String value) {
        return trim(value).toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
