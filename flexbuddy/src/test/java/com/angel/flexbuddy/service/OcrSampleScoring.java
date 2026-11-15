package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Test-only accuracy accounting; no screenshot parsing or earnings rules. */
final class OcrSampleScoring {
    static final String[] FIELDS = {"station", "date", "start", "end", "pay", "tips"};
    enum Match { UNLABELLED, CORRECT, INCORRECT }

    private OcrSampleScoring() {}

    /** Expected columns include the source file first. Five-field legacy CSVs simply leave tips unscored. */
    static List<Match> compare(String[] expected, String[] actual) {
        if (actual.length != FIELDS.length) throw new IllegalArgumentException("Six actual fields required");
        List<Match> matches = new ArrayList<>();
        for (int field = 0; field < FIELDS.length; field++) {
            String want = expected == null || expected.length <= field + 1 ? "" : expected[field + 1];
            if (want.isEmpty()) {
                matches.add(Match.UNLABELLED);
                continue;
            }
            boolean correct = actual[field] != null && (field >= 4
                    ? new BigDecimal(want).compareTo(new BigDecimal(actual[field])) == 0
                    : want.equals(actual[field]));
            matches.add(correct ? Match.CORRECT : Match.INCORRECT);
        }
        return List.copyOf(matches);
    }

    static boolean fullyLabelled(List<Match> matches) {
        return matches.size() == FIELDS.length && !matches.contains(Match.UNLABELLED);
    }

    static boolean allSixCorrect(List<Match> matches) {
        return fullyLabelled(matches) && matches.stream().allMatch(match -> match == Match.CORRECT);
    }
}
