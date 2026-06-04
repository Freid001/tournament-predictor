package com.tournamentpredictor.service.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scores raw matchup lines produced by the line builders: computes combined predictions
 * (ELO + qualification form) and applies any do_you_disagree overrides.
 *
 * Formula: 85% ELO + 15% qualification form (teams with no qual data: 100% ELO).
 * H2H (combined comp+friendly) is included as a reference column only.
 */
public class PredictionScorer {

    static final String OUTPUT_HEADER =
            "match_id,team1,team2,path,predicted_winner,elo,qualification_form,h2h,prediction";

    private static final CSVFormat LINE_FORMAT = CSVFormat.DEFAULT;

    private final EloCalculator eloCalculator;
    private final HeadToHeadCalculator headToHeadCalculator;
    private final QualificationFormCalculator qualCalc;

    public PredictionScorer(EloCalculator eloCalculator, HeadToHeadCalculator headToHeadCalculator) {
        this(eloCalculator, headToHeadCalculator, null);
    }

    public PredictionScorer(EloCalculator eloCalculator, HeadToHeadCalculator headToHeadCalculator,
                            QualificationFormCalculator qualCalc) {
        this.eloCalculator = eloCalculator;
        this.headToHeadCalculator = headToHeadCalculator;
        this.qualCalc = qualCalc;
    }

    public List<String> scoreLines(List<String> rawLines, Map<String, String> disagreeMap) {
        return scoreLines(rawLines, disagreeMap, null);
    }

    public List<String> scoreLines(List<String> rawLines, Map<String, String> disagreeMap,
                                   QualificationFormCalculator qualificationFormCalculator) {
        QualificationFormCalculator effectiveQualCalc = qualificationFormCalculator != null ? qualificationFormCalculator : qualCalc;
        List<String> output = new ArrayList<>();
        output.add(OUTPUT_HEADER);
        for (int i = 1; i < rawLines.size(); i++) {
            String line = rawLines.get(i);
            if (line.trim().isEmpty()) {
                output.add("");
                continue;
            }
            CSVRecord record = parseLine(line);
            if (record == null || record.size() < 5) {
                continue;
            }

            String matchId = record.get(0).trim();
            String team1Display = record.get(1).trim();
            String team2Display = record.get(2).trim();
            String path = record.get(3).trim();
            String eloPrediction = record.get(4).trim();
            String disagree = disagreeMap.getOrDefault(matchId + "|" + team1Display + "|" + team2Display, "");
            String team1Name = eloCalculator.extractTeamName(team1Display);
            String team2Name = eloCalculator.extractTeamName(team2Display);

            // --- Qualification form (reference display + used in prediction) ---
            boolean hasQual1 = effectiveQualCalc != null && effectiveQualCalc.hasData(team1Name);
            boolean hasQual2 = effectiveQualCalc != null && effectiveQualCalc.hasData(team2Name);
            double qualScore1 = effectiveQualCalc != null ? effectiveQualCalc.getFormScore(team1Name) : 0.5;
            double qualScore2 = effectiveQualCalc != null ? effectiveQualCalc.getFormScore(team2Name) : 0.5;
            boolean hasQualData = hasQual1 || hasQual2;
            double qualRelativePct = (qualScore1 + qualScore2) > 0.0 ? qualScore1 / (qualScore1 + qualScore2) : 0.5;
            String qualFormDisplay = buildFormDisplay(hasQualData, qualRelativePct, team1Name, team2Name, eloPrediction);

            // --- Combined H2H (reference only, not used in prediction) ---
            String h2hDisplay = headToHeadCalculator.computeCombinedPrediction(team1Name, team2Name);

            String prediction = eloCalculator.combinePredictionsWithQual(
                    team1Name, team2Name, eloPrediction, qualRelativePct, hasQualData);
            String predictedWinner = eloCalculator.applyDisagreeOverride(disagree, prediction, team1Display, team2Display);
            output.add(String.join(",", matchId, team1Display, team2Display, path, predictedWinner,
                    eloPrediction, qualFormDisplay, h2hDisplay, prediction));
        }
        return output;
    }

    /** Builds "TeamName (X%)" or "N/A" display from a relative pct for a matchup column. */
    static String buildFormDisplay(boolean hasData, double relativePct, String team1Name, String team2Name, String eloPrediction) {
        if (!hasData) return "N/A";
        int pct1 = (int) Math.round(relativePct * 100);
        int pct2 = 100 - pct1;
        if (pct1 > pct2) return team1Name + " (" + pct1 + "%)";
        if (pct2 > pct1) return team2Name + " (" + pct2 + "%)";
        // tie — favour team with higher ELO
        String eloWinner = eloPrediction.contains("(") ? eloPrediction.substring(0, eloPrediction.lastIndexOf('(')).trim() : team1Name;
        return eloWinner + " (50%)";
    }

    private static CSVRecord parseLine(String line) {
        try {
            List<CSVRecord> records = LINE_FORMAT.parse(new java.io.StringReader(line)).getRecords();
            return records.isEmpty() ? null : records.get(0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
