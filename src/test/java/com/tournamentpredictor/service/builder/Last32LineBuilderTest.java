package com.tournamentpredictor.service.builder;

import com.tournamentpredictor.loader.CsvLoader;
import com.tournamentpredictor.service.util.DisplayBuilder;
import com.tournamentpredictor.service.util.EloCalculator;
import com.tournamentpredictor.service.util.PathCalculator;
import com.tournamentpredictor.service.util.PathFatigueCalculator;
import com.tournamentpredictor.service.util.SlotStatusEvaluator;
import com.tournamentpredictor.service.util.ThirdPlaceResolver;
import com.tournamentpredictor.service.util.TokenResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class Last32LineBuilderTest {

    private DisplayBuilder displayBuilder;
    private PathCalculator pathCalculator;
    private EloCalculator eloCalculator;

    @BeforeEach
    void setUp() {
        eloCalculator = new EloCalculator();
        displayBuilder = new DisplayBuilder(new TokenResolver());
        pathCalculator = new PathCalculator(new SlotStatusEvaluator(eloCalculator), eloCalculator);
    }

    // ─── Test data helpers ────────────────────────────────────────────────────

    /**
     * Groups: A1=England, A2=USA, B1=Spain, B2=France, B3=Scotland.
     * Slots A3, A4, B4 are empty. All are explicitly mapped so DisplayBuilder
     * never sees omitted (blank) groupWinner/runnerUp/thirdPlace values.
     */
    private static Map<String, String> baseGroups() {
        Map<String, String> m = new HashMap<>();
        m.put("A1", "England"); m.put("A2", "USA"); m.put("A3", ""); m.put("A4", "");
        m.put("B1", "Spain"); m.put("B2", "France"); m.put("B3", "Scotland"); m.put("B4", "");
        return m;
    }

    private static Map<String, String> baseGroupWinner() {
        Map<String, String> m = new HashMap<>();
        m.put("A1", "yes"); m.put("A2", "no"); m.put("A3", "no"); m.put("A4", "no");
        m.put("B1", "yes"); m.put("B2", "no"); m.put("B3", "no"); m.put("B4", "no");
        return m;
    }

    private static Map<String, String> baseRunnerUp() {
        Map<String, String> m = new HashMap<>();
        m.put("A1", "no"); m.put("A2", "yes"); m.put("A3", "no"); m.put("A4", "no");
        m.put("B1", "no"); m.put("B2", "yes"); m.put("B3", "no"); m.put("B4", "no");
        return m;
    }

    /** Scotland (B3)=yes; all other slots=no. */
    private static Map<String, String> baseThirdPlace() {
        Map<String, String> m = new HashMap<>();
        m.put("A1", "no"); m.put("A2", "no"); m.put("A3", "no"); m.put("A4", "no");
        m.put("B1", "no"); m.put("B2", "no"); m.put("B3", "yes"); m.put("B4", "no");
        return m;
    }

    private static Map<String, Integer> baseElos() {
        return Map.of("England", 2000, "USA", 1800, "Spain", 2100, "France", 2050, "Scotland", 1900);
    }

    /** Stub resolver returning predetermined match→teams assignments. */
    private ThirdPlaceResolver stubResolver(Map<String, List<String>> assignments) {
        return new ThirdPlaceResolver(null) {
            @Override
            public Map<String, List<String>> buildCompositeAssignments(
                    List<CsvLoader.BracketEntry> brackets, Map<String, String> groups,
                    Map<String, String> thirdPlace, Map<String, Integer> eloRatings) {
                return assignments;
            }
        };
    }

    /** Stub resolver that always throws IOException. */
    private ThirdPlaceResolver throwingResolver() {
        return new ThirdPlaceResolver(null) {
            @Override
            public Map<String, List<String>> buildCompositeAssignments(
                    List<CsvLoader.BracketEntry> brackets, Map<String, String> groups,
                    Map<String, String> thirdPlace, Map<String, Integer> eloRatings) throws IOException {
                throw new IOException("simulated failure");
            }
        };
    }

    private Last32LineBuilder builderWith(ThirdPlaceResolver resolver) {
        return new Last32LineBuilder(displayBuilder, pathCalculator, eloCalculator, resolver);
    }

    private Last32LineBuilder builderNoResolver() {
        return new Last32LineBuilder(displayBuilder, pathCalculator, eloCalculator);
    }

    /** Returns the path column values for all data lines with the given matchId. */
    private List<String> pathsFor(List<String> lines, String matchId) {
        List<String> paths = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith(matchId + ",")) {
                String[] cols = line.split(",", -1);
                if (cols.length >= 4) paths.add(cols[3]);
            }
        }
        return paths;
    }

    // ─── Fallback: resolver=null ──────────────────────────────────────────────

    @Nested
    class CompositeFallback {

        /**
         * With no resolver, any 3rd-place team that DisplayBuilder includes (third_place != "no")
         * must appear as "alt", never "predicted".
         */
        @Test
        void teamWithThirdPlaceYes_pathIsAlt() {
            var lines = builderNoResolver().buildLast32Lines(
                    baseGroups(), baseGroupWinner(), baseRunnerUp(), baseThirdPlace(), baseElos(),
                    List.of(new CsvLoader.BracketEntry("M10", "A1", "AB3", "LAST_32")));

            List<String> paths = pathsFor(lines, "M10");
            assertFalse(paths.isEmpty(), "Expected at least one M10 data line");
            assertTrue(paths.stream().allMatch("alt"::equals),
                    "Fallback composite 3rd-place should always be alt, got: " + paths);
        }

        /**
         * When all third_place values are "no", DisplayBuilder produces no displays for the
         * composite token; the bracket entry generates zero data lines.
         */
        @Test
        void allThirdPlaceNo_noRowsProduced() {
            Map<String, String> allNo = new HashMap<>(baseThirdPlace());
            allNo.put("B3", "no");

            var lines = builderNoResolver().buildLast32Lines(
                    baseGroups(), baseGroupWinner(), baseRunnerUp(), allNo, baseElos(),
                    List.of(new CsvLoader.BracketEntry("M10", "A1", "AB3", "LAST_32")));

            assertTrue(pathsFor(lines, "M10").isEmpty(),
                    "Expected no M10 rows when every 3rd-place slot is no");
        }
    }

    // ─── Resolved path: resolver present ─────────────────────────────────────

    @Nested
    class CompositeResolved {

        /** Resolver assigns Scotland to M10; Scotland=yes; England (A1) is primary → predicted. */
        @Test
        void assignedTeam_yesThirdPlace_primaryOpponent_isPredicted() {
            var resolver = stubResolver(Map.of("M10", List.of("Scotland")));
            var lines = builderWith(resolver).buildLast32Lines(
                    baseGroups(), baseGroupWinner(), baseRunnerUp(), baseThirdPlace(), baseElos(),
                    List.of(new CsvLoader.BracketEntry("M10", "A1", "AB3", "LAST_32")));

            List<String> paths = pathsFor(lines, "M10");
            assertTrue(paths.contains("predicted"),
                    "Assigned yes-3rd-place team vs primary group-winner should produce predicted, got: " + paths);
        }

        /**
         * Scotland=maybe (not yes); even when resolver-assigned and facing a primary opponent,
         * the path must be alt — only "yes" earns predicted.
         */
        @Test
        void assignedTeam_maybeThirdPlace_primaryOpponent_isAlt() {
            Map<String, String> tp = new HashMap<>(baseThirdPlace());
            tp.put("B3", "maybe");

            var resolver = stubResolver(Map.of("M10", List.of("Scotland")));
            var lines = builderWith(resolver).buildLast32Lines(
                    baseGroups(), baseGroupWinner(), baseRunnerUp(), tp, baseElos(),
                    List.of(new CsvLoader.BracketEntry("M10", "A1", "AB3", "LAST_32")));

            List<String> paths = pathsFor(lines, "M10");
            assertTrue(paths.stream().allMatch("alt"::equals),
                    "maybe 3rd-place should always be alt even when assigned, got: " + paths);
        }

        /**
         * Resolver assigns Scotland to M10 (yes), but the opposing token A1 has
         * groupWinner="maybe" → isTokenPrimary returns false → path=alt.
         */
        @Test
        void assignedTeam_yesThirdPlace_nonPrimaryOpponent_isAlt() {
            Map<String, String> gw = new HashMap<>(baseGroupWinner());
            gw.put("A1", "maybe"); // England is no longer a confident group winner

            var resolver = stubResolver(Map.of("M10", List.of("Scotland")));
            var lines = builderWith(resolver).buildLast32Lines(
                    baseGroups(), gw, baseRunnerUp(), baseThirdPlace(), baseElos(),
                    List.of(new CsvLoader.BracketEntry("M10", "A1", "AB3", "LAST_32")));

            List<String> paths = pathsFor(lines, "M10");
            assertTrue(paths.stream().allMatch("alt"::equals),
                    "Non-primary (maybe) opponent should keep path alt, got: " + paths);
        }

        /** Resolver assigns a different team (Italy) to M10; Scotland (yes) is not assigned → alt. */
        @Test
        void unassignedTeam_yesThirdPlace_isAlt() {
            var resolver = stubResolver(Map.of("M10", List.of("Italy")));
            var lines = builderWith(resolver).buildLast32Lines(
                    baseGroups(), baseGroupWinner(), baseRunnerUp(), baseThirdPlace(), baseElos(),
                    List.of(new CsvLoader.BracketEntry("M10", "A1", "AB3", "LAST_32")));

            List<String> paths = pathsFor(lines, "M10");
            assertFalse(paths.isEmpty(), "Expected M10 rows for a valid unassigned 3rd-place candidate");
            assertTrue(paths.stream().allMatch("alt"::equals),
                    "Unassigned 3rd-place team should be alt, got: " + paths);
        }

        /** When the resolver throws IOException the builder falls back to fallback logic (alt). */
        @Test
        void ioExceptionFromResolver_fallsBackToAlt() {
            var lines = builderWith(throwingResolver()).buildLast32Lines(
                    baseGroups(), baseGroupWinner(), baseRunnerUp(), baseThirdPlace(), baseElos(),
                    List.of(new CsvLoader.BracketEntry("M10", "A1", "AB3", "LAST_32")));

            List<String> paths = pathsFor(lines, "M10");
            assertFalse(paths.isEmpty(), "Expected M10 rows after IOException fallback");
            assertTrue(paths.stream().allMatch("alt"::equals),
                    "After IOException resolver fallback, composite teams should be alt, got: " + paths);
        }
    }

    // ─── Output structure ─────────────────────────────────────────────────────

    @Nested
    class OutputStructure {

        @Test
        void firstLineIsHeader() {
            var lines = builderNoResolver().buildLast32Lines(
                    baseGroups(), baseGroupWinner(), baseRunnerUp(), baseThirdPlace(), baseElos(),
                    List.of(new CsvLoader.BracketEntry("M10", "A1", "AB3", "LAST_32")));

            assertEquals("match_id,team1,team2,path,elo,team1_path_fatigue,team2_path_fatigue,team1_path_opponent,team2_path_opponent,do_you_disagree", lines.get(0));
        }


        @Test
        void last32RowsIncludeGroupLoadFatigue() {
            Last32LineBuilder builder = new Last32LineBuilder(displayBuilder, pathCalculator, eloCalculator, null, new PathFatigueCalculator());
            var lines = builder.buildLast32Lines(
                    baseGroups(), baseGroupWinner(), baseRunnerUp(), baseThirdPlace(), baseElos(),
                    List.of(new CsvLoader.BracketEntry("M11", "B1", "A2", "LAST_32")));

            String row = lines.stream()
                    .filter(line -> line.startsWith("M11,"))
                    .findFirst()
                    .orElseThrow();
            String[] cols = row.split(",", -1);

            assertEquals("63", cols[5]); // Spain carries France and Scotland group load: 200×0.25 + 50×0.25
            assertEquals("38", cols[6]); // USA carries England group load: 150×0.25
            assertTrue(cols[7].contains("G|France:-6"));
            assertTrue(cols[7].contains("G|Scotland:-2"));
            assertTrue(cols[8].contains("G|England:-5"));
        }

        /** Each LAST_32 match produces exactly one trailing blank line. */
        @Test
        void eachMatchFollowedByTrailingBlank() {
            var lines = builderNoResolver().buildLast32Lines(
                    baseGroups(), baseGroupWinner(), baseRunnerUp(), baseThirdPlace(), baseElos(),
                    Arrays.asList(
                            new CsvLoader.BracketEntry("M10", "A1", "AB3", "LAST_32"),
                            new CsvLoader.BracketEntry("M11", "B1", "A2", "LAST_32")));

            long blankCount = lines.stream().filter(String::isEmpty).count();
            assertEquals(2, blankCount,
                    "Exactly one blank line should follow each LAST_32 match");
        }

        /** Entries with a non-LAST_32 stage are silently ignored. */
        @Test
        void nonLast32StageIgnored() {
            var lines = builderNoResolver().buildLast32Lines(
                    baseGroups(), baseGroupWinner(), baseRunnerUp(), baseThirdPlace(), baseElos(),
                    List.of(new CsvLoader.BracketEntry("M99", "A1", "B1", "QUARTER")));

            // Only the header line should be present (no data, no blanks)
            assertEquals(1, lines.size());
        }
    }
}
