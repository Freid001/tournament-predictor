package com.tournamentpredictor.services.prediction.builder;

import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PathCalculator;
import com.tournamentpredictor.services.calculation.PathFatigueCalculator;
import com.tournamentpredictor.services.calculation.TeamEloSnapshot;

import java.util.List;
import java.util.Map;

public class Last8LineBuilder {
    private final LaterRoundLineBuilder delegate;

    public Last8LineBuilder(PathCalculator pathCalculator, EloCalculator predictionHelper,
                            PathFatigueCalculator pathFatigueCalc) {
        this.delegate = new LaterRoundLineBuilder(pathCalculator, predictionHelper, pathFatigueCalc);
    }

    public List<String> buildLast8Lines(Map<String, Integer> eloRatings, List<CsvLoader.BracketEntry> brackets,
                                        List<String> last16Rows) {
        return buildLast8Lines(eloRatings, brackets, last16Rows, Map.of());
    }

    public List<String> buildLast8Lines(Map<String, Integer> eloRatings, List<CsvLoader.BracketEntry> brackets,
                                        List<String> last16Rows, Map<String, TeamEloSnapshot> snapshots) {
        return delegate.buildLines(eloRatings, brackets, last16Rows, snapshots, "QUARTER", "last_16");
    }
}
