package com.tournamentpredictor.service.util;

import java.util.Locale;
import java.util.Random;

public class ExpectedGoalsCalculator {
    private static final double DEFAULT_BASE_TOTAL_GOALS = 2.60;
    private static final double DEFAULT_GOAL_DIFF_PER_400_ELO = 1.00;
    private static final double MIN_EXPECTED_GOALS = 0.20;
    private static final double MAX_EXPECTED_GOALS = 4.50;
    private static final int MAX_SCORELINE_GOALS = 10;
    // Signed Attack/Defence inputs shape goals only; they are not ELO adjustments.
    private static final double DEFAULT_QUALITY_XG_PER_LEVEL = 0.05;

    private final double eloScaleDivisor;
    private final double baseTotalGoals;
    private final double goalDiffPer400Elo;
    private final double totalMultiplier;
    private final double qualityXgPerLevel;

    public ExpectedGoalsCalculator() {
        this(400.0, DEFAULT_BASE_TOTAL_GOALS, DEFAULT_GOAL_DIFF_PER_400_ELO, 1.0, DEFAULT_QUALITY_XG_PER_LEVEL);
    }

    ExpectedGoalsCalculator(double eloScaleDivisor, double baseTotalGoals, double goalDiffPer400Elo) {
        this(eloScaleDivisor, baseTotalGoals, goalDiffPer400Elo, 1.0, DEFAULT_QUALITY_XG_PER_LEVEL);
    }

    public ExpectedGoalsCalculator(double eloScaleDivisor, double baseTotalGoals,
                                   double goalDiffPer400Elo, double totalMultiplier) {
        this(eloScaleDivisor, baseTotalGoals, goalDiffPer400Elo, totalMultiplier, DEFAULT_QUALITY_XG_PER_LEVEL);
    }

    public ExpectedGoalsCalculator(double eloScaleDivisor, double baseTotalGoals,
                                   double goalDiffPer400Elo, double totalMultiplier,
                                   double qualityXgPerLevel) {
        this.eloScaleDivisor = eloScaleDivisor;
        this.baseTotalGoals = baseTotalGoals;
        this.goalDiffPer400Elo = goalDiffPer400Elo;
        this.totalMultiplier = totalMultiplier;
        this.qualityXgPerLevel = qualityXgPerLevel;
    }

    public Projection project(String team1, String team2, int team1Elo, int team2Elo) {
        return project(team1, team2, team1Elo, team2Elo, 0, 0, 0, 0);
    }

    /**
     * Projects a score distribution from adjusted ELO plus separate current-squad goal profiles.
     * Form belongs in adjusted ELO; Attack/Defence should use separate evidence to avoid double counting.
     */
    public Projection project(String team1, String team2, int team1Elo, int team2Elo,
                              int team1AttackQuality, int team1DefenceQuality,
                              int team2AttackQuality, int team2DefenceQuality) {
        double noDrawTeam1WinProbability = eloWinProbability(team1Elo, team2Elo);
        double eloDiff = team1Elo - team2Elo;
        double expectedGoalDiff = (eloDiff / 400.0) * goalDiffPer400Elo;
        double team1ExpectedGoals = clamp(totalMultiplier * ((baseTotalGoals + expectedGoalDiff) / 2.0
                + qualityXgPerLevel * (team1AttackQuality - team2DefenceQuality)));
        double team2ExpectedGoals = clamp(totalMultiplier * ((baseTotalGoals - expectedGoalDiff) / 2.0
                + qualityXgPerLevel * (team2AttackQuality - team1DefenceQuality)));

        ScoreProbability scoreProbability = scoreProbability(team1ExpectedGoals, team2ExpectedGoals);
        double team1AdvanceProbability = scoreProbability.team1WinProbability
                + (scoreProbability.drawProbability * noDrawTeam1WinProbability);
        double team2AdvanceProbability = 1.0 - team1AdvanceProbability;

        String pick = team1AdvanceProbability >= team2AdvanceProbability ? team1 : team2;
        int advancePct = (int) Math.round(Math.max(team1AdvanceProbability, team2AdvanceProbability) * 100);

        return new Projection(
                team1,
                team2,
                round2(team1ExpectedGoals),
                round2(team2ExpectedGoals),
                roundPct(scoreProbability.team1WinProbability),
                roundPct(scoreProbability.drawProbability),
                roundPct(scoreProbability.team2WinProbability),
                roundPct(team1AdvanceProbability),
                roundPct(team2AdvanceProbability),
                scoreProbability.team1WinProbability,
                scoreProbability.drawProbability,
                scoreProbability.team2WinProbability,
                noDrawTeam1WinProbability,
                scoreProbability.mostLikelyTeam1Goals,
                scoreProbability.mostLikelyTeam2Goals,
                roundPct(scoreProbability.mostLikelyProbability),
                pick,
                advancePct
        );
    }

