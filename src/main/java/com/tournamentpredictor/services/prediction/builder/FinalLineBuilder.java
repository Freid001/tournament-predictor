package com.tournamentpredictor.services.prediction.builder;

import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PathCalculator;
import com.tournamentpredictor.services.calculation.PathFatigueCalculator;
import com.tournamentpredictor.services.calculation.TeamEloSnapshot;

import java.util.List;
import java.util.Map;

public class FinalLineBuilder {
    private final LaterRoundLineBuilder delegate;

    public FinalLineBuilder(PathCalculator pathCalculator, EloCalculator predictionHelper,
                            PathFatigueCalculator pathFatigueCalc) {
        this.delegate = new LaterRoundLineBuilder(pathCalculator, predictionHelper, pathFatigueCalc);
    }

    public List<String> buildFinalLines(Map<String, Integer> eloRatings, List<CsvLoader.BracketEntry> brackets,
                                        List<String> last4Rows) {
        return buildFinalLines(eloRatings, brackets, last4Rows, Map.of());
    }

    public List<String> buildFinalLines(Map<String, Integer> eloRatings, List<CsvLoader.BracketEntry> brackets,
                                        List<String> last4Rows, Map<String, TeamEloSnapshot> snapshots) {
        return delegate.buildLines(eloRatings, brackets, last4Rows, snapshots, "FINAL", "last_4");
    }
}
