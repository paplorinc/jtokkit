package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;

import java.util.Arrays;
import java.util.Iterator;

import static java.lang.Character.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.binarySearch;

public class Cl100kParser {
    private static final String SDTM = "sdtmSDTM";
    private static final String SIMPLE_WHITESPACES = "\t\n\u000B\u000C\r";
    private static final int[] REMAINING_WHITESPACES = "\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000".codePoints().sorted().toArray();

    public static Iterable<ByteArrayList> split(String input) {
        assert isValidUTF8(input) : "Input is not UTF-8: " + input;
        return new TokenIterator(input);
    }

    public static boolean isValidUTF8(String input) {
        return UTF_8.newEncoder().canEncode(input);
    }

    static boolean isLetter(int ch) {
        if (ch < 0xaa) {
            return (ch >= 'A') && (ch <= 'z') && ((ch >= 'a') || (ch <= 'Z'));
        } else if (ch <= 0x323af) {
            switch (getType(ch)) {
                case UPPERCASE_LETTER:
                case LOWERCASE_LETTER:
                case TITLECASE_LETTER:
                case MODIFIER_LETTER:
                case OTHER_LETTER:
                    return true;
            }
        }
        return false;
    }

    static boolean isNumeric(int ch) {
        if (ch < 0xb2) {
            return (ch >= '0') && (ch <= '9');
        } else if (ch <= 0x1fbf9) {
            switch (getType(ch)) {
                case DECIMAL_DIGIT_NUMBER:
                case LETTER_NUMBER:
                case OTHER_NUMBER:
                    return true;
            }
        }
        return false;
    }

    static boolean isLetterOrNumeric(int ch) {
        if (ch < 0xaa) {
            return (((ch >= 'A') && (ch <= 'z') && ((ch >= 'a') || (ch <= 'Z'))) || ((ch >= '0') && (ch <= '9')));
        } else if (ch <= 0x323af) {
            switch (getType(ch)) {
                case UPPERCASE_LETTER:
                case LOWERCASE_LETTER:
                case TITLECASE_LETTER:
                case MODIFIER_LETTER:
                case OTHER_LETTER:
                case DECIMAL_DIGIT_NUMBER:
                case LETTER_NUMBER:
                case OTHER_NUMBER:
                    return true;
            }
        }
        return false;
    }

    static boolean isWhitespace(int ch) {
        if (ch <= '\r') {
            return SIMPLE_WHITESPACES.indexOf(ch) >= 0;
        } else if (ch < '\u0085') {
            return ch == ' ';
        } else {
            return (ch == '\u0085')
                    || (ch == '\u00A0')
                    || ((ch >= '\u1680') && (ch <= '\u3000') && (binarySearch(REMAINING_WHITESPACES, ch) >= 0));
        }
    }

    static boolean isWhitespaceOrLetterOrNumeric(int ch) {
        return isWhitespace(ch)
                || isLetterOrNumeric(ch);
    }

    static boolean isNewline(int ch) {
        return (ch == '\n')
                || (ch == '\r');
    }

    static boolean isNewlineOrLetterOrNumeric(int ch) {
        return isNewline(ch)
                || isLetterOrNumeric(ch);
    }

    public static ByteArrayList toUtf8Bytes(String input, int start, int end, ByteArrayList dst) {
        assert end > start;
        dst.clear();
        for (int i = start; i < end; i++) {
            assert Arrays.equals(input.substring(start, i).getBytes(UTF_8), dst.toByteArray())
                    : "Mismatch around byte for (" + start + ", " + i + ") - `" + input.substring(start, i) + "` (" + Arrays.toString(input.substring(start, i).getBytes(UTF_8)) + ") != `" + new String(dst.toByteArray(), UTF_8) + "` in `" + Arrays.toString(dst.toByteArray()) + "`";
            int cp = input.codePointAt(i);
            if (cp < 0x80) {
                dst.add((byte) cp);
            } else if (cp < 0x800) {
                dst.add((byte) (0xc0 | (cp >> 0x6)));
                dst.add((byte) (0x80 | (cp & 0x3f)));
            } else if (cp < MIN_SUPPLEMENTARY_CODE_POINT) {
                dst.add((byte) (0xe0 | (cp >> 0xc)));
                dst.add((byte) (0x80 | ((cp >> 0x6) & 0x3f)));
                dst.add((byte) (0x80 | (cp & 0x3f)));
            } else {
                assert cp < (MAX_CODE_POINT + 1) : "Invalid code point: " + cp;
                dst.add((byte) (0xf0 | (cp >> 0x12)));
                dst.add((byte) (0x80 | ((cp >> 0xc) & 0x3f)));
                dst.add((byte) (0x80 | ((cp >> 0x6) & 0x3f)));
                dst.add((byte) (0x80 | (cp & 0x3f)));
                i++;
            }
        }
        return dst;
    }

    private static class TokenIterator implements Iterable<ByteArrayList>, Iterator<ByteArrayList> {
        private final ByteArrayList utf8Bytes = new ByteArrayList();
        private final String input;
        private int endIndex = 0;

