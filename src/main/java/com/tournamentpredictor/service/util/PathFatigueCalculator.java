package com.tournamentpredictor.service.util;

import com.tournamentpredictor.config.PredictionConfig;

/**
 * Calculates tournament path fatigue as a signal for knockout round predictions.
 *
 * Research shows teams that face tougher opponents in earlier knockout rounds tend to
 * underperform their base ELO in later rounds due to accumulated physical and psychological
 * fatigue. This signal is separate from base ELO (historical strength) — it captures how
 * depleted a team is based on the quality of opponents already beaten in this tournament.
 *
 * <h3>Formula</h3>
 * For each prior opponent:
 * <pre>
 *   rawScore  = opponentElo - tournamentAvgElo
 *   weighted  = rawScore x stageMult(round beaten) x upsetMult(if applicable)
 * </pre>
 * Accumulated across all prior rounds:
 * <pre>
 *   fatigueElo = (cumulativeTotal / 100) x -eloFactor
 * </pre>
 * Positive results (easy path) are capped at 0 -- an easy bracket is not a rest bonus;
 * it simply means no accumulated fatigue burden.
 *
 * <h3>Stage multipliers</h3>
 * Later rounds count more because teams have played more games by then and rotation
 * becomes harder:
 * <ul>
 *   <li>group   x 0.25 -- group-stage load carried into the first knockout match</li>
 *   <li>last_32 x 0.5 -- early round, squad still fresh, bench minutes available</li>
 *   <li>last_16 x 1.0 -- baseline, first team heavily involved</li>
 *   <li>last_8  x 1.2 -- quarter-final pressure, legs heavier</li>
 *   <li>last_4  x 1.5 -- semi-final, virtually no rotation, fatigue at peak</li>
 * </ul>
 *
 * <h3>Bench-depth multiplier</h3>
 * Excellent squads absorb hard paths best; good/deep squads absorb them better; limited and thin squads absorb them worse.
 * This multiplier only changes negative fatigue penalties. It never creates a bonus on an easy path.
 *
 * All values are configurable via application.properties under the {@code path.fatigue.*} prefix.
 */
public class PathFatigueCalculator {

    private int    tournamentAvgElo       = 1850;
    private int    eloFactor              = 12;
    private double stageMultGroup         = 0.25;
    private double stageMultLast32        = 0.5;
    private double stageMultLast16        = 1.0;
    private double stageMultLast8         = 1.2;
    private double stageMultLast4         = 1.5;
    private double upsetMultiplier         = 1.25;
    private double depthExcellentMultiplier = 0.70;
    private double depthGoodMultiplier    = 0.85;
    private double depthLimitedMultiplier = 1.15;
    private double depthThinMultiplier    = 1.30;
    /** Wire all path fatigue constants from application.properties via {@link PredictionConfig}. */
    public PathFatigueCalculator withConfig(PredictionConfig config) {
        this.tournamentAvgElo       = config.getPathFatigueTournamentAvgElo();
        this.eloFactor              = config.getPathFatigueEloFactor();
        this.stageMultGroup         = config.getPathFatigueStageMultGroup();
        this.stageMultLast32        = config.getPathFatigueStageMultLast32();
        this.stageMultLast16        = config.getPathFatigueStageMultLast16();
        this.stageMultLast8         = config.getPathFatigueStageMultLast8();
        this.stageMultLast4         = config.getPathFatigueStageMultLast4();
        this.upsetMultiplier         = config.getPathFatigueUpsetMultiplier();
        this.depthExcellentMultiplier = config.getPathFatigueDepthExcellentMultiplier();
        this.depthGoodMultiplier    = config.getPathFatigueDepthGoodMultiplier();
        this.depthLimitedMultiplier = config.getPathFatigueDepthLimitedMultiplier();
        this.depthThinMultiplier    = config.getPathFatigueDepthThinMultiplier();
        return this;
    }

    /** Raw difficulty of a single opponent: how far above/below the tournament average they are. */
    public int rawScore(int opponentElo) {
        return opponentElo - tournamentAvgElo;
    }

    /** Stage multiplier for the round in which an opponent was beaten. */
    public double stageMultiplierForRound(String stageBeat) {
        return switch (stageBeat.toLowerCase()) {
            case "group" -> stageMultGroup;
            case "last_32" -> stageMultLast32;
            case "last_16" -> stageMultLast16;
            case "last_8"  -> stageMultLast8;
            case "last_4"  -> stageMultLast4;
            default -> throw new IllegalArgumentException("Unknown stage: " + stageBeat);
        };
    }

    /**
     * Group-stage contribution is one-way: hard group opponents add load, but weaker
     * group opponents do not create a rest bonus before the knockouts.
     */
    public int groupStageWeightedContribution(int opponentElo) {
        int raw = Math.max(0, rawScore(opponentElo));
        return (int) Math.round(raw * stageMultGroup);
    }

    /**
     * Knockout contribution is also one-way: a weak opponent can add zero load,
     * but cannot erase fatigue already accumulated earlier in the tournament.
     */
    public int knockoutWeightedContribution(int opponentElo, String stageBeat) {
        return knockoutWeightedContribution(opponentElo, stageBeat, false);
    }

    /** Upset wins amplify the normal opponent-strength load; they do not add a flat penalty. */
    public int knockoutWeightedContribution(int opponentElo, String stageBeat, boolean upsetWin) {
        int raw = Math.max(0, rawScore(opponentElo));
        double multiplier = stageMultiplierForRound(stageBeat) * (upsetWin ? upsetMultiplier : 1.0);
        return (int) Math.round(raw * multiplier);
    }

    /**
     * Convert a cumulative pre-weighted path total to an ELO delta.
     * Capped at 0: an easy path reduces fatigue to zero, but cannot grant a positive bonus.
     */
    public int eloAdjustmentFromWeighted(int weightedTotal) {
        int raw = (int) Math.round((weightedTotal / 100.0) * -eloFactor);
        return Math.min(raw, 0);
    }

    /**
     * Apply bench-depth sensitivity to a negative fatigue ELO value.
     * Level -1 = excellent, 0 = good/deep, 1 = limited, 2 = thin. Easy paths remain 0, never a bonus.
     */
    public int applyDepthMultiplier(int fatigueElo, int depthLevel) {
        if (fatigueElo >= 0) return fatigueElo;
        double multiplier = switch (depthLevel) {
            case -1 -> depthExcellentMultiplier;
            case 0 -> depthGoodMultiplier;
            case 1 -> depthLimitedMultiplier;
            case 2 -> depthThinMultiplier;
            default -> 1.0;
        };
        return (int) Math.round(fatigueElo * multiplier);
    }

    /** Returns the tournament average ELO baseline used for fatigue raw score calculation. */
    public int getTournamentAvgElo() { return tournamentAvgElo; }

    /** Difficulty label for cumulative weighted totals (sum across all rounds beaten so far).
     * Thresholds scaled relative to a single last_16 game against an average-strength opponent.
     */
    public String label(int weightedTotal) {
        if (weightedTotal < -200) return "Very Easy";
        if (weightedTotal <  -60) return "Easy";
        if (weightedTotal <=  60) return "Medium";
        if (weightedTotal <= 200) return "Hard";
        return "Very Hard";
    }
}
