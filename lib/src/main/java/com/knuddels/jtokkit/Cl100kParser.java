package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;

import java.util.Arrays;

import static java.lang.Character.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.binarySearch;


public class Cl100kParser {
    private static final String SDTM = "sdtmSDTMſ";
    private static final String SIMPLE_WHITESPACES = "\t\n\u000B\u000C\r";
    private static final int[] REMAINING_WHITESPACES = "\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000".codePoints().sorted().toArray();

    public static int split(String input, int maxTokenCount, TokenConsumer tokenConsumer) {
        assert isValidUTF8(input) : "Input is not UTF-8: " + input;
        var utf8Bytes = new ByteArrayList();
        var codePoints = input.codePoints().iterator();
        var ch = new int[]{-1, -1, -1};

        var tokenCount = 0;
        while (tokenCount < maxTokenCount) {
            for (var i = 0; i < ch.length && codePoints.hasNext(); i++) {
                if (ch[i] < 0) {
                    ch[i] = codePoints.nextInt();
                }
            }
            if (ch[0] < 0) {
                break;
            }
            utf8Bytes.clear();
            addUtf8Bytes(ch[0], utf8Bytes); // TODO only add if not already there

            if (ch[0] == '\'') {
                if (isShortContraction(ch[1])) {
                    // 1) `'[sdtm]` - contractions, such as the suffixes of `he's`, `I'd`, `'tis`, `I'm`
                    addUtf8Bytes(ch[1], utf8Bytes);
                    ch[0] = ch[2];
                    ch[1] = ch[2] = -1;
                    tokenCount += tokenConsumer.apply(utf8Bytes.elements(), 0, utf8Bytes.size());
                    continue;
                } else if (ch[2] > 0) {
                    // 1) `'(?:ll|ve|re)` - contractions, such as the suffixes of `you'll`, `we've`, `they're`
                    if (isLongContraction(ch[1], ch[2])) {
                        addUtf8Bytes(ch[1], utf8Bytes);
                        addUtf8Bytes(ch[2], utf8Bytes);
                        ch[0] = ch[1] = ch[2] = -1;
                        tokenCount += tokenConsumer.apply(utf8Bytes.elements(), 0, utf8Bytes.size());
                        continue;
                    }
                }
            }

            if ((isNotNewlineOrLetterOrNumeric(ch[0]) && isLetter(ch[1])) || isLetter(ch[0])) {
                // 2) `[^\r\n\p{L}\p{N}]?+\p{L}+` - words such as ` of`, `th`, `It`, ` not`
                ch[0] = -1;
                if (isLetter(ch[1])) {
                    addUtf8Bytes(ch[1], utf8Bytes);
                    ch[1] = -1;
                    if (isLetter(ch[2])) {
                        addUtf8Bytes(ch[2], utf8Bytes);
                        ch[2] = -1;

                        while (codePoints.hasNext() && isLetter(ch[0] = codePoints.nextInt())) {
                            addUtf8Bytes(ch[0], utf8Bytes);
                            ch[0] = -1;
                        }
                    } else {
                        ch[0] = ch[2];
                        ch[2] = -1;
                    }
                } else {
                    ch[0] = ch[1];
                    ch[1] = ch[2];
                    ch[2] = -1;
                }
                tokenCount += tokenConsumer.apply(utf8Bytes.elements(), 0, utf8Bytes.size());
            } else if (isNumeric(ch[0])) {
                // 3) `\p{N}{1,3}` - numbers, such as `4`, `235` or `3½`
                ch[0] = -1;
                if (isNumeric(ch[1])) {
                    addUtf8Bytes(ch[1], utf8Bytes);
                    ch[1] = -1;
                    if (isNumeric(ch[2])) {
                        addUtf8Bytes(ch[2], utf8Bytes);
                        ch[2] = -1;
                    } else {
                        ch[0] = ch[2];
                        ch[2] = -1;
                    }
                } else {
                    ch[0] = ch[1];
                    ch[1] = ch[2];
                    ch[2] = -1;
                }
                tokenCount += tokenConsumer.apply(utf8Bytes.elements(), 0, utf8Bytes.size());
            } else if (isNotWhitespaceOrLetterOrNumeric(ch[0]) || ((ch[0] == ' ') && isNotWhitespaceOrLetterOrNumeric(ch[1]))) {
                // 4) ` ?[^\s\p{L}\p{N}]++[\r\n]*` - punctuation, such as `,`, ` .`, `"`
                ch[0] = -1;
                if (isNotWhitespaceOrLetterOrNumeric(ch[1])) {
                    addUtf8Bytes(ch[1], utf8Bytes);
                    ch[1] = -1;
                    if (isNotWhitespaceOrLetterOrNumeric(ch[2])) {
                        addUtf8Bytes(ch[2], utf8Bytes);
                        ch[2] = -1;
                        while (codePoints.hasNext() && isNotWhitespaceOrLetterOrNumeric(ch[0] = codePoints.nextInt())) {
                            addUtf8Bytes(ch[0], utf8Bytes);
                            ch[0] = -1;
                        }
                    } else {
                        ch[0] = ch[2];
                        ch[2] = -1;
                    }
                } else {
                    ch[0] = ch[1];
                    ch[1] = ch[2];
                    ch[2] = -1;
                }
                for (var i = 0; i < ch.length && codePoints.hasNext(); i++) {
                    if (ch[i] < 0) {
                        ch[i] = codePoints.nextInt();
                    }
                }
                if (isNewline(ch[0])) {
                    addUtf8Bytes(ch[0], utf8Bytes);
                    ch[0] = -1;
                    if (isNewline(ch[1])) {
                        addUtf8Bytes(ch[1], utf8Bytes);
                        ch[1] = -1;
                        if (isNewline(ch[2])) {
                            addUtf8Bytes(ch[2], utf8Bytes);
                            ch[2] = -1;
                            while (codePoints.hasNext() && isNewline(ch[0] = codePoints.nextInt())) {
                                addUtf8Bytes(ch[0], utf8Bytes);
                                ch[0] = -1;
                            }
                        } else {
                            ch[0] = ch[2];
                            ch[2] = -1;
                        }
                    } else {
                        ch[0] = ch[1];
                        ch[1] = ch[2];
                        ch[2] = -1;
                    }
                }
                tokenCount += tokenConsumer.apply(utf8Bytes.elements(), 0, utf8Bytes.size());
            } else {
                // 5) `\s*[\r\n]+` - line endings such as `\r\n    \r\n`
                // 6) `\s+(?!\S)` - whitespaces such as `               ` or ` `
                // 7) `\s+` - unmatched remaining spaces, such as ` `
                assert isWhitespace(ch[0]) : "Invalid character: " + Arrays.toString(toChars(ch[0]));
                var lastWhitespace = ch[0];
                ch[0] = -1;
                if (isWhitespace(ch[1])) {
                    addUtf8Bytes(ch[1], utf8Bytes);
                    lastWhitespace = ch[1];
                    ch[1] = -1;
                    if (isWhitespace(ch[2])) {
                        addUtf8Bytes(ch[2], utf8Bytes);
                        lastWhitespace = ch[2];
                        ch[2] = -1;
                        while (codePoints.hasNext() && isWhitespace(ch[0] = codePoints.nextInt())) {
                            addUtf8Bytes(ch[0], utf8Bytes);
                            lastWhitespace = ch[0];
                            ch[0] = -1;
                        }
                    } else {
                        ch[0] = ch[2];
                        ch[2] = -1;
                    }
                } else {
                    ch[0] = ch[1];
                    ch[1] = ch[2];
                    ch[2] = -1;
                }

                var lastNewLineIndex = getSplitIndex(utf8Bytes); // TODO bake into previous loops
                int start = 0;
                if (lastNewLineIndex >= 0) {
                    tokenCount += tokenConsumer.apply(utf8Bytes.elements(), 0, lastNewLineIndex + 1);
                    start = lastNewLineIndex + 1;
                }

                if (lastNewLineIndex < utf8Bytes.size() - 1) {
                    var byteCount = utf8ByteCount(lastWhitespace); // TODO simplify
                    if (ch[0] >= 0 && !isWhitespace(ch[0]) && utf8Bytes.size() > byteCount) {
                        utf8Bytes.size(utf8Bytes.size() - byteCount);
                        ch[1] = ch[0];
                        ch[0] = lastWhitespace;
                    }
                    if (start < utf8Bytes.size()) {
                        tokenCount += tokenConsumer.apply(utf8Bytes.elements(), start, utf8Bytes.size());
                    }
                }
            }
        }
        return tokenCount;
    }

