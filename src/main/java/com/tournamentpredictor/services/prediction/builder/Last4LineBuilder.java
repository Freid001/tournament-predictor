package com.tournamentpredictor.services.prediction.builder;

import com.tournamentpredictor.services.io.CsvLoader;
import com.tournamentpredictor.services.calculation.EloCalculator;
import com.tournamentpredictor.services.calculation.PathCalculator;
import com.tournamentpredictor.services.calculation.PathFatigueCalculator;
import com.tournamentpredictor.services.calculation.TeamEloSnapshot;

import java.util.List;
import java.util.Map;

public class Last4LineBuilder {
    private final LaterRoundLineBuilder delegate;

    public Last4LineBuilder(PathCalculator pathCalculator, EloCalculator predictionHelper,
                            PathFatigueCalculator pathFatigueCalc) {
        this.delegate = new LaterRoundLineBuilder(pathCalculator, predictionHelper, pathFatigueCalc);
    }

    public List<String> buildLast4Lines(Map<String, Integer> eloRatings, List<CsvLoader.BracketEntry> brackets,
                                        List<String> last8Rows) {
        return buildLast4Lines(eloRatings, brackets, last8Rows, Map.of());
    }

    public List<String> buildLast4Lines(Map<String, Integer> eloRatings, List<CsvLoader.BracketEntry> brackets,
                                        List<String> last8Rows, Map<String, TeamEloSnapshot> snapshots) {
        return delegate.buildLines(eloRatings, brackets, last8Rows, snapshots, "SEMI", "last_8");
    }
}
