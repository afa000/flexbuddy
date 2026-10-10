package com.angel.flexbuddy.i18n;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the string and template literals in a browser script, skipping comments and regular expressions. A template
 * literal gives its text without the {@code ${...}} parts, and the literals inside those parts are found as well. This
 * is a scanner for a test, not a JavaScript parser: it is good enough for the code in {@code static/js}.
 */
final class ScriptLiterals {

    /** One literal: its quote character, its text (escapes left as written) and the line it starts on. */
    record Literal(char quote, String text, int line) {
    }

    private static final String REGEX_AFTER = "(,=:[!&|?{};+-*%<>~^";

    private ScriptLiterals() {
    }

    static List<Literal> in(String source) {
        List<Literal> found = new ArrayList<>();
        scan(source, 0, source.length(), found);
        return found;
    }

    private static int line(String source, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (source.charAt(i) == '\n') line++;
        }
        return line;
    }

    private static void scan(String source, int from, int to, List<Literal> found) {
        int i = from;
        char previous = 0;
        while (i < to) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < to && source.charAt(i + 1) == '/') {
                int end = source.indexOf('\n', i);
                i = end < 0 || end > to ? to : end;
                continue;
            }
            if (c == '/' && i + 1 < to && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? to : end + 2;
                continue;
            }
            if (c == '/' && (previous == 0 || REGEX_AFTER.indexOf(previous) >= 0 || endsWithKeyword(source, i))) {
                i = skipRegex(source, i, to);
                previous = ')';
                continue;
            }
            if (c == '\'' || c == '"') {
                int end = i + 1;
                while (end < to && source.charAt(end) != c) {
                    end += source.charAt(end) == '\\' ? 2 : 1;
                }
                found.add(new Literal(c, source.substring(i + 1, Math.min(end, to)), line(source, i)));
                i = end + 1;
                previous = 'a';
                continue;
            }
            if (c == '`') {
                i = scanTemplate(source, i, to, found);
                previous = 'a';
                continue;
            }
            if (!Character.isWhitespace(c)) previous = c;
            i++;
        }
    }

    private static boolean endsWithKeyword(String source, int index) {
        String before = source.substring(Math.max(0, index - 7), index).stripTrailing();
        return before.endsWith("return") || before.endsWith("typeof");
    }

    private static int skipRegex(String source, int start, int to) {
        int i = start + 1;
        boolean inClass = false;
        while (i < to) {
            char c = source.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '[') inClass = true;
            else if (c == ']') inClass = false;
            else if ((c == '/' && !inClass) || c == '\n') break;
            i++;
        }
        return i + 1;
    }

    /** Reads a template literal from its opening backtick; returns the index after its closing one. */
    private static int scanTemplate(String source, int start, int to, List<Literal> found) {
        StringBuilder text = new StringBuilder();
        int i = start + 1;
        while (i < to) {
            char c = source.charAt(i);
            if (c == '\\') {
                text.append(source, i, Math.min(i + 2, to));
                i += 2;
                continue;
            }
            if (c == '`') break;
            if (c == '$' && i + 1 < to && source.charAt(i + 1) == '{') {
                int depth = 1;
                int expressionStart = i + 2;
                int j = expressionStart;
                while (j < to && depth > 0) {
                    char d = source.charAt(j);
                    if (d == '\\') {
                        j += 2;
                        continue;
                    }
                    if (d == '\'' || d == '"') {
                        j++;
                        while (j < to && source.charAt(j) != d) j += source.charAt(j) == '\\' ? 2 : 1;
                    } else if (d == '`') {
                        j = scanTemplate(source, j, to, new ArrayList<>()) - 1;
                    } else if (d == '{') {
                        depth++;
                    } else if (d == '}') {
                        depth--;
                    }
                    j++;
                }
                scan(source, expressionStart, j - 1, found);
                i = j;
                continue;
            }
            text.append(c);
            i++;
        }
        found.add(new Literal('`', text.toString(), line(source, start)));
        return i + 1;
    }
}
