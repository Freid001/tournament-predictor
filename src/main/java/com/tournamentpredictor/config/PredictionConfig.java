package com.tournamentpredictor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PredictionConfig {

    // Combined prediction formula:
    //   finalPct = eloWeight * ELO + qualFormWeight * QualificationForm
    //   Hosts with no qual data: finalPct = 100% ELO

    @Value("${prediction.elo.weight:0.85}")
    private double eloWeight;

    @Value("${prediction.qual.form.weight:0.15}")
    private double qualFormWeight;

    @Value("${elo.scale.divisor:400.0}")
    private double eloScaleDivisor;

    @Value("${h2h.decay.half.life.years:5.0}")
    private double halfLifeYears;

    @Value("${group.home.advantage.elo:100}")
    private int homeAdvantageElo;

    @Value("${group.injury.penalty.minor:25}")
    private int injuryPenaltyMinor;

    @Value("${group.injury.penalty.significant:50}")
    private int injuryPenaltySignificant;

    @Value("${group.injury.penalty.critical:100}")
    private int injuryPenaltyCritical;

    @Value("${group.heat.advantage.mild:8}")
    private int heatAdvanttageMild;

    @Value("${group.heat.advantage.moderate:15}")
    private int heatAdvanttageModerate;

    @Value("${group.heat.advantage.strong:25}")
    private int heatAdvanttageStrong;

    @Value("${group.squad.dropout.penalty.minor:20}")
    private int squadDropoutPenaltyMinor;

    @Value("${group.squad.dropout.penalty.significant:40}")
    private int squadDropoutPenaltySignificant;

    @Value("${group.squad.dropout.penalty.critical:75}")
    private int squadDropoutPenaltyCritical;

    @Value("${group.confidence.gap:25}")
    private int confidenceGap;

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

    public double getEloWeight() { return eloWeight; }
    public double getQualFormWeight() { return qualFormWeight; }
    public double getEloScaleDivisor() { return eloScaleDivisor; }
    public double getHalfLifeYears() { return halfLifeYears; }
    public int getHomeAdvantageElo() { return homeAdvantageElo; }
    public int[] getInjuryPenalties() { return new int[]{0, injuryPenaltyMinor, injuryPenaltySignificant, injuryPenaltyCritical}; }
    public int[] getHeatAdvantages() { return new int[]{0, heatAdvanttageMild, heatAdvanttageModerate, heatAdvanttageStrong}; }
    public int[] getSquadDropoutPenalties() { return new int[]{0, squadDropoutPenaltyMinor, squadDropoutPenaltySignificant, squadDropoutPenaltyCritical}; }
    public int getConfidenceGap() { return confidenceGap; }
    public double getBettingStrongCandidateMinProfit() { return bettingStrongCandidateMinProfit; }
    public double getBettingCandidateMinProfit() { return bettingCandidateMinProfit; }
    public double getBettingWeakCandidateMinProfit() { return bettingWeakCandidateMinProfit; }
    public int getBettingCandidateMinPct() { return bettingCandidateMinPct; }
    public int getBettingRiskyMinPct() { return bettingRiskyMinPct; }
    public double getBettingMoonshotMinProfit() { return bettingMoonshotMinProfit; }
}
