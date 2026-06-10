package com.tournamentpredictor.services.bracket;

import com.tournamentpredictor.services.io.CsvLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TokenResolver {
    public String resolveToken(String token, Map<String, String> groups) {
        if (token == null) {
            return "";
        }
        token = token.trim();
        if (token.matches("^[A-L][1-4]$")) {
            return groups.getOrDefault(token, token);
        }
        if (token.matches("^[A-L]+3$")) {
            List<String> parts = new ArrayList<>();
            for (char group : token.toCharArray()) {
                if (group == '3') {
                    break;
                }
                String slot = "" + group + "3";
                String team = groups.getOrDefault(slot, "");
                if (!team.isEmpty()) {
                    parts.add(team);
                }
            }
            return String.join("/", parts);
        }
        return token;
    }

    public String resolveTokenWithBrackets(String token, Map<String, String> groups, List<CsvLoader.BracketEntry> brackets) {
        if (token == null) {
            return "";
        }
        token = token.trim();
        if (token.matches("^W\\d+$")) {
            String matchId = "M" + token.substring(1);
            for (CsvLoader.BracketEntry bracket : brackets) {
                if (matchId.equalsIgnoreCase(bracket.matchId)) {
                    String resolved1 = resolveToken(bracket.token1, groups);
                    String resolved2 = resolveToken(bracket.token2, groups);
                    List<String> parts = new ArrayList<>();
                    if (resolved1 != null && !resolved1.isEmpty()) {
                        parts.add(resolved1);
                    }
                    if (resolved2 != null && !resolved2.isEmpty()) {
                        parts.add(resolved2);
                    }
                    return String.join("/", parts);
                }
            }
            return token;
        }
        return resolveToken(token, groups);
    }

    public boolean isSimpleSlot(String token) {
        return token != null && token.matches("^[A-L][12]");
    }

    public String swapSlot(String token) {
        if (token == null) {
            return null;
        }
        if (token.matches("^[A-L]1$")) {
            return token.charAt(0) + "2";
        }
        if (token.matches("^[A-L]2$")) {
            return token.charAt(0) + "1";
        }
        return token;
    }
}
