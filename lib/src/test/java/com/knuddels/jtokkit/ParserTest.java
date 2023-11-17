package com.knuddels.jtokkit;

import org.junit.jupiter.api.Test;

import static com.knuddels.jtokkit.EncodingFactory.compileRegex;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParserTest {

    @Test
    public void testIsUnicodeWhitespace() {
        var whitespacePattern = compileRegex("\\s", true);
        for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
            var charAsString = new String(Character.toChars(cp));
            var matchesRegex = whitespacePattern.matcher(charAsString).matches();
            var isWhitespace = Parser.isUnicodeWhitespace(cp);

            assertEquals(matchesRegex, isWhitespace, "Mismatch at code point: " + cp);
        }
    }

    @Test
    public void testIsNumeric() {
        var numericPattern = compileRegex("\\p{N}", true);
        for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
            var charAsString = new String(Character.toChars(cp));
            var matchesRegex = numericPattern.matcher(charAsString).matches();
            var isNumeric = Parser.isNumeric(cp);

            assertEquals(matchesRegex, isNumeric, "Mismatch at code point: " + cp);
        }
    }

    @Test
    public void testIsLetterOrNumeric() {
        var letterOrNumericPattern = compileRegex("[\\p{L}\\p{N}]", true);
        for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
            var charAsString = new String(Character.toChars(cp));
            var matchesRegex = letterOrNumericPattern.matcher(charAsString).matches();
            var isLetterOrNumeric = Parser.isLetterOrNumeric(cp);

            assertEquals(matchesRegex, isLetterOrNumeric, "Mismatch at code point: " + cp);
        }
    }

    @Test
    public void testIsNewline() {
        var letterOrNumericPattern = compileRegex("[\r\n]", true);
        for (var cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
            var charAsString = new String(Character.toChars(cp));
            var matchesRegex = letterOrNumericPattern.matcher(charAsString).matches();
            var isNewline = Parser.isNewline(cp);

            assertEquals(matchesRegex, isNewline, "Mismatch at code point: " + cp);
        }
    }
}