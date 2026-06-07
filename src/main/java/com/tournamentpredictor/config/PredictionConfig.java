package com.tournamentpredictor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PredictionConfig {

    @Value("${simulation.runs:25000}")
    private int simulationRuns;

    @Value("${simulation.seed:20260605}")
    private long simulationSeed;

    @Value("${xg.total.multiplier:1.0}")
    private double expectedGoalsMultiplier;

    @Value("${elo.scale.divisor:400.0}")
    private double eloScaleDivisor;

    @Value("${xg.goal.diff.per.400.elo:1.0}")
    private double goalDiffPer400Elo;

    @Value("${qual.form.since.year:2023}")
    private int qualFormSinceYear;

    @Value("${qual.form.until.year:2026}")
    private int qualFormUntilYear;

    @Value("${qual.form.elo.max:0}")
    private int qualFormEloMax;

    @Value("${pre.tournament.form.elo.max:50}")
    private int preTournamentFormEloMax;

    @Value("${pre.tournament.form.max.games:3}")
    private int preTournamentFormMaxGames;

    @Value("${pre.tournament.form.since.year:2026}")
    private int preTournamentFormSinceYear;

    @Value("${pre.tournament.form.until.year:2026}")
    private int preTournamentFormUntilYear;

    @Value("${group.home.advantage.elo:100}")
    private int homeAdvantageElo;

    @Value("${group.injury.penalty.minor:22}")
    private int injuryPenaltyMinor;

    @Value("${group.injury.penalty.significant:45}")
    private int injuryPenaltySignificant;

    @Value("${group.injury.penalty.critical:90}")
    private int injuryPenaltyCritical;

    @Value("${group.heat.advantage.mild:9}")
    private int heatAdvanttageMild;

    @Value("${group.heat.advantage.moderate:18}")
    private int heatAdvanttageModerate;

    @Value("${group.heat.advantage.strong:35}")
    private int heatAdvanttageStrong;

    @Value("${group.squad.dropout.penalty.minor:18}")
    private int squadDropoutPenaltyMinor;

    @Value("${group.squad.dropout.penalty.significant:35}")
    private int squadDropoutPenaltySignificant;

    @Value("${group.squad.dropout.penalty.critical:70}")
    private int squadDropoutPenaltyCritical;

    @Value("${group.confidence.gap:25}")
    private int confidenceGap;

    @Value("${squad.age.young.elo:10}")
    private int squadAgeYoungPenalty;

    @Value("${squad.age.aging.elo:8}")
    private int squadAgeAgingPenalty;

    @Value("${squad.cohesion.unsettled.elo:11}")
    private int squadCohesionUnsettledPenalty;

    @Value("${squad.cohesion.disrupted.elo:22}")
    private int squadCohesionDisruptedPenalty;

    @Value("${squad.cohesion.fractured.elo:45}")
    private int squadCohesionFracturedPenalty;

    @Value("${squad.depth.excellent.elo:10}")
    private int squadDepthExcellentBonus;

    @Value("${squad.depth.limited.elo:10}")
    private int squadDepthLimitedPenalty;

    @Value("${squad.depth.thin.elo:20}")
    private int squadDepthThinPenalty;

    @Value("${betting.strong.candidate.min.profit:20.0}")
    private double bettingStrongCandidateMinProfit;

    @Value("${betting.candidate.min.profit:10.0}")
    private double bettingCandidateMinProfit;

    @Value("${betting.weak.candidate.min.profit:5.0}")
    private double bettingWeakCandidateMinProfit;

    @Value("${betting.candidate.min.pct:40}")
    private int bettingCandidateMinPct;

    @Value("${betting.risky.min.pct:30}")
    private int bettingRiskyMinPct;

    @Value("${betting.moonshot.min.profit:20.0}")
    private double bettingMoonshotMinProfit;

    @Value("${path.fatigue.tournament.avg.elo:1850}")
    private int pathFatigueTournamentAvgElo;

    @Value("${path.fatigue.elo.factor:12}")
    private int pathFatigueEloFactor;

    @Value("${path.fatigue.stage.group.multiplier:0.25}")
    private double pathFatigueStageMultGroup;

    @Value("${path.fatigue.stage.last32.multiplier:0.5}")
    private double pathFatigueStageMultLast32;

    @Value("${path.fatigue.stage.last16.multiplier:1.0}")
    private double pathFatigueStageMultLast16;

    @Value("${path.fatigue.stage.last8.multiplier:1.2}")
    private double pathFatigueStageMultLast8;

    @Value("${path.fatigue.stage.last4.multiplier:1.5}")
    private double pathFatigueStageMultLast4;

    @Value("${path.fatigue.upset.multiplier:1.25}")
    private double pathFatigueUpsetMultiplier;

    @Value("${path.fatigue.depth.excellent.multiplier:0.70}")
    private double pathFatigueDepthExcellentMultiplier;

    @Value("${path.fatigue.depth.good.multiplier:0.85}")
    private double pathFatigueDepthGoodMultiplier;

    @Value("${path.fatigue.depth.limited.multiplier:1.15}")
    private double pathFatigueDepthLimitedMultiplier;

    @Value("${path.fatigue.depth.thin.multiplier:1.30}")
    private double pathFatigueDepthThinMultiplier;

    public int getSimulationRuns() { return simulationRuns; }
    public long getSimulationSeed() { return simulationSeed; }
    public double getExpectedGoalsMultiplier() { return expectedGoalsMultiplier; }
    public double getEloScaleDivisor() { return eloScaleDivisor; }
    public double getGoalDiffPer400Elo() { return goalDiffPer400Elo; }
    public int getQualFormSinceYear() { return qualFormSinceYear; }
    public int getQualFormUntilYear() { return qualFormUntilYear; }
    public int getQualFormEloMax() { return qualFormEloMax; }
    public int getPreTournamentFormEloMax() { return preTournamentFormEloMax; }
    public int getPreTournamentFormMaxGames() { return preTournamentFormMaxGames; }
    public int getPreTournamentFormSinceYear() { return preTournamentFormSinceYear; }
    public int getPreTournamentFormUntilYear() { return preTournamentFormUntilYear; }
    public int getHomeAdvantageElo() { return homeAdvantageElo; }
    public int[] getInjuryPenalties() { return new int[]{0, injuryPenaltyMinor, injuryPenaltySignificant, injuryPenaltyCritical}; }
    public int[] getHeatAdvantages() { return new int[]{0, heatAdvanttageMild, heatAdvanttageModerate, heatAdvanttageStrong}; }
    public int[] getSquadDropoutPenalties() { return new int[]{0, squadDropoutPenaltyMinor, squadDropoutPenaltySignificant, squadDropoutPenaltyCritical}; }
    public int getConfidenceGap() { return confidenceGap; }
    public int getSquadAgeYoungPenalty() { return squadAgeYoungPenalty; }
    public int getSquadAgeAgingPenalty() { return squadAgeAgingPenalty; }
    public int getSquadCohesionUnsettledPenalty() { return squadCohesionUnsettledPenalty; }
    public int getSquadCohesionDisruptedPenalty() { return squadCohesionDisruptedPenalty; }
    public int getSquadCohesionFracturedPenalty() { return squadCohesionFracturedPenalty; }
    public int getSquadDepthExcellentBonus() { return squadDepthExcellentBonus; }
    public int getSquadDepthLimitedPenalty() { return squadDepthLimitedPenalty; }
    public int getSquadDepthThinPenalty() { return squadDepthThinPenalty; }
    public double getBettingStrongCandidateMinProfit() { return bettingStrongCandidateMinProfit; }
    public double getBettingCandidateMinProfit() { return bettingCandidateMinProfit; }
    public double getBettingWeakCandidateMinProfit() { return bettingWeakCandidateMinProfit; }
    public int getBettingCandidateMinPct() { return bettingCandidateMinPct; }
    public int getBettingRiskyMinPct() { return bettingRiskyMinPct; }
    public double getBettingMoonshotMinProfit() { return bettingMoonshotMinProfit; }

    public int getPathFatigueTournamentAvgElo() { return pathFatigueTournamentAvgElo; }
    public int getPathFatigueEloFactor() { return pathFatigueEloFactor; }
    public double getPathFatigueStageMultGroup() { return pathFatigueStageMultGroup; }
    public double getPathFatigueStageMultLast32() { return pathFatigueStageMultLast32; }
    public double getPathFatigueStageMultLast16() { return pathFatigueStageMultLast16; }
    public double getPathFatigueStageMultLast8() { return pathFatigueStageMultLast8; }
    public double getPathFatigueStageMultLast4() { return pathFatigueStageMultLast4; }
    public double getPathFatigueUpsetMultiplier() { return pathFatigueUpsetMultiplier; }
    public double getPathFatigueDepthExcellentMultiplier() { return pathFatigueDepthExcellentMultiplier; }
    public double getPathFatigueDepthGoodMultiplier() { return pathFatigueDepthGoodMultiplier; }
    public double getPathFatigueDepthLimitedMultiplier() { return pathFatigueDepthLimitedMultiplier; }
    public double getPathFatigueDepthThinMultiplier() { return pathFatigueDepthThinMultiplier; }
}
