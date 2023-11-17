package com.knuddels.jtokkit;

import java.util.Arrays;
import java.util.function.Predicate;

import static java.lang.Character.isLetter;
import static java.lang.Character.toLowerCase;

public class Parser {
    private static final String SDTM = "sdtmSDTM";
    private static final int[] UNICODE_WHITESPACE = "\t\u000B\u000C\u0085\u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000".codePoints().sorted().toArray();

    public static void split(String input, Predicate<CharSequence> fragmentConsumer) {
        StringBuilder currentToken = new StringBuilder();

        for (int index = 0; index < input.length(); ) {
            var c0 = input.charAt(index);

            if (isShortContraction(input, c0, index)) {
                // 1) `'[sdtm]` - contractions, such as the suffixes of `he's`, `I'd`, `'tis`, `I'm`
                var c1 = input.charAt(index + 1);
                currentToken.appendCodePoint(c0).appendCodePoint(c1);
            } else if (isLongContraction(input, c0, index)) {
                // 1) `'(?:|ll|ve|re)` - contractions, such as the suffixes of `you'll`, `we've`, `they're`
                var c1 = input.charAt(index + 1);
                var c2 = input.charAt(index + 2);
                currentToken.appendCodePoint(c0).appendCodePoint(c1).appendCodePoint(c2);
            } else if (isWord(input, c0, index)) {
                // 2) `[^\r\n\p{L}\p{N}]?+\p{L}+` - words such as ` of`, `th`, `It`, ` not`
                currentToken.appendCodePoint(c0);
                int j = index + 1;
                while (j < input.length()) {
                    c0 = input.charAt(j);
                    if (!isLetter(c0)) {
                        break;
                    }
                    currentToken.appendCodePoint(c0);
                    j++;
                }
            } else if (isNumeric(c0)) {
                // 3) `\p{N}{1,3}` - numbers, such as `4`, `235` or `3½`
                currentToken.appendCodePoint(c0);
                var j = index + 1;
                if (j < input.length()) {
                    var c1 = input.charAt(j);
                    if (isNumeric(c1)) {
                        currentToken.appendCodePoint(c1);
                        j++;
                        if (j < input.length()) {
                            var c2 = input.charAt(j);
                            if (isNumeric(c2)) {
                                currentToken.appendCodePoint(c2);
                            }
                        }
                    }
                }
            } else if (isPunctuation(input, c0, index)) {
                // 4) ` ?[^\s\p{L}\p{N}]++[\r\n]*` - punctuation, such as `,`, ` .`, `"`
                currentToken.appendCodePoint(c0); // space or punctuation
                var j = index + 1;
                while (j < input.length()) {
                    c0 = input.charAt(j);
                    if (isWhitespaceLetterOrNumeric(c0)) {
                        break;
                    }
                    currentToken.appendCodePoint(c0);
                    j++;
                }

                while (j < input.length()) {
                    c0 = input.charAt(j);
                    if (!isNewline(c0)) {
                        break;
                    }

                    currentToken.appendCodePoint(c0);
                    j++;
                }
            } else {
                // 5) `\s*[\r\n]+` - line endings such as `\r\n    \r\n`
                // 6) `\s+(?!\S)` - whitespaces such as `               ` or ` `
                // 7) `\s+` - unmatched remaining spaces, such as ` `
                assert isUnicodeWhitespace(c0) : "Unexpected character: " + c0 + " at index " + index + " for text: " + input;
                int j = index;
                do {
                    c0 = input.charAt(j);
                    if (!isUnicodeWhitespace(c0)) {
                        break;
                    }
                    currentToken.appendCodePoint(c0);
                    j++;
                } while (j < input.length());

                int lastNewLineIndex = Math.max(currentToken.lastIndexOf("\r"), currentToken.lastIndexOf("\n"));
                if (lastNewLineIndex >= 0) {
                    var substring = currentToken.subSequence(0, lastNewLineIndex + 1); // TODO get rid of substring
                    var limitReached = fragmentConsumer.test(substring);
                    if (limitReached) {
                        return;
                    }
                    currentToken.delete(0, lastNewLineIndex + 1);
                    index += substring.length();
                    j = index;
                }

                if (!currentToken.isEmpty()) {
                    if ((j < input.length() && !isUnicodeWhitespace(c0))
                            && (lastNewLineIndex >= 0 || currentToken.length() > 1)) {
                        currentToken.setLength(currentToken.length() - 1);
                    }
                }
            }

            if (!currentToken.isEmpty()) {
                index += currentToken.length();
                boolean limitReached = fragmentConsumer.test(currentToken);
                if (limitReached) {
                    return;
                }

                currentToken.setLength(0);
            }
        }
    }

    // 4) ` ?[^\s\p{L}\p{N}]++[\r\n]*` - punctuation, such as `,`, ` .`, `"`
    private static boolean isPunctuation(String input, int ch, int index) {
        return index + 1 >= input.length()
                || ch == ' ' && !isWhitespaceLetterOrNumeric(input.charAt(index + 1))
                || !isWhitespaceLetterOrNumeric(ch);
    }

    // 2) `[^\r\n\p{L}\p{N}]?+\p{L}+` - words such as ` of`, `th`, `It`, ` not`
    private static boolean isWord(String input, int ch, int index) {
        return isLetter(ch) ||
                (!isNewline(ch) && !isLetterOrNumeric(ch) && (index + 1 >= input.length() || isLetter(input.charAt(index + 1))));
    }

    private static boolean isShortContraction(String input, int ch, int index) {
        if (ch != '\'' || index + 1 >= input.length()) {
            return false;
        }
        return SDTM.indexOf(input.charAt(index + 1)) >= 0;
    }

    private static boolean isLongContraction(String input, int ch, int index) {
        if (ch != '\'' || index + 2 >= input.length()) {
            return false;
        }
        var c1 = toLowerCase(input.charAt(index + 1));
        var c2 = toLowerCase(input.charAt(index + 2));
        return ((c1 == 'l' && c2 == 'l') || (c1 == 'v' && c2 == 'e') || (c1 == 'r' && c2 == 'e'));
    }

    // https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/regex/CharPredicates.java#L320-L322
    static boolean isNumeric(int ch) {
        int type = Character.getType(ch);
        return type == Character.DECIMAL_DIGIT_NUMBER
                || type == Character.LETTER_NUMBER
                || type == Character.OTHER_NUMBER;
    }

    // https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/lang/Character.java#L11377-L11410
    static boolean isUnicodeWhitespace(int ch) {
        return ch == ' ' || isNewline(ch) || Arrays.binarySearch(UNICODE_WHITESPACE, ch) >= 0;
    }

    static boolean isNewline(int ch) {
        return ch == '\r'
                || ch == '\n';
    }


    static boolean isLetterOrNumeric(int ch) {
        return isLetter(ch) || isNumeric(ch);
    }

    static boolean isWhitespaceLetterOrNumeric(int ch) {
        return isUnicodeWhitespace(ch)
                || isLetterOrNumeric(ch);
    }
}