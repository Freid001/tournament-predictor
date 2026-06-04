package com.tournamentpredictor.service.mapper;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class DisagreeMapMapper {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

    public Map<String, String> loadDisagreeMap(Path predictionsFile) throws IOException {
        Map<String, String> disagreeMap = new LinkedHashMap<>();
        try (var reader = Files.newBufferedReader(predictionsFile);
             var parser = FORMAT.parse(reader)) {
            for (CSVRecord record : parser) {
                if (record.size() < 3) continue;
                String disagree = record.isMapped("do_you_disagree")
                        ? record.get("do_you_disagree")
                        : (record.size() > 5 ? record.get(5) : "");
                if (!disagree.isEmpty()) {
                    disagreeMap.put(record.get(0) + "|" + record.get(1) + "|" + record.get(2), disagree);
                }
            }
        }
        return disagreeMap;
    }
}
