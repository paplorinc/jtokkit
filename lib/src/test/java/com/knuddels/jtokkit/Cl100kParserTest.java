package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.CharacterCodingException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import static com.knuddels.jtokkit.Cl100kParser.addUtf8Bytes;
import static com.knuddels.jtokkit.Cl100kParser.isValidUTF8;
import static com.knuddels.jtokkit.EncodingFactory.compileRegex;
import static java.lang.Character.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.*;

public class Cl100kParserTest {
    public static final String PUNCTUATION = "'\".,?!:()";
    private static final String LETTERS = generateUnicodeCategoryString(Cl100kParser::isLetter);
    private static final String NUMBERS = generateUnicodeCategoryString(Cl100kParser::isNumeric);
    private static final String WHITESPACES = generateUnicodeCategoryString(Cl100kParser::isWhitespace);
    private static final String LETTER_OR_NUMERIC = generateUnicodeCategoryString(Cl100kParser::isLetterOrNumeric);
    private static final String NOT_NEWLINE_OR_LETTER_OR_NUMERIC = generateUnicodeCategoryString(Cl100kParser::isNotNewlineOrLetterOrNumeric);
    private static final String NOT_WHITESPACE_OR_LETTER_OR_NUMERIC = generateUnicodeCategoryString(Cl100kParser::isNotWhitespaceOrLetterOrNumeric);
    private static final List<String> SPECIAL = List.of("'s", "'t", "'re", "'ve", "'m", "'ll", "'d", "'ſ", "'x", "🤚🏾", "😩", "　", "½");
    private static final String NEWLINES = "\n\r";

