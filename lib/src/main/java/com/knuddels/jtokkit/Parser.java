package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;

import java.util.Arrays;
import java.util.function.Predicate;

import static java.lang.Character.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.binarySearch;

public class Parser {

    private static final String SDTM = "sdtmSDTM";
    private static final int[] REMAINING_UNICODE_WHITESPACES = "\t\u000B\u000C\u0085\u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000".codePoints().sorted().toArray();

    public static void split(String input, Predicate<ByteArrayList> fragmentConsumer) {
        assert isValidUTF8(input) : "Input is not UTF-8: " + input;
        var utf8Bytes = new ByteArrayList();
        for (int startIndex = 0, endIndex = startIndex; startIndex < input.length(); ) {
            var c0 = input.codePointAt(startIndex);

            if (c0 == '\'' && isShortContraction(input, startIndex)) {
                // 1) `'[sdtm]` - contractions, such as the suffixes of `he's`, `I'd`, `'tis`, `I'm`
                endIndex += 2;
            } else if (c0 == '\'' && isLongContraction(input, startIndex)) {
                // 1) `'(?:ll|ve|re)` - contractions, such as the suffixes of `you'll`, `we've`, `they're`
                endIndex += 3;
            } else if (isLetter(c0) || isWord(input, c0, startIndex)) {
                // 2) `[^\r\n\p{L}\p{N}]?+\p{L}+` - words such as ` of`, `th`, `It`, ` not`
                do {
                    endIndex += Character.charCount(c0);
                } while (endIndex < input.length() && isLetter(c0 = input.codePointAt(endIndex)));
            } else if (isNumeric(c0)) {
                // 3) `\p{N}{1,3}` - numbers, such as `4`, `235` or `3½`
                endIndex += Character.charCount(c0);
                for (var i = 0; i < 2 && endIndex < input.length() && isNumeric(c0 = input.codePointAt(endIndex)); i++) {
                    endIndex += Character.charCount(c0);
                }
            } else if (!isWhitespaceOrLetterOrNumeric(c0) || isSpaceOrPunctuation(input, c0, startIndex)) {
                // 4) ` ?[^\s\p{L}\p{N}]++[\r\n]*` - punctuation, such as `,`, ` .`, `"`
                endIndex += Character.charCount(c0);
                while (endIndex < input.length() && !isWhitespaceOrLetterOrNumeric(c0 = input.codePointAt(endIndex))) {
                    endIndex += Character.charCount(c0);
                }

                while (endIndex < input.length() && isNewline(input.codePointAt(endIndex))) {
                    endIndex++;
                }
            } else {
                // 5) `\s*[\r\n]+` - line endings such as `\r\n    \r\n`
                // 6) `\s+(?!\S)` - whitespaces such as `               ` or ` `
                // 7) `\s+` - unmatched remaining spaces, such as ` `
                var lastNewLineIndex = -1;
                do {
                    c0 = input.codePointAt(endIndex);
                    if (isNewline(c0)) {
                        lastNewLineIndex = endIndex;
                    } else if (c0 != ' ' && !isRemainingWhitespace(c0)) {
                        break;
                    }

                    assert isWhitespace(c0);
                    endIndex += Character.charCount(c0);
                } while (endIndex < input.length());

                if (lastNewLineIndex >= startIndex) {
                    endIndex = lastNewLineIndex + 1;
                    if (fragmentConsumer.test(toUtf8Bytes(input, startIndex, endIndex, utf8Bytes))) {
                        return;
                    }
                    startIndex = endIndex;
                } else {
                    var charCount = charCount(input.codePointAt(endIndex - 1));
                    if (endIndex < input.length() && (endIndex - startIndex) > charCount && !isWhitespace(c0)) {
                        endIndex -= charCount;
                    }
                }
            }
            if (endIndex > startIndex) {
                if (fragmentConsumer.test(toUtf8Bytes(input, startIndex, endIndex, utf8Bytes))) {
                    return;
                }
                startIndex = endIndex;
            }
        }
    }

    public static boolean isValidUTF8(String input) {
        return UTF_8.newEncoder().canEncode(input);
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

    private static boolean isSpaceOrPunctuation(String input, int ch, int index) {
        return ch == ' '
                && index + Character.charCount(ch) < input.length()
                && !isWhitespaceOrLetterOrNumeric(input.codePointAt(index + Character.charCount(ch)));
    }

    private static boolean isWord(String input, int ch, int index) {
        return !isNewlineOrLetterOrNumeric(ch)
                && (index + Character.charCount(ch) < input.length())
                && isLetter(input.codePointAt(index + Character.charCount(ch)));
    }

    static boolean isLetter(int ch) {
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

    static boolean isWhitespace(int ch) {
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

    public static ByteArrayList toUtf8Bytes(String input, int start, int end, ByteArrayList dst) {
        dst.clear();
        for (int i = start; i < end; i++) {
            assert Arrays.equals(input.substring(start, i).getBytes(UTF_8), dst.toByteArray())
                    : "Mismatch around byte for (" + start + ", " + i + ") - `" + input.substring(start, i) + "` (" + Arrays.toString(input.substring(start, i).getBytes(UTF_8)) + ") != `" + new String(dst.toByteArray(), UTF_8) + "` in `" + Arrays.toString(dst.toByteArray()) + "`";
            int cp = input.codePointAt(i);
            if (cp < 0x80) {
                dst.add((byte) cp);
            } else if (cp < 0x800) {
                dst.add((byte) (0xc0 | cp >> 0x6));
                dst.add((byte) (0x80 | cp & 0x3f));
            } else if (cp < MIN_SUPPLEMENTARY_CODE_POINT) {
                dst.add((byte) (0xe0 | cp >> 0xc));
                dst.add((byte) (0x80 | cp >> 0x6 & 0x3f));
                dst.add((byte) (0x80 | cp & 0x3f));
            } else {
                assert cp < MAX_CODE_POINT + 1 : "Invalid code point: " + cp;
                dst.add((byte) (0xf0 | cp >> 0x12));
                dst.add((byte) (0x80 | cp >> 0xc & 0x3f));
                dst.add((byte) (0x80 | cp >> 0x6 & 0x3f));
                dst.add((byte) (0x80 | cp & 0x3f));
                i++;
            }
        }
        return dst;
    }
}