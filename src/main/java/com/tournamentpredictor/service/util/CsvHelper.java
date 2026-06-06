package com.tournamentpredictor.service.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CsvHelper {

    private static final CSVFormat LINE_FORMAT = CSVFormat.DEFAULT;

    public void checkNotLocked(Path output) throws IOException {
        if (Files.exists(output)) {
            throw new IOException("Output already exists (locked): " + output + ". Delete it to re-run this mode.");
        }
    }

    public boolean isLocked(Path output) {
        return Files.exists(output);
    }

    public List<String> sortGroupsPrimaryFirst(List<String> lines) {
        if (lines.isEmpty()) {
            return lines;
        }
        List<String> result = new ArrayList<>();
        result.add(lines.get(0));
        List<String> group = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) {
                flushSortedGroup(group, result);
                result.add("");
                group.clear();
            } else {
                group.add(line);
            }
        }
        if (!group.isEmpty()) {
            flushSortedGroup(group, result);
        }
        return result;
    }

    /**
     * Returns header + model-selected rows only. Blank separators and alternate rows are removed.
     * This defines the generated bracket for staged analysis; it does not prune branches from
     * SimulationHandler.handleGroups(), where every sampled winner continues through that run.
     */
    public List<String> filterPrimaryOnly(List<String> lines) {
        List<String> output = new ArrayList<>();
        if (lines.isEmpty()) return output;
        output.add(lines.get(0)); // header
        for (int i = 1; i < lines.size(); i++) {
            String row = lines.get(i);
            if (row.trim().isEmpty()) continue;
            if ("predicted".equals(getCol(row, 3))) {
                output.add(row);
            }
        }
        return output;
    }

    public void flushSortedGroup(List<String> group, List<String> result) {
        group.sort((left, right) -> {
            String leftPath  = getCol(left, 3);
            String rightPath = getCol(right, 3);
            return Integer.compare("predicted".equals(leftPath) ? 0 : 1, "predicted".equals(rightPath) ? 0 : 1);
        });
        result.addAll(group);
    }

    private static String getCol(String line, int index) {
        try {
            List<CSVRecord> records = LINE_FORMAT.parse(new StringReader(line)).getRecords();
            if (records.isEmpty() || records.get(0).size() <= index) return "";
            return records.get(0).get(index).trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

