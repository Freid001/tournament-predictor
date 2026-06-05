package com.tournamentpredictor.service.util;

public record TeamEloSnapshot(int baseElo, int qualBonus, int adjustedElo, int squadDepthLevel) {
    public TeamEloSnapshot(int baseElo, int qualBonus, int adjustedElo) {
        this(baseElo, qualBonus, adjustedElo, 0);
    }
}
