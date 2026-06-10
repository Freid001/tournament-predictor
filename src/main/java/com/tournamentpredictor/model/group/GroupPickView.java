package com.tournamentpredictor.model.group;

import java.util.LinkedHashMap;
import java.util.List;

public record GroupPickView(List<GroupPickRow> rows, LinkedHashMap<String, List<GroupPickRow>> groupedRows) {
}
