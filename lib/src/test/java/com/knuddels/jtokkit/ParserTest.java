package com.knuddels.jtokkit;

import com.knuddels.jtokkit.reference.GptBytePairEncodingOriginal;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static com.knuddels.jtokkit.EncodingFactory.compileRegex;
import static com.knuddels.jtokkit.EncodingFactoryTest.normalizeStringForTesting;
import static java.lang.Character.*;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParserTest {
    private static final String LETTERS = generateUnicodeCategoryString(Parser::isLetter);
    private static final String NUMBERS = generateUnicodeCategoryString(Parser::isNumeric);
    private static final String WHITESPACES = generateUnicodeCategoryString(Parser::isWhitespace);
    private static final String LETTER_OR_NUMERIC = generateUnicodeCategoryString(Parser::isLetterOrNumeric);
    private static final String NEWLINE_OR_LETTER_OR_NUMERIC = generateUnicodeCategoryString(Parser::isNewlineOrLetterOrNumeric);
    private static final String WHITESPACE_OR_LETTER_OR_NUMERIC = generateUnicodeCategoryString(Parser::isWhitespaceOrLetterOrNumeric);
    private static final String NEWLINES = "\n\r";

    private static String generateUnicodeCategoryString(IntPredicate characterProperty) {
        return IntStream.range(MIN_CODE_POINT, MAX_CODE_POINT)
                .filter(ParserTest::isValid)
                .filter(characterProperty)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private static boolean isValid(int c) {
        return isValidCodePoint(c) && isDefined(c) && !isSurrogate((char) c);
    }

    private static ThreadLocalRandom rand() {
        return ThreadLocalRandom.current();
    }

    public static GptBytePairEncodingOriginal getOriginalEncoder() {
        var originalRegex = "(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}{1,3}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+";
        var regex = Pattern.compile(originalRegex, Pattern.UNICODE_CHARACTER_CLASS);

        var encoder = EncodingFactory.loadMergeableRanks("/com/knuddels/jtokkit/cl100k_base.tiktoken");
        return new GptBytePairEncodingOriginal("cl100k_base", regex, encoder, EncodingFactory.SPECIAL_TOKENS_CL100K_BASE);
    }

    @Test
    public void testParserWithRandomStrings() {
        var originalEncoder = getOriginalEncoder();
        var encoder = (GptBytePairEncoding) EncodingFactory.cl100kBase();

        for (var i = 0; i < 10_000; i++) {
            var textString = generateRandomString();

            var originalEncoded = originalEncoder.encode(textString);
            if (!originalEncoder.decode(originalEncoded).equals(textString)) { // invalid encoding
                i--;
                continue;
            }
            System.out.print("✓");
            if (i % 100 == 0) {
                System.out.println();
            }

            var expected = originalEncoder.pattern.matcher(textString);
            var originalEncodedStream = originalEncoded.stream();

            var chars = textString.toCharArray();
            Parser.split(chars, (start, end) -> {
                assertTrue(expected.find());

                var actual = new String(chars, start, end - start);

                assertEquals(normalizeStringForTesting(expected.group()), normalizeStringForTesting(actual), textString);
                assertEquals(expected.group(), actual, textString);

//                var actualEncoded = encoder.encode(actual).primitiveStream().boxed().collect(toList());
//                assertEquals(originalEncodedStream.limit(actualEncoded.size()).collect(toList()), actualEncoded);
                return false;
            });
        }
    }

    private String generateRandomString() {
        var length = rand().nextInt(1, 10);
        return rand().ints(length, 0, 10)
                .mapToObj(this::getRandomCharFromCategory)
                .collect(joining());
    }

    private String getRandomCharFromCategory(int category) {
        switch (category) {
            case 0:
                return String.valueOf(LETTERS.charAt(rand().nextInt(LETTERS.length())));
            case 1:
                return String.valueOf(NUMBERS.charAt(rand().nextInt(NUMBERS.length())));
            case 2:
                return String.valueOf(WHITESPACES.charAt(rand().nextInt(WHITESPACES.length())));
            case 3:
                return String.valueOf(NEWLINES.charAt(rand().nextInt(NEWLINES.length())));
            case 4:
                return String.valueOf(LETTER_OR_NUMERIC.charAt(rand().nextInt(LETTER_OR_NUMERIC.length())));
            case 5:
                return String.valueOf(NEWLINE_OR_LETTER_OR_NUMERIC.charAt(rand().nextInt(NEWLINE_OR_LETTER_OR_NUMERIC.length())));
            case 6:
                return String.valueOf(WHITESPACE_OR_LETTER_OR_NUMERIC.charAt(rand().nextInt(WHITESPACE_OR_LETTER_OR_NUMERIC.length())));
            default:
                int r;
                do {
                    r = rand().nextInt(MIN_CODE_POINT, MAX_CODE_POINT);
                } while (!isValid(r));
                return String.valueOf(Character.toChars(r));
        }
    }

    @Test
    public void testIsNumeric() {
        var numericPattern = compileRegex("\\p{N}", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = new String(toChars(cp));
            var matchesRegex = numericPattern.matcher(charAsString).matches();
            var isNumeric = Parser.isNumeric(cp);

            assertEquals(matchesRegex, isNumeric, "Mismatch at code point: " + cp);
        }
    }

    @Test
    public void testIsLetter() {
        var letterOrNumericPattern = compileRegex("\\p{L}", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = new String(toChars(cp));
            var matchesRegex = letterOrNumericPattern.matcher(charAsString).matches();
            var isLetterOrNumeric = Parser.isLetter(cp);

            assertEquals(matchesRegex, isLetterOrNumeric, "Mismatch at code point: " + cp);
        }
    }

    @Test
    public void testIsUnicodeWhitespace() {
        var whitespacePattern = compileRegex("\\s", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = new String(toChars(cp));
            var matchesRegex = whitespacePattern.matcher(charAsString).matches();
            var isWhitespace = Parser.isWhitespace(cp);

            assertEquals(matchesRegex, isWhitespace, "Mismatch at code point: " + cp);
        }
    }

    @Test
    public void testIsLetterOrNumeric() {
        var letterOrNumericPattern = compileRegex("[\\p{L}\\p{N}]", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = new String(toChars(cp));
            var matchesRegex = letterOrNumericPattern.matcher(charAsString).matches();
            var isLetterOrNumeric = Parser.isLetterOrNumeric(cp);

            assertEquals(matchesRegex, isLetterOrNumeric, "Mismatch at code point: " + cp);
        }
    }

    @Test
    public void testIsWhitespaceLetterOrNumeric() {
        var letterOrNumericPattern = compileRegex("[\\s\\p{L}\\p{N}]", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = new String(toChars(cp));
            var matchesRegex = letterOrNumericPattern.matcher(charAsString).matches();
            var isNewline = Parser.isWhitespaceOrLetterOrNumeric(cp);

            assertEquals(matchesRegex, isNewline, "Mismatch at code point: " + cp);
        }
    }

    @Test
    public void testIsNewlineOrLetterOrNumeric() {
        var letterOrNumericPattern = compileRegex("[\r\n\\p{L}\\p{N}]", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = new String(toChars(cp));
            var matchesRegex = letterOrNumericPattern.matcher(charAsString).matches();
            var isNewline = Parser.isNewlineOrLetterOrNumeric(cp);

            assertEquals(matchesRegex, isNewline, "Mismatch at code point: " + cp);
        }
    }

    @Test
    public void testIsNewline() {
        var letterOrNumericPattern = compileRegex("[\r\n]", true);
        for (var cp = MIN_CODE_POINT; cp <= MAX_CODE_POINT; cp++) {
            var charAsString = new String(toChars(cp));
            var matchesRegex = letterOrNumericPattern.matcher(charAsString).matches();
            var isNewline = Parser.isNewline(cp);

            assertEquals(matchesRegex, isNewline, "Mismatch at code point: " + cp);
        }
    }
}