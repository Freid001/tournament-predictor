package com.tournamentpredictor.service.builder;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.PathCalculator;
import com.tournamentpredictor.service.util.PathFatigueCalculator;
import com.tournamentpredictor.service.util.TeamEloSnapshot;

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