    private double eloWinProbability(int team1Elo, int team2Elo) {
        return 1.0 / (1.0 + Math.pow(10.0, (team2Elo - team1Elo) / eloScaleDivisor));
    }

    private ScoreProbability scoreProbability(double team1ExpectedGoals, double team2ExpectedGoals) {
        double[] team1GoalProbabilities = poissonProbabilities(team1ExpectedGoals);
        double[] team2GoalProbabilities = poissonProbabilities(team2ExpectedGoals);
        double team1WinProbability = 0.0;
        double drawProbability = 0.0;
        double team2WinProbability = 0.0;
        double mostLikelyProbability = -1.0;
        int mostLikelyTeam1Goals = 0;
        int mostLikelyTeam2Goals = 0;

        for (int team1Goals = 0; team1Goals <= MAX_SCORELINE_GOALS; team1Goals++) {
            for (int team2Goals = 0; team2Goals <= MAX_SCORELINE_GOALS; team2Goals++) {
                double probability = team1GoalProbabilities[team1Goals] * team2GoalProbabilities[team2Goals];
                if (team1Goals > team2Goals) {
                    team1WinProbability += probability;
                } else if (team1Goals == team2Goals) {
                    drawProbability += probability;
                } else {
                    team2WinProbability += probability;
                }
                if (probability > mostLikelyProbability) {
                    mostLikelyProbability = probability;
                    mostLikelyTeam1Goals = team1Goals;
                    mostLikelyTeam2Goals = team2Goals;
                }
            }
        }

        double total = team1WinProbability + drawProbability + team2WinProbability;
        return new ScoreProbability(
                team1WinProbability / total,
                drawProbability / total,
                team2WinProbability / total,
                mostLikelyTeam1Goals,
                mostLikelyTeam2Goals,
                mostLikelyProbability
        );
    }

    private double[] poissonProbabilities(double lambda) {
        double[] probabilities = new double[MAX_SCORELINE_GOALS + 1];
        probabilities[0] = Math.exp(-lambda);
        for (int goals = 1; goals <= MAX_SCORELINE_GOALS; goals++) {
            probabilities[goals] = probabilities[goals - 1] * lambda / goals;
        }
        return probabilities;
    }

    private double clamp(double value) {
        return Math.max(MIN_EXPECTED_GOALS, Math.min(MAX_EXPECTED_GOALS, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static int roundPct(double value) {
        return (int) Math.round(value * 100.0);
    }

    private record ScoreProbability(double team1WinProbability,
                                    double drawProbability,
                                    double team2WinProbability,
                                    int mostLikelyTeam1Goals,
                                    int mostLikelyTeam2Goals,
                                    double mostLikelyProbability) {
    }

    public record Projection(String team1,
                             String team2,
                             double team1ExpectedGoals,
                             double team2ExpectedGoals,
                             int team1WinPct,
                             int drawPct,
                             int team2WinPct,
                             int team1AdvancePct,
                             int team2AdvancePct,
                             double exactTeam1WinProbability,
                             double exactDrawProbability,
                             double exactTeam2WinProbability,
                             double noDrawTeam1WinProbability,
                             int mostLikelyTeam1Goals,
                             int mostLikelyTeam2Goals,
                             int mostLikelyScorePct,
                             String pick,
                             int pickAdvancePct) {

        public String expectedGoalsText() {
            return formatGoals(team1ExpectedGoals) + " - " + formatGoals(team2ExpectedGoals);
        }

        public String mostLikelyScoreText() {
            return mostLikelyTeam1Goals + "-" + mostLikelyTeam2Goals;
        }

        public SampledScoreline sampleScoreline(Random random) {
            int team1Goals = samplePoisson(team1ExpectedGoals, random);
            int team2Goals = samplePoisson(team2ExpectedGoals, random);
            boolean team1Advances = team1Goals > team2Goals
                    || (team1Goals == team2Goals && random.nextDouble() < noDrawTeam1WinProbability);
            return new SampledScoreline(team1Goals, team2Goals, team1Advances);
        }

        private static int samplePoisson(double lambda, Random random) {
            double limit = Math.exp(-lambda);
            double product = 1.0;
            int goals = 0;
            do {
                goals++;
                product *= random.nextDouble();
            } while (product > limit);
            return goals - 1;
        }

        private static String formatGoals(double goals) {
            return String.format(Locale.ROOT, "%.2f", goals);
        }
    }

    public record SampledScoreline(int team1Goals, int team2Goals, boolean team1Advances) {
        public String scoreText() {
            return team1Goals + "-" + team2Goals;
        }
    }
}