    private static String generateUnicodeCategoryString(IntPredicate characterProperty) {
        return IntStream.range(MIN_CODE_POINT, MAX_CODE_POINT)
                .filter(Character::isDefined)
                .filter(characterProperty)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private static ThreadLocalRandom rand() {
        return ThreadLocalRandom.current();
    }

    public static Map<Integer, String> fetchUnicodeData() {
        var url = "https://www.unicode.org/Public/UCD/latest/ucd/UnicodeData.txt";
        Map<Integer, String> unicodeMap = new HashMap<>();

        try (var br = new BufferedReader(new InputStreamReader(new URL(url).openStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                var parts = line.split(";");
                assert parts.length > 1;
                var codePoint = Integer.parseInt(parts[0], 16);
                var name = parts[1];
                unicodeMap.put(codePoint, name);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return unicodeMap;
    }

    private static String getMessage(String textString, GptBytePairEncodingOriginal originalEncoder, GptBytePairEncoding encoder, int maxTokenCount) {
        var expected = originalEncoder.encode(textString, maxTokenCount).getTokens();
        var actual = encoder.encode(textString, maxTokenCount).getTokens();
        return "`" + textString + "` should have mapped to: " + expected + " for maxTokenCount = " + maxTokenCount + ", but was: " + actual;
    }

    @Test
    void testToUtf8Bytes() {
        var input = "\uD81C\uDFE1";

        var dst = new ByteArrayList();
        addUtf8Bytes(input, 0, input.length(), dst);

        var expectedBytes = input.getBytes(UTF_8);
        var actualBytes = dst.toByteArray();

        assertArrayEquals(expectedBytes, actualBytes, "UTF-8 conversion did not match expected bytes");
        assertEquals(new String(expectedBytes, UTF_8), input);
    }

    @Test
    public void testToUtf8BytesOnFetchedUnicodeData() throws CharacterCodingException {
        fetchUnicodeData().entrySet().stream().parallel().forEach(e -> {
            var expected = Character.toString(e.getKey());
            if (isValidUTF8(expected)) {
                var dst = new ByteArrayList();
                addUtf8Bytes(expected, 0, expected.length(), dst);

                assertArrayEquals(expected.getBytes(UTF_8), dst.toByteArray(), () -> "Expected `" + Arrays.toString(expected.getBytes(UTF_8)) + "` (`" + expected + "`) but was `" + Arrays.toString(dst.toByteArray()) + "`");
            } else {
                System.out.println("Skipping invalid UTF-8: " + e.getValue() + " (" + e.getKey() + ")");
            }
        });
    }

    @Test
    public void testParserAndEncoderWithRandomStrings() {
        var originalEncoder = GptBytePairEncodingOriginal.getEncoder();
        var encoder = (GptBytePairEncoding) EncodingFactory.cl100kBase();

        IntStream.range(0, 10_000).parallel().forEach(i -> {
            var textString = generateValidText(originalEncoder, 20);
            int maxTokenCount = rand().nextInt(1, 2 * textString.length());

            if (i % 1_000 == 0) {
                System.out.print("✓");
            }
            if (i % 10_000 == 0) {
                System.out.println();
            }

            var expected = originalEncoder.pattern.matcher(textString);

            Cl100kParser.split(textString, utf8Bytes -> {
                assertTrue(expected.find(), () -> getMessage(textString, originalEncoder, encoder, maxTokenCount));

                var actual = new String(utf8Bytes.toByteArray(), UTF_8);

                var group = expected.group();
                //assertEquals(normalizeStringForTesting(group), normalizeStringForTesting(actual), () -> getMessage(textString, originalEncoder, encoder, maxTokenCount));
                assertEquals(group, actual, () -> getMessage(textString, originalEncoder, encoder, maxTokenCount));

                return false;
            });
            assertFalse(expected.find(), () -> getMessage(textString, originalEncoder, encoder, maxTokenCount));

            var expectedTokens = originalEncoder.encode(textString, maxTokenCount).getTokens();
            var actualTokens = encoder.encode(textString, maxTokenCount).getTokens();
            assertEquals(expectedTokens, actualTokens, () -> getMessage(textString, originalEncoder, encoder, maxTokenCount));
        });
    }

    private String generateValidText(GptBytePairEncodingOriginal originalEncoder, int maxStringLength) {
        String textString;
        do {
            textString = generateRandomString(maxStringLength);
        } while (!Objects.equals(originalEncoder.decode(originalEncoder.encode(textString)), textString));
        return textString;
    }

    private String generateRandomString(int maxStringLength) {
        var length = rand().nextInt(1, maxStringLength);
        return rand()
                .ints(length, 0, 20)
                .mapToObj(this::getRandomCharFromCategory)
                .map(String::valueOf)
                .map(obj -> rand().nextBoolean() ? obj : (rand().nextBoolean() ? obj.toUpperCase() : obj.toLowerCase()))
                .collect(joining());
    }

    private char[] getRandomCharFromCategory(int category) {
        switch (category) {
            case 0:
                return new char[]{' '};
            case 1:
                return new char[]{' ', ' '};
            case 2:
            case 3:
            case 4:
                return toChars((rand().nextBoolean() ? 'a' : 'A') + rand().nextInt('z' - 'a' + 1));
            case 5:
                return toChars(PUNCTUATION.codePointAt(rand().nextInt(PUNCTUATION.length())));
            case 6:
            case 7:
                return toChars(NEWLINES.codePointAt(rand().nextInt(NEWLINES.length())));
            case 8:
                return toChars(NUMBERS.codePointAt(rand().nextInt(NUMBERS.length())));
            case 9:
                return toChars(WHITESPACES.codePointAt(rand().nextInt(WHITESPACES.length())));
            case 10:
            case 11:
                return toChars(LETTERS.codePointAt(rand().nextInt(LETTERS.length())));
            case 12:
                return toChars(LETTER_OR_NUMERIC.codePointAt(rand().nextInt(LETTER_OR_NUMERIC.length())));
            case 13:
                return toChars(NOT_NEWLINE_OR_LETTER_OR_NUMERIC.codePointAt(rand().nextInt(NOT_NEWLINE_OR_LETTER_OR_NUMERIC.length())));
            case 14:
                return toChars(NOT_WHITESPACE_OR_LETTER_OR_NUMERIC.codePointAt(rand().nextInt(NOT_WHITESPACE_OR_LETTER_OR_NUMERIC.length())));
            case 15:
            case 16:
                return toChars(0x1F600 + rand().nextInt(0x50)); // emojis
            case 17:
                return SPECIAL.get(rand().nextInt(SPECIAL.size())).toCharArray();
            case 18:
            case 19:
                while (true) {
                    var r = rand().nextInt(MIN_CODE_POINT, MAX_CODE_POINT);
                    if (isDefined(r)) {
                        return toChars(r);
                    }
                }
            default:
                throw new IllegalStateException();
        }
    }

    @Test
    public void testIsApostophed() {
        var count = 0;
        var pattern = compileRegex("^(?:'s|'t|'re|'ve|'m|'ll|'d)$", true);

        System.out.println("isShortContraction");
        for (var cp1 = MIN_CODE_POINT; cp1 <= MAX_CODE_POINT; cp1++) { // Seems 'ſ is also a contraction...
            var asString = "'" + Character.toString(cp1);
            var matchesRegex = pattern.matcher(asString).matches();
            var actual = Cl100kParser.isShortContraction(cp1);
            if (matchesRegex) {
                count++;
            }
            assertEquals(matchesRegex, actual, "Mismatch at code point: `" + asString + "` (" + cp1 + ")");
        }

        if (false) { // Takes too long
            System.out.println("isLongContraction");
            for (var cp1 = MIN_CODE_POINT; cp1 <= MAX_CODE_POINT; cp1++) {
                for (var cp2 = MIN_CODE_POINT; cp2 <= MAX_CODE_POINT; cp2++) {
                    var asString = "'" + Character.toString(cp1) + Character.toString(cp2);
                    var matchesRegex = pattern.matcher(asString).matches();
                    var actual = Cl100kParser.isLongContraction(cp1, cp2);
                    if (matchesRegex) {
                        count++;
                    }
                    assertEquals(matchesRegex, actual, "Mismatch at code point: `" + asString + "` (" + cp1 + ", " + cp2 + ")");
                }
            }
            System.out.println(count);
        }
    }

    @Test
    public void testIsNumeric() {
        var count = 0;
        assertFalse(Cl100kParser.isNumeric(-1));
        var pattern = compileRegex("^\\p{N}$", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = Character.toString(cp);
            var matchesRegex = pattern.matcher(charAsString).matches();
            var actual = Cl100kParser.isNumeric(cp);
            if (matchesRegex) {
                count++;
            }

            assertEquals(matchesRegex, actual, "Mismatch at code point: `" + charAsString + "` (" + cp + ")");
        }
        System.out.println(count);
    }

    @Test
    public void testIsLetter() {
        var count = 0;
        assertFalse(Cl100kParser.isLetter(-1));
        var pattern = compileRegex("^\\p{L}$", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = Character.toString(cp);
            var matchesRegex = pattern.matcher(charAsString).matches();
            var actual = Cl100kParser.isLetter(cp);
            if (matchesRegex) {
                count++;
            }
            assertEquals(matchesRegex, actual, "Mismatch at code point: `" + charAsString + "` (" + cp + ")");
        }
        System.out.println(count);
    }

    @Test
    public void testIsUnicodeWhitespace() {
        var count = 0;
        assertFalse(Cl100kParser.isWhitespace(-1));
        var pattern = compileRegex("^\\s$", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = Character.toString(cp);
            var matchesRegex = pattern.matcher(charAsString).matches();
            var actual = Cl100kParser.isWhitespace(cp);
            if (matchesRegex) {
                count++;
            }
            assertEquals(matchesRegex, actual, "Mismatch at code point: `" + charAsString + "` (" + cp + ")");
        }
        System.out.println(count);
    }

    @Test
    public void testIsLetterOrNumeric() {
        var count = 0;
        assertFalse(Cl100kParser.isLetterOrNumeric(-1));
        var pattern = compileRegex("^[\\p{L}\\p{N}]$", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = Character.toString(cp);
            var matchesRegex = pattern.matcher(charAsString).matches();
            var actual = Cl100kParser.isLetterOrNumeric(cp);
            if (matchesRegex) {
                count++;
            }
            assertEquals(matchesRegex, actual, "Mismatch at code point: `" + charAsString + "` (" + cp + ")");
        }
        System.out.println(count);
    }

    @Test
    public void testIsNotWhitespaceOrLetterOrNumeric() {
        var count = 0;
        assertFalse(Cl100kParser.isNotWhitespaceOrLetterOrNumeric(-1));
        var pattern = compileRegex("^[^\\s\\p{L}\\p{N}]$", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = Character.toString(cp);
            var matchesRegex = pattern.matcher(charAsString).matches();
            var actual = Cl100kParser.isNotWhitespaceOrLetterOrNumeric(cp);
            if (matchesRegex) {
                count++;
            }
            assertEquals(matchesRegex, actual, "Mismatch at code point: `" + charAsString + "` (" + cp + ")");
        }
        System.out.println(count);
    }

    @Test
    public void testIsNotNewlineOrLetterOrNumeric() {
        var count = 0;
        assertFalse(Cl100kParser.isNotNewlineOrLetterOrNumeric(-1));
        var pattern = compileRegex("^[^\r\n\\p{L}\\p{N}]$", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = Character.toString(cp);
            var matchesRegex = pattern.matcher(charAsString).matches();
            var actual = Cl100kParser.isNotNewlineOrLetterOrNumeric(cp);
            if (matchesRegex) {
                count++;
            }
            assertEquals(matchesRegex, actual, "Mismatch at code point: `" + charAsString + "` (" + cp + ")");
        }
        System.out.println(count);
    }

    @Test
    public void testIsNewline() {
        var count = 0;
        assertFalse(Cl100kParser.isNewline(-1));
        var pattern = compileRegex("^[\r\n]$", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = Character.toString(cp);
            var matchesRegex = pattern.matcher(charAsString).matches();
            var isNewline = Cl100kParser.isNewline(cp);
            if (matchesRegex) {
                count++;
            }
            assertEquals(matchesRegex, isNewline, "Mismatch at code point: `" + charAsString + "` (" + cp + ")");
        }
        System.out.println(count);
    }
}