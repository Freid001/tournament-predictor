package com.tournamentpredictor.service.builder;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.PathCalculator;
import com.tournamentpredictor.service.util.PathFatigueCalculator;
import com.tournamentpredictor.service.util.TeamEloSnapshot;

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
