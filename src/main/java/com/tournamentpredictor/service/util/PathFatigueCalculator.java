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
 *   weighted  = rawScore x stageMult(round beaten)
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
 *   <li>last_32 x 0.5 -- early round, squad still fresh, bench minutes available</li>
 *   <li>last_16 x 1.0 -- baseline, first team heavily involved</li>
 *   <li>last_8  x 1.2 -- quarter-final pressure, legs heavier</li>
 *   <li>last_4  x 1.5 -- semi-final, virtually no rotation, fatigue at peak</li>
 * </ul>
 *
 * <h3>Depth multiplier</h3>
 * Thin squads cannot rotate, so path fatigue hits them harder. Applied ONLY to negative
 * fatigue values -- it amplifies an existing burden, never creates one from an easy path.
 * <ul>
 *   <li>Normal (0) -- no multiplier</li>
 *   <li>Limited (1) x 1.15 -- bench quality drops sharply after 13-14 players</li>
 *   <li>Thin    (2) x 1.30 -- minnow-level depth, even 1-2 injuries devastating</li>
 * </ul>
 * This is NOT double-counting with the static squad depth ELO penalty:
 * the depth penalty is a fixed quality gap that always applies; the depth multiplier
 * is a dynamic fatigue sensitivity that only activates on hard paths.
 *
 * All values are configurable via application.properties under the {@code path.fatigue.*} prefix.
 */
public class PathFatigueCalculator {

    private int    tournamentAvgElo       = 1850;
    private int    eloFactor              = 12;
    private double stageMultLast32        = 0.5;
    private double stageMultLast16        = 1.0;
    private double stageMultLast8         = 1.2;
    private double stageMultLast4         = 1.5;
    private double depthLimitedMultiplier = 1.15;
    private double depthThinMultiplier    = 1.30;

    /** Wire all path fatigue constants from application.properties via {@link PredictionConfig}. */
    public PathFatigueCalculator withConfig(PredictionConfig config) {
        this.tournamentAvgElo       = config.getPathFatigueTournamentAvgElo();
        this.eloFactor              = config.getPathFatigueEloFactor();
        this.stageMultLast32        = config.getPathFatigueStageMultLast32();
        this.stageMultLast16        = config.getPathFatigueStageMultLast16();
        this.stageMultLast8         = config.getPathFatigueStageMultLast8();
        this.stageMultLast4         = config.getPathFatigueStageMultLast4();
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
            case "last_32" -> stageMultLast32;
            case "last_16" -> stageMultLast16;
            case "last_8"  -> stageMultLast8;
            case "last_4"  -> stageMultLast4;
            default -> throw new IllegalArgumentException("Unknown stage: " + stageBeat);
        };
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
     * Apply the squad depth multiplier to a negative fatigue ELO value.
     * Has no effect when fatigueElo >= 0 (easy or neutral path).
     *
     * @param fatigueElo  result of {@link #eloAdjustmentFromWeighted} (should be <= 0)
     * @param depthLevel  0 = normal, 1 = limited, 2 = thin
     * @return amplified fatigue penalty (still <= 0)
     */
    public int applyDepthMultiplier(int fatigueElo, int depthLevel) {
        if (fatigueElo >= 0) return fatigueElo;
        double multiplier = switch (depthLevel) {
            case 1  -> depthLimitedMultiplier;
            case 2  -> depthThinMultiplier;
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