    static boolean isShortContraction(int ch) {
        return SDTM.indexOf(ch) >= 0;
    }

    static boolean isLongContraction(int ch1, int ch2) {
        if (((ch1 == 'l') && (ch2 == 'l'))
                || ((ch1 == 'v') && (ch2 == 'e'))
                || ((ch1 == 'r') && (ch2 == 'e'))) {
            return true;
        } else {
            var lch1 = toUpperCase(ch1);
            var lch2 = toUpperCase(ch2);
            return ((lch1 == 'L') && (lch2 == 'L'))
                    || ((lch1 == 'V') && (lch2 == 'E'))
                    || ((lch1 == 'R') && (lch2 == 'E'));
        }
    }

    private static int getSplitIndex(ByteArrayList utf8Bytes) {
        for (var i = utf8Bytes.size() - 1; i >= 0; i--) {
            if (isNewline(utf8Bytes.getByte(i))) {
                return i;
            }
        }
        return -1;
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

    static boolean isNewline(int ch) {
        return (ch == '\r')
                || (ch == '\n');
    }

    static boolean isNotWhitespaceOrLetterOrNumeric(int ch) {
        if (ch < '0') {
            return ch >= 0 && ch != ' ' && (ch > '\r' || ch < '\t');
        } else {
            return !isLetterOrNumeric(ch) && !isWhitespace(ch);
        }
    }

    static boolean isNotNewlineOrLetterOrNumeric(int ch) {
        if (ch < '0') {
            return ch >= 0 && (ch == ' ' || !isNewline(ch));
        } else {
            return !isLetterOrNumeric(ch);
        }
    }

    static void addUtf8Bytes(int cp, ByteArrayList dst) {
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
        }
    }

    static int utf8ByteCount(int cp) {
        if (cp < 0x80) {
            return 1;
        } else if (cp < 0x800) {
            return 2;
        } else if (cp < MIN_SUPPLEMENTARY_CODE_POINT) {
            return 3;
        } else {
            assert cp < (MAX_CODE_POINT + 1) : "Invalid code point: " + cp;
            return 4;
        }
    }

    @FunctionalInterface
    public interface TokenConsumer {

        int apply(byte[] elements, int start, int end);
    }
}