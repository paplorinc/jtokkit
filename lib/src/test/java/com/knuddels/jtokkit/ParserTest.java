package com.knuddels.jtokkit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import static com.knuddels.jtokkit.EncodingFactory.compileRegex;
import static com.knuddels.jtokkit.EncodingFactoryTest.normalizeStringForTesting;
import static java.lang.Character.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParserTest {
    public static final String PUNCTUATION = "'\".,?!:()";
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

    @Test
    public void testParserWithRandomStrings() {
        // TODO [188, 172, 99, 96] fails!
        var originalEncoder = GptBytePairEncodingOriginal.getEncoder();
        var encoder = (GptBytePairEncoding) EncodingFactory.cl100kBase();

        IntStream.range(0, 1_000_000).parallel().forEach(i -> {
            var textString = generateRandomString();
            var originalEncoded = originalEncoder.encode(textString);
            if (originalEncoder.decode(originalEncoded).equals(textString)) { // valid encoding
                System.out.print("✓");
                if (i % 100 == 0) {
                    System.out.println();
                }

                var expected = originalEncoder.pattern.matcher(textString);
                var originalEncodedSpliterator = originalEncoded.spliterator();

                var codepoints = textString.codePoints().toArray();
                Parser.split(codepoints, (start, end) -> {
                    assertTrue(expected.find());

                    var actual = new String(codepoints, start, end - start);

                    assertEquals(normalizeStringForTesting(expected.group()), normalizeStringForTesting(actual), "`" + textString + "`");
                    assertEquals(expected.group(), actual, "`" + textString + "`");

                    var actualEncoded = encoder.encode(actual).primitiveStream().boxed().collect(toList());
                    assertEquals(stream(originalEncodedSpliterator, false).limit(actualEncoded.size()).collect(toList()), actualEncoded, "`" + textString + "`");
                    return false;
                });
            }
        });
    }

    private String generateRandomString() {
        var length = rand().nextInt(1, 10);
        return rand().ints(length, 0, 15)
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
                return Character.toChars((rand().nextBoolean() ? 'a' : 'A') + rand().nextInt('z' - 'a'));
            case 5:
                return new char[]{PUNCTUATION.charAt(rand().nextInt(PUNCTUATION.length()))};
            case 6:
                return new char[]{NEWLINES.charAt(rand().nextInt(NEWLINES.length()))};
            case 7:
                return new char[]{NUMBERS.charAt(rand().nextInt(NUMBERS.length()))};
            case 8:
                return new char[]{WHITESPACES.charAt(rand().nextInt(WHITESPACES.length()))};
            case 9:
            case 10:
                return new char[]{LETTERS.charAt(rand().nextInt(LETTERS.length()))};
            case 11:
                return new char[]{LETTER_OR_NUMERIC.charAt(rand().nextInt(LETTER_OR_NUMERIC.length()))};
            case 12:
                return new char[]{NEWLINE_OR_LETTER_OR_NUMERIC.charAt(rand().nextInt(NEWLINE_OR_LETTER_OR_NUMERIC.length()))};
            case 13:
                return new char[]{WHITESPACE_OR_LETTER_OR_NUMERIC.charAt(rand().nextInt(WHITESPACE_OR_LETTER_OR_NUMERIC.length()))};
            default:
                while (true) {
                    int r = rand().nextInt(MIN_CODE_POINT, MAX_CODE_POINT);
                    if (isValid(r)) {
                        return Character.toChars(r);
                    }
                }
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