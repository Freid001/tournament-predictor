package com.tournamentpredictor.services.web;

import org.junit.jupiter.api.Test;
import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultFormServiceTest {
    private final ResultFormService service = new ResultFormService();

    @Test
    void knockoutRowsSkipBlankRowsAndKeepPenalties() {
        Map<String, String> params = new HashMap<>();
        params.put("rowCount_last_16", "2");
        params.put("matchId_last_16_0", "M1");
        params.put("team1_last_16_0", "Spain");
        params.put("team2_last_16_0", "Germany");
        params.put("winner_last_16_0", "Spain");
        params.put("homeScore_last_16_0", "1");
        params.put("awayScore_last_16_0", "1");
        params.put("penalties_last_16_0", "on");
        HttpServletRequest request = request(params);

        List<Map<String, String>> rows = service.knockoutRows(request, "last_16");

        assertEquals(1, rows.size());
        assertEquals("last_16", rows.get(0).get("round"));
        assertEquals("M1", rows.get(0).get("match_id"));
        assertEquals("yes", rows.get(0).get("penalties"));
    }

    @Test
    void groupRowsUseCompactFieldNames() {
        Map<String, String> params = new HashMap<>();
        params.put("rowCount", "1");
        params.put("group0", "A");
        params.put("matchId0", "A1");
        params.put("team10", "France");
        params.put("team20", "Italy");
        params.put("winner0", "Italy");
        params.put("homeScore0", "0");
        params.put("awayScore0", "2");
        HttpServletRequest request = request(params);

        List<Map<String, String>> rows = service.groupRows(request);

        assertEquals(1, rows.size());
        assertEquals(ResultFormService.GROUP_HEADERS, List.copyOf(rows.get(0).keySet()));
        assertEquals("groups", rows.get(0).get("round"));
        assertEquals("A", rows.get(0).get("group"));
        assertEquals("", rows.get(0).get("penalties"));
    }

    private HttpServletRequest request(Map<String, String> params) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> "getParameter".equals(method.getName()) ? params.get((String) args[0]) : null
        );
    }
}