        public TokenIterator(String input) {
            this.input = input;
        }

        @Override
        public Iterator<ByteArrayList> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            return endIndex < input.length();
        }

        @Override
        public ByteArrayList next() {
            int startIndex = endIndex;
            int c0 = input.codePointAt(startIndex);

            if ((c0 == '\'') && ((startIndex + 1) < input.length())) {
                int c1 = input.codePointAt(startIndex + 1);
                if (SDTM.indexOf(c1) >= 0) {
                    // 1) `'[sdtm]` - contractions, such as the suffixes of `he's`, `I'd`, `'tis`, `I'm`
                    endIndex += 2;
                    return toUtf8Bytes(input, startIndex, endIndex, utf8Bytes);
                } else if ((startIndex + 2) < input.length()) {
                    // 1) `'(?:ll|ve|re)` - contractions, such as the suffixes of `you'll`, `we've`, `they're`
                    int lc1 = toLowerCase(c1);
                    int lc2 = toLowerCase(input.codePointAt(startIndex + 2));
                    if (((lc1 == 'l') && (lc2 == 'l'))
                            || ((lc1 == 'v') && (lc2 == 'e'))
                            || ((lc1 == 'r') && (lc2 == 'e'))) {
                        endIndex += 3;
                        return toUtf8Bytes(input, startIndex, endIndex, utf8Bytes);
                    }
                }
            }

            int cc0 = charCount(c0);
            int nextIndex = startIndex + cc0;
            int c1 = (nextIndex < input.length()) ? input.codePointAt(nextIndex) : -1;
            int cc1 = charCount(c1);
            if (isLetter(c0) || (!isNewlineOrLetterOrNumeric(c0) && isLetter(c1))) {
                // 2) `[^\r\n\p{L}\p{N}]?+\p{L}+` - words such as ` of`, `th`, `It`, ` not`
                endIndex += cc0;
                if (isLetter(c1)) {
                    endIndex += cc1;
                    while ((endIndex < input.length()) && isLetter(c0 = input.codePointAt(endIndex))) {
                        endIndex += charCount(c0);
                    }
                }
                return toUtf8Bytes(input, startIndex, endIndex, utf8Bytes);
            }

            if (isNumeric(c0)) {
                // 3) `\p{N}{1,3}` - numbers, such as `4`, `235` or `3½`
                endIndex += cc0;
                if (isNumeric(c1)) {
                    endIndex += cc1;
                    if ((endIndex < input.length()) && isNumeric(c0 = input.codePointAt(endIndex))) {
                        endIndex += charCount(c0);
                    }
                }
                return toUtf8Bytes(input, startIndex, endIndex, utf8Bytes);
            }

            if (!isWhitespaceOrLetterOrNumeric(c0) || ((c0 == ' ') && !isWhitespaceOrLetterOrNumeric(c1))) {
                // 4) ` ?[^\s\p{L}\p{N}]++[\r\n]*` - punctuation, such as `,`, ` .`, `"`
                endIndex += cc0;
                if ((endIndex < input.length()) && !isWhitespaceOrLetterOrNumeric(c1)) {
                    endIndex += cc1;
                    while ((endIndex < input.length()) && !isWhitespaceOrLetterOrNumeric(c0 = input.codePointAt(endIndex))) {
                        endIndex += charCount(c0);
                    }
                }
                while ((endIndex < input.length()) && isNewline(input.codePointAt(endIndex))) {
                    endIndex++;
                }
                return toUtf8Bytes(input, startIndex, endIndex, utf8Bytes);
            }

            // 5) `\s*[\r\n]+` - line endings such as `\r\n    \r\n`
            // 6) `\s+(?!\S)` - whitespaces such as `               ` or ` `
            // 7) `\s+` - unmatched remaining spaces, such as ` `
            assert isWhitespace(c0);
            int lastNewLineIndex = isNewline(c0) ? endIndex : -1;
            assert charCount(c0) == 1;
            endIndex++;
            if (isWhitespace(c1)) {
                assert cc1 == 1;
                if (isNewline(c1)) {
                    lastNewLineIndex = endIndex;
                }
                endIndex++;
                while (endIndex < input.length()) {
                    c0 = input.codePointAt(endIndex);
                    if (!isWhitespace(c0)) {
                        break;
                    }
                    if (isNewline(c0)) {
                        lastNewLineIndex = endIndex;
                    }

                    assert charCount(c0) == 1;
                    endIndex++;
                }
            }

            if (lastNewLineIndex >= startIndex) {
                endIndex = lastNewLineIndex + 1;
            } else {
                assert charCount(input.codePointAt(endIndex - 1)) == 1;
                if ((endIndex < input.length()) && ((endIndex - startIndex) > 1) && !isWhitespace(c0)) {
                    endIndex--;
                }
            }
            return toUtf8Bytes(input, startIndex, endIndex, utf8Bytes);
        }
    }
}