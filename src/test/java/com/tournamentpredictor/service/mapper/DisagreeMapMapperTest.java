package com.tournamentpredictor.service.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisagreeMapMapperTest {
    @TempDir
    Path tempDir;

    @Test
    void loadDisagreeMap_readsDynamicDoYouDisagreeColumn() throws IOException {
        Path file = tempDir.resolve("last_32.csv");
        Files.write(file, List.of(
                "match_id,team1,team2,path,elo,history_competitions,history_friendlies,prediction,do_you_disagree",
                "M1,England,Germany,predicted,England (60%),England (55%),England (54%),England (58%),yes"
        ));

        Map<String, String> disagreeMap = new DisagreeMapMapper().loadDisagreeMap(file);

        assertEquals("yes", disagreeMap.get("M1|England|Germany"));
    }

    @Test
    void loadDisagreeMap_fallsBackToLegacyColumnIndex() throws IOException {
        Path file = tempDir.resolve("last_16.csv");
        Files.write(file, List.of(
                "match_id,team1,team2,path,predicted_winner,legacy_column",
                "M2,Spain,France,predicted,Spain (52%),no"
        ));

        Map<String, String> disagreeMap = new DisagreeMapMapper().loadDisagreeMap(file);

        assertEquals("no", disagreeMap.get("M2|Spain|France"));
    }
}
