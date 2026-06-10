package com.tournamentpredictor.services.web;

import org.springframework.ui.Model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class PageModelService {
    private final WebControllerService web;

    public PageModelService(WebControllerService web) {
        this.web = web;
    }

    public void addTournamentPage(Model model, String tournament, String pageTitle) {
        model.addAttribute("tournament", tournament);
        model.addAttribute("tournamentLabel", web.displayTournament(tournament));
        model.addAttribute("pageTitle", pageTitle);
    }

    public void addDisabledNavigation(Model model) {
        model.addAttribute("hasPrevRound", false);
        model.addAttribute("hasNextRound", false);
        model.addAttribute("prevViewUrl", "#");
        model.addAttribute("prevViewEnabled", false);
        model.addAttribute("nextViewUrl", "#");
        model.addAttribute("nextViewEnabled", false);
        model.addAttribute("editUrl", null);
    }

    public <T> LinkedHashMap<String, List<T>> groupBy(List<T> rows, Function<T, String> keyExtractor) {
        LinkedHashMap<String, List<T>> groupedRows = new LinkedHashMap<>();
        for (T row : rows) {
            groupedRows.computeIfAbsent(keyExtractor.apply(row), ignored -> new ArrayList<>()).add(row);
        }
        return groupedRows;
    }
}
