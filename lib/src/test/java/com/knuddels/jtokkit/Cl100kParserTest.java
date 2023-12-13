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

import static com.knuddels.jtokkit.EncodingFactory.compileRegex;
import static com.knuddels.jtokkit.EncodingFactoryTest.normalizeStringForTesting;
import static com.knuddels.jtokkit.Cl100kParser.isValidUTF8;
import static com.knuddels.jtokkit.Cl100kParser.toUtf8Bytes;
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
    private static final String NEWLINE_OR_LETTER_OR_NUMERIC = generateUnicodeCategoryString(Cl100kParser::isNewlineOrLetterOrNumeric);
    private static final String WHITESPACE_OR_LETTER_OR_NUMERIC = generateUnicodeCategoryString(Cl100kParser::isWhitespaceOrLetterOrNumeric);
    private static final String NEWLINES = "\n\r";

    private static String generateUnicodeCategoryString(IntPredicate characterProperty) {
        return IntStream.range(MIN_CODE_POINT, MAX_CODE_POINT)
                .filter(Cl100kParserTest::validTestCodePoint)
                .filter(characterProperty)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private static boolean validTestCodePoint(int c) {
        return isValidCodePoint(c) && isDefined(c);
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
            throw new IllegalStateException();
        }
        return unicodeMap;
    }

    private static String getMessage(String textString, GptBytePairEncodingOriginal originalEncoder, GptBytePairEncoding encoder) {
        return "`" + textString + "` should have mapped to: " + originalEncoder.encode(textString) + ", but was: " + encoder.encode(textString);
    }

    @Test
    void testToUtf8Bytes() {
        var input = "\uD81C\uDFE1";

        var dst = new ByteArrayList();
        toUtf8Bytes(input, 0, input.length(), dst);

        var expectedBytes = input.getBytes(UTF_8);
        var actualBytes = dst.toByteArray();

        assertArrayEquals(expectedBytes, actualBytes, "UTF-8 conversion did not match expected bytes");
        assertEquals(new String(expectedBytes, UTF_8), input);
    }

    @Test
    public void testToUtf8BytesOnFetchedUnicodeData() throws CharacterCodingException {
        var byteArrayList = new ByteArrayList();
        fetchUnicodeData().forEach((codepoint, name) -> {
            var expected = new String(toChars(codepoint));
            if (isValidUTF8(expected)) {
                toUtf8Bytes(expected, 0, expected.length(), byteArrayList);

                assertArrayEquals(expected.getBytes(UTF_8), byteArrayList.toByteArray(), () -> "Expected `" + Arrays.toString(expected.getBytes(UTF_8)) + "` (`" + expected + "`) but was `" + Arrays.toString(byteArrayList.toByteArray()) + "`");
            } else {
                System.out.println("Skipping invalid UTF-8: " + name + " (" + codepoint + ")");
            }
        });
    }

    @Test
    public void testParserAndEncoderWithRandomStrings() {
        var originalEncoder = GptBytePairEncodingOriginal.getEncoder();
        var encoder = (GptBytePairEncoding) EncodingFactory.cl100kBase();

        IntStream.range(0, 100_000).parallel().forEach(i -> {
            var textString = generateValidText(originalEncoder, 100);

            if (i % 1_000 == 0) {
                System.out.print("✓");
            }
            if (i % 10_000 == 0) {
                System.out.println();
            }

            var expected = originalEncoder.pattern.matcher(textString);

            var actualEncoded = new ArrayList<>();
            for (ByteArrayList utf8Bytes : Cl100kParser.split(textString)) {
                assertTrue(expected.find(), () -> getMessage(textString, originalEncoder, encoder));

                var actual = new String(utf8Bytes.toByteArray(), UTF_8);

                var group = expected.group();
                assertEquals(normalizeStringForTesting(group), normalizeStringForTesting(actual), () -> getMessage(textString, originalEncoder, encoder));
                assertEquals(group, actual, () -> getMessage(textString, originalEncoder, encoder));

                actualEncoded.addAll(encoder.encode(actual));
            }
            assertFalse(expected.find(), () -> getMessage(textString, originalEncoder, encoder));

            assertEquals(originalEncoder.encode(textString), actualEncoded, () -> getMessage(textString, originalEncoder, encoder));
        });
    }

    private String generateValidText(GptBytePairEncodingOriginal originalEncoder, int stringLength) {
        String textString;
        do {
            textString = generateRandomString(stringLength);
        } while (!Objects.equals(originalEncoder.decode(originalEncoder.encode(textString)), textString));
        return textString;
    }

    private String generateRandomString(int stringLength) {
        var length = rand().nextInt(1, stringLength);
        return rand()
                .ints(length, 0, 20)
                .mapToObj(this::getRandomCharFromCategory)
                .map(String::valueOf)
                .map(obj -> rand().nextBoolean() ? obj.toUpperCase() : obj.toLowerCase())
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
                return toChars((rand().nextBoolean() ? 'a' : 'A') + rand().nextInt('z' - 'a'));
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
                return toChars(NEWLINE_OR_LETTER_OR_NUMERIC.codePointAt(rand().nextInt(NEWLINE_OR_LETTER_OR_NUMERIC.length())));
            case 14:
                return toChars(WHITESPACE_OR_LETTER_OR_NUMERIC.codePointAt(rand().nextInt(WHITESPACE_OR_LETTER_OR_NUMERIC.length())));
            case 15:
            case 16:
                return toChars(0x1F600 + rand().nextInt(0x50)); // emojis
            case 17:
            case 18:
            case 19:
                while (true) {
                    var r = rand().nextInt(MIN_CODE_POINT, MAX_CODE_POINT);
                    if (validTestCodePoint(r)) {
                        return toChars(r);
                    }
                }
            default:
                throw new IllegalStateException();
        }
    }

    @Test
    public void testIsNumeric() {
        assertFalse(Cl100kParser.isNumeric(-1));
        var pattern = compileRegex("^\\p{N}$", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = new String(toChars(cp));
            var matchesRegex = pattern.matcher(charAsString).matches();
            var isNumeric = Cl100kParser.isNumeric(cp);

            assertEquals(matchesRegex, isNumeric, "Mismatch at code point: " + cp);
        }
    }

    @Test
    public void testIsLetter() {
        assertFalse(Cl100kParser.isLetter(-1));
        var pattern = compileRegex("^\\p{L}$", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = new String(toChars(cp));
            var matchesRegex = pattern.matcher(charAsString).matches();
            var isLetter = Cl100kParser.isLetter(cp);

            assertEquals(matchesRegex, isLetter, "Mismatch at code point: " + cp);
        }
    }

    @Test
    public void testIsUnicodeWhitespace() {
        assertFalse(Cl100kParser.isWhitespace(-1));
        var pattern = compileRegex("^\\s$", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = new String(toChars(cp));
            var matchesRegex = pattern.matcher(charAsString).matches();
            var isWhitespace = Cl100kParser.isWhitespace(cp);

            assertEquals(matchesRegex, isWhitespace, "Mismatch at code point: " + cp);
        }
    }

    @Test
    public void testIsLetterOrNumeric() {
        assertFalse(Cl100kParser.isLetterOrNumeric(-1));
        var pattern = compileRegex("^[\\p{L}\\p{N}]$", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = new String(toChars(cp));
            var matchesRegex = pattern.matcher(charAsString).matches();
            var isLetterOrNumeric = Cl100kParser.isLetterOrNumeric(cp);

            assertEquals(matchesRegex, isLetterOrNumeric, "Mismatch at code point: " + cp);
        }
    }

    @Test
    public void testIsWhitespaceLetterOrNumeric() {
        assertFalse(Cl100kParser.isWhitespaceOrLetterOrNumeric(-1));
        var pattern = compileRegex("^[\\s\\p{L}\\p{N}]$", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = new String(toChars(cp));
            var matchesRegex = pattern.matcher(charAsString).matches();
            var isNewline = Cl100kParser.isWhitespaceOrLetterOrNumeric(cp);

            assertEquals(matchesRegex, isNewline, "Mismatch at code point: " + cp);
        }
    }

    @Test
    public void testIsNewlineOrLetterOrNumeric() {
        assertFalse(Cl100kParser.isNewlineOrLetterOrNumeric(-1));
        var pattern = compileRegex("^[\r\n\\p{L}\\p{N}]$", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = new String(toChars(cp));
            var matchesRegex = pattern.matcher(charAsString).matches();
            var isNewline = Cl100kParser.isNewlineOrLetterOrNumeric(cp);

            assertEquals(matchesRegex, isNewline, "Mismatch at code point: " + cp);
        }
    }

    @Test
    public void testIsNewline() {
        assertFalse(Cl100kParser.isNewline(-1));
        var pattern = compileRegex("^[\r\n]$", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = new String(toChars(cp));
            var matchesRegex = pattern.matcher(charAsString).matches();
            var isNewline = Cl100kParser.isNewline(cp);

            assertEquals(matchesRegex, isNewline, "Mismatch at code point: " + cp);
        }
    }
}