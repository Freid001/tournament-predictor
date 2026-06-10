package com.tournamentpredictor.services.calculation;

// Attack/Defence are carried with the ELO snapshot for simulation input, but are excluded from adjustedElo.
public record TeamEloSnapshot(int baseElo, int qualBonus, int adjustedElo, int squadDepthLevel,
                              int attackQuality, int defenceQuality) {
    public TeamEloSnapshot(int baseElo, int qualBonus, int adjustedElo) {
        this(baseElo, qualBonus, adjustedElo, 0, 0, 0);
    }

    public TeamEloSnapshot(int baseElo, int qualBonus, int adjustedElo, int squadDepthLevel) {
        this(baseElo, qualBonus, adjustedElo, squadDepthLevel, 0, 0);
    }
}
