package com.knuddels.jtokkit;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Character.*;

public class Parser {
    public static final String SDTM = "sdtmSDTM";
    public static final String CRLF = "\r\n";

    public static List<String> parse(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();

        for (int index = 0; index < input.length(); ) {
            int c0 = input.codePointAt(index);

            // 1) `'(?:[sdtm]|ll|ve|re)` - contractions, such as the suffixes of `he's`, `I'd`, `'tis`, `I'm`, `you'll`, `we've`, `they're`
            if (isShortContraction(input, c0, index)) {
                var i1 = index + charCount(c0);
                var c1 = input.codePointAt(i1);
                currentToken.appendCodePoint(c0).appendCodePoint(c1);
            } else if (isLongContraction(input, c0, index)) {
                var i1 = index + charCount(c0);
                var c1 = input.codePointAt(i1);
                var c2 = input.codePointAt(i1 + charCount(c1));
                currentToken.appendCodePoint(c0).appendCodePoint(c1).appendCodePoint(c2);
            } else if (isWord(input, c0, index)) {
                // 2) `[^\r\n\p{L}\p{N}]?+\p{L}+` - words such as ` of`, `th`, `It`, ` not`
                currentToken.appendCodePoint(c0);
                int j = index + charCount(c0);

                while (j < input.length()) {
                    c0 = input.codePointAt(j);
                    if (!isLetter(c0)) {
                        break;
                    }
                    currentToken.appendCodePoint(c0);
                    j += charCount(c0);
                }
            } else if (isNumeric(c0)) {
                // 3) `\p{N}{1,3}` - numbers, such as `4`, `235` or `3½`
                currentToken.appendCodePoint(c0);
                var j = index + charCount(c0);
                if (j < input.length()) {
                    var c1 = input.codePointAt(j);
                    if (isNumeric(c1)) {
                        currentToken.appendCodePoint(c1);
                        j += charCount(c1);
                        if (j < input.length()) {
                            var c2 = input.codePointAt(j);
                            if (isNumeric(c2)) {
                                currentToken.appendCodePoint(c2);
                            }
                        }
                    }
                }
            } else if (isPunctuation(input, c0, index)) {
                // 4) ` ?[^\s\p{L}\p{N}]++[\r\n]*` - punctuation, such as `,`, ` .`, `"`
                currentToken.appendCodePoint(c0); // space or punctuation
                var j = index + charCount(c0);
                while (j < input.length()) {
                    c0 = input.codePointAt(j);
                    if (isWhitespaceLetterOrNumeric(c0)) {
                        break;
                    }
                    currentToken.appendCodePoint(c0);
                    j += charCount(c0);
                }

                while (j < input.length()) {
                    c0 = input.codePointAt(j);
                    if (c0 != '\r' && c0 != '\n') {
                        break;
                    }

                    currentToken.appendCodePoint(c0);
                    j += charCount(c0);
                }
            } else {
                // 5) `\s*[\r\n]+` - line endings such as `\r\n    \r\n`
                // 6) `\s+(?!\S)` - whitespaces such as `               ` or ` `
                // 7) `\s+` - unmatched remaining spaces, such as ` `
                assert isUnicodeWhitespace(c0) : "Unexpected character: " + c0 + " at index " + index + " for text: " + input;
                int j = index;
                do {
                    c0 = input.codePointAt(j);
                    if (!isUnicodeWhitespace(c0)) {
                        break;
                    }
                    currentToken.appendCodePoint(c0);
                    j += charCount(c0);
                } while (j < input.length());

                int lastNewLineIndex = Math.max(currentToken.lastIndexOf("\r"), currentToken.lastIndexOf("\n"));
                if (lastNewLineIndex >= 0) {
                    var substring = currentToken.substring(0, lastNewLineIndex + 1);
                    tokens.add(substring);
                    currentToken.delete(0, lastNewLineIndex + 1);
                    index += substring.codePoints().map(Character::charCount).sum();
                    j = index;
                }

                if (!currentToken.isEmpty()) {
                    if ((j < input.length() && !isUnicodeWhitespace(c0))
                            && (lastNewLineIndex >= 0 || currentToken.length() > charCount(c0))) {
                        currentToken.setLength(currentToken.length() - charCount(c0));
                    }
                }
            }

            if (!currentToken.isEmpty()) {
                index += currentToken.codePoints().map(Character::charCount).sum();
                tokens.add(currentToken.toString());
                currentToken.setLength(0);
            }
        }
        return tokens;
    }

    // 4) ` ?[^\s\p{L}\p{N}]++[\r\n]*` - punctuation, such as `,`, ` .`, `"`
    private static boolean isPunctuation(String input, int c0, int index) {
        int i1 = index + charCount(c0);
        return !isWhitespaceLetterOrNumeric(c0) ||
                i1 >= input.length()
                || c0 == ' ' && !isWhitespaceLetterOrNumeric(input.codePointAt(i1));
    }

    // 2) `[^\r\n\p{L}\p{N}]?+\p{L}+` - words such as ` of`, `th`, `It`, ` not`

    private static boolean isWord(String input, int c0, int index) {
        var i1 = index + charCount(c0);
        return isLetter(c0) ||
                (CRLF.indexOf(c0) < 0 && !isLetterOrNumeric(c0) && (i1 >= input.length() || isLetter(input.codePointAt(i1))));
    }

    private static boolean isShortContraction(String input, int c0, int index) {
        if (c0 != '\'' || index + charCount(c0) >= input.length()) {
            return false;
        }
        var c1 = input.codePointAt(index + charCount(c0));
        return SDTM.indexOf(c1) >= 0;
    }

    private static boolean isLongContraction(String input, int c0, int index) {
        if (c0 != '\'' || index + 2 >= input.length()) {
            return false;
        }
        var c1 = toLowerCase(input.codePointAt(index + charCount(c0)));
        var c2 = toLowerCase(input.codePointAt(index + charCount(c0) + charCount(c1)));
        return ((c1 == 'l' && c2 == 'l') || (c1 == 'v' && c2 == 'e') || (c1 == 'r' && c2 == 'e'));
    }

    // https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/regex/CharPredicates.java#L320-L322
    private static boolean isNumeric(int codePoint) {
        if (Character.isDigit(codePoint)) {
            return true;
        }
        int type = Character.getType(codePoint);
        return type == Character.LETTER_NUMBER
                || type == Character.OTHER_NUMBER;
    }

    // https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/lang/Character.java#L11377-L11410
    private static boolean isUnicodeWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) ||
                codePoint == '\u00A0' || // Non-breaking space
                codePoint == '\u2007' || // Figure space
                codePoint == '\u202F';   // Narrow no-break space
    }

    private static boolean isLetterOrNumeric(int codePoint) {
        return isLetter(codePoint)
                || isNumeric(codePoint);
    }

    private static boolean isWhitespaceLetterOrNumeric(int codePoint) {
        return isUnicodeWhitespace(codePoint)
                || isLetterOrNumeric(codePoint);
    }


}