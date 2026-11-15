package com.angel.flexbuddy.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class OcrSampleScoringTest {
    private final String[] expected = {"made-up.png", "ZZT9", "10-18", "10:00", "13:30", "72.00", "18.50"};
    private final String[] actual = {"ZZT9", "10-18", "10:00", "13:30", "72", "18.5"};

    @Test void comparesBothMoneyFieldsNumericallyAndCountsAllSix() {
        var scores = OcrSampleScoring.compare(expected, actual);
        assertTrue(OcrSampleScoring.fullyLabelled(scores));
        assertTrue(OcrSampleScoring.allSixCorrect(scores));
    }

    @Test void legacyFiveFieldLabelsNeverCountAsAllSix() {
        var scores = OcrSampleScoring.compare(Arrays.copyOf(expected, 6), actual);
        assertEquals(OcrSampleScoring.Match.UNLABELLED, scores.get(5));
        assertFalse(OcrSampleScoring.fullyLabelled(scores));
        assertFalse(OcrSampleScoring.allSixCorrect(scores));
    }

    @Test void blankLabelsRemainUnscoredEvenWhenTheActualValueIsMissing() {
        var labels = expected.clone();
        labels[1] = "";
        var values = actual.clone();
        values[0] = null;
        var scores = OcrSampleScoring.compare(labels, values);
        assertEquals(OcrSampleScoring.Match.UNLABELLED, scores.getFirst());
        assertFalse(OcrSampleScoring.allSixCorrect(scores));
        assertTrue(OcrSampleScoring.compare(null, actual).stream()
                .allMatch(match -> match == OcrSampleScoring.Match.UNLABELLED));
    }

    @Test void tipsMismatchAndMissingPayFailAFullyLabelledScreenshot() {
        for (int field : List.of(4, 5)) {
            var values = actual.clone();
            values[field] = field == 4 ? null : "18.51";
            var scores = OcrSampleScoring.compare(expected, values);
            assertEquals(OcrSampleScoring.Match.INCORRECT, scores.get(field));
            assertTrue(OcrSampleScoring.fullyLabelled(scores));
            assertFalse(OcrSampleScoring.allSixCorrect(scores));
        }
    }

    @Test void nonMoneyFieldsAreExactAndIncompleteScoreListsCannotPass() {
        var values = actual.clone();
        values[0] = "zzt9";
        assertEquals(OcrSampleScoring.Match.INCORRECT, OcrSampleScoring.compare(expected, values).getFirst());
        assertFalse(OcrSampleScoring.allSixCorrect(List.of(OcrSampleScoring.Match.CORRECT)));
    }
}
