package com.knuddels.jtokkit;

import java.util.function.Predicate;

import static java.lang.Character.toLowerCase;
import static java.util.Arrays.binarySearch;

public class Parser {
    private static final String SDTM = "sdtmSDTM";
    private static final int[] REMAINING_UNICODE_WHITESPACES = "\t\u000B\u000C\u0085\u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000".codePoints().sorted().toArray();

    public static void split(String input, Predicate<CharSequence> fragmentConsumer) {
        for (int index = 0; index < input.length(); ) {
            var nextIndex = index;
            var c0 = input.codePointAt(index);

            if (c0 == '\'' && isShortContraction(input, index)) {
                // 1) `'[sdtm]` - contractions, such as the suffixes of `he's`, `I'd`, `'tis`, `I'm`
                nextIndex += 2;
            } else if (c0 == '\'' && isLongContraction(input, index)) {
                // 1) `'(?:|ll|ve|re)` - contractions, such as the suffixes of `you'll`, `we've`, `they're`
                nextIndex += 3;
            } else if (isUnicodeLetter(c0) || isWord(input, c0, index)) {
                // 2) `[^\r\n\p{L}\p{N}]?+\p{L}+` - words such as ` of`, `th`, `It`, ` not`
                do {
                    nextIndex++;
                } while (nextIndex < input.length() && isUnicodeLetter(input.codePointAt(nextIndex)));
            } else if (isNumeric(c0)) {
                // 3) `\p{N}{1,3}` - numbers, such as `4`, `235` or `3½`
                nextIndex += 1;
                for (int i = 0; i < 2 && nextIndex < input.length() && isNumeric(input.codePointAt(nextIndex)); i++) {
                    nextIndex++;
                }
            } else if (!isWhitespaceOrLetterOrNumeric(c0) || isPunctuation(input, c0, index)) {
                // 4) ` ?[^\s\p{L}\p{N}]++[\r\n]*` - punctuation, such as `,`, ` .`, `"`
                nextIndex += 1;
                while (nextIndex < input.length()) {
                    c0 = input.codePointAt(nextIndex);
                    if (isWhitespaceOrLetterOrNumeric(c0)) {
                        break;
                    }
                    nextIndex++;
                }

                while (nextIndex < input.length() && isNewline(input.codePointAt(nextIndex))) {
                    nextIndex++;
                }
            } else {
                // 5) `\s*[\r\n]+` - line endings such as `\r\n    \r\n`
                // 6) `\s+(?!\S)` - whitespaces such as `               ` or ` `
                // 7) `\s+` - unmatched remaining spaces, such as ` `
                assert isUnicodeWhitespace(c0) : "Unexpected character: " + c0 + " at index " + index + " for text: " + input;

                int lastNewLineIndex = -1;
                do {
                    c0 = input.codePointAt(nextIndex);
                    if (isNewline(c0)) {
                        lastNewLineIndex = nextIndex - index;
                    } else if (c0 != ' ' && !isRemainingWhitespace(c0)) {
                        break;
                    }

                    nextIndex++;
                } while (nextIndex < input.length());

                if (lastNewLineIndex >= 0) {
                    var end = index + lastNewLineIndex + 1;
                    if (fragmentConsumer.test(input.subSequence(index, end))) {
                        return;
                    }
                    nextIndex = index = end;
                }

                if (nextIndex > index) {
                    if (nextIndex < input.length() && !isUnicodeWhitespace(c0) && (nextIndex - index) > 1) {
                        nextIndex--;
                    }
                }
            }

            if (nextIndex > index) {
                if (fragmentConsumer.test(input.subSequence(index, nextIndex))) {
                    return;
                }
                index = nextIndex;
            }
        }
    }

    private static boolean isShortContraction(String input, int index) {
        return index + 1 < input.length()
                && SDTM.indexOf(input.codePointAt(index + 1)) >= 0;
    }

    private static boolean isLongContraction(String input, int index) {
        if (index + 2 >= input.length()) {
            return false;
        }
        var c1 = toLowerCase(input.codePointAt(index + 1));
        var c2 = toLowerCase(input.codePointAt(index + 2));
        return ((c1 == 'l' && c2 == 'l')
                || (c1 == 'v' && c2 == 'e')
                || (c1 == 'r' && c2 == 'e'));
    }

    // 4) ` ?[^\s\p{L}\p{N}]++[\r\n]*` - punctuation, such as `,`, ` .`, `"`
    private static boolean isPunctuation(String input, int ch, int index) {
        return (index + 1 < input.length()) && ch == ' ' && !isWhitespaceOrLetterOrNumeric(input.codePointAt(index + 1));
    }

    // 2) `[^\r\n\p{L}\p{N}]?+\p{L}+` - words such as ` of`, `th`, `It`, ` not`
    private static boolean isWord(String input, int ch, int index) {
        return !isNewlineOrLetterOrNumeric(ch) && (index + 1 < input.length()) && isUnicodeLetter(input.codePointAt(index + 1));
    }


    static boolean isUnicodeLetter(int ch) {
        if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
            return true;
        }
        switch (Character.getType(ch)) {
            case Character.UPPERCASE_LETTER:
            case Character.LOWERCASE_LETTER:
            case Character.TITLECASE_LETTER:
            case Character.MODIFIER_LETTER:
            case Character.OTHER_LETTER:
                return true;
            default:
                return false;
        }
    }

    static boolean isNumeric(int ch) {
        if (ch >= '0' && ch <= '9') {
            return true;
        }
        switch (Character.getType(ch)) {
            case Character.DECIMAL_DIGIT_NUMBER:
            case Character.LETTER_NUMBER:
            case Character.OTHER_NUMBER:
                return true;
            default:
                return false;
        }
    }

    static boolean isUnicodeWhitespace(int ch) {
        if (ch == ' ' || isNewline(ch)) {
            return true;
        }
        return isRemainingWhitespace(ch);
    }

    private static boolean isRemainingWhitespace(int ch) {
        return binarySearch(REMAINING_UNICODE_WHITESPACES, ch) >= 0;
    }

    static boolean isNewline(int ch) {
        return ch == '\n'
                || ch == '\r';
    }

    static boolean isLetterOrNumeric(int ch) {
        return isLetterOrNumericType(Character.getType(ch));
    }

    static boolean isNewlineOrLetterOrNumeric(int ch) {
        return isNewline(ch)
                || isLetterOrNumericType(Character.getType(ch));
    }

    private static boolean isLetterOrNumericType(int type) {
        switch (type) {
            case Character.UPPERCASE_LETTER:
            case Character.LOWERCASE_LETTER:
            case Character.TITLECASE_LETTER:
            case Character.MODIFIER_LETTER:
            case Character.OTHER_LETTER:
            case Character.DECIMAL_DIGIT_NUMBER:
            case Character.LETTER_NUMBER:
            case Character.OTHER_NUMBER:
                return true;
            default:
                return false;
        }
    }

    static boolean isWhitespaceOrLetterOrNumeric(int ch) {
        if (ch == ' ' || isNewline(ch)) {
            return true;
        }
        var type = Character.getType(ch);
        if (isLetterOrNumericType(type)) {
            return true;
        }
        var isPotentialWhitespaceType = type >= Character.SPACE_SEPARATOR && type <= Character.CONTROL;
        return isPotentialWhitespaceType && isRemainingWhitespace(ch);
    }
}