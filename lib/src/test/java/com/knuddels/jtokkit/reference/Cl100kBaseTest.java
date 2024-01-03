package com.knuddels.jtokkit.reference;

import com.knuddels.jtokkit.Cl100kParser;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.GptBytePairEncodingOriginal;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static com.knuddels.jtokkit.Cl100kTest.normalizeStringForTesting;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Cl100kBaseTest {

    private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    Encoding getEncoding() {
        return ENCODING;
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void cl100kBaseEncodesCorrectly(
            String input,
            String output
    ) {
        var expected = TestUtils.parseEncodingString(output);
        var actual = getEncoding().encode(input);

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void cl100kBaseEncodesStable(String input) {
        var actual = getEncoding().decode(getEncoding().encode(input));

        assertEquals(input, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void cl100kBaseEncodesCorrectlyWithMaxTokensSet(
            String input,
            String output,
            String outputMaxTokens10
    ) {
        var expected = TestUtils.parseEncodingString(output);
        var expectedWithMaxTokens = TestUtils.parseEncodingString(outputMaxTokens10);
        var encodingResult = getEncoding().encode(input, 10);

        assertEquals(expectedWithMaxTokens, encodingResult.getTokens(), "Encoding result does not match expected value for input: " + input);
        assertEquals(expected.size() > expectedWithMaxTokens.size(), encodingResult.isTruncated());
    }

    @Test
    void testEdgeCases() throws Exception {
        var testStrings = List.of(
                "\n",
                " ",
                "a : b",
                "  a",
                "\n \n ",
                "\n \n",
                "\n ",
                "\n \n!",
                "\n \n   ",
                "\n  !",
                "\n A",
                "  \n\r  \r\n  \r \n  A\nA \n A",
                ",\n\n",
                " ***\n\n\n\n",

                "   !",
                "   A",
                "   0",
                "   *",

                "   \n!",
                "   \nA",
                "   \n0",
                "   \n*",

                "   \n !",
                "   \n A",
                "   \n 0",
                "   \n *",

                "Many words map to one token, but some don't: indivisible.\n\nUnicode characters like emojis may be split into many tokens containing the underlying bytes: \uD83E\uDD1A\uD83C\uDFFE\n\nSequences of characters commonly found next to each other may be grouped together: 1234567890",
                "I paid $123,456 to 9876543210 people!",
                "Mixed script: 你好 world! \uD83C\uDF0D",
                "Unicode snowman: ☃️",
                "I'm:  0\n",
                "We'll meet at 3 o'clock.",
                "Hello, world! It's a beautiful day...",
                "In 2023, I'll be 25 years old.",
                "Hello \n\n World  !",
                " It's 2:30pm;\n\n\n\nlet's eat, sleep , and code!",
                "'Thank God, here it is.' But when we took up the trunk...",
                "user@example.com",
                "this is a 'quoted' word",
                "　　a",
                "'ſ",
                "'ſ\uD84F\uDDB8\uD84C\uDD2CƘ淚",
                "\uD83D\uDE29\n",
                "03½",
                "* \u05E2",
                "مرحبا بالعالم! كيف حالك؟ \uD83D\uDE0E",
                "\u0000\uD81C\uDFE1 a\u0000b-\u0000\u0000\u0000 \u0000",
                "\uD83C\uDF0D a",
                "(\uD856\uDDD9h",
                ",   \uD880\uDE44",
                "  \uDB96\uDE10)",
                "ﮀ\n ",
                "\uD83D\uDE10\uD86B\uDDABX",
                "෫\uD838\uDD44",
                "\uD871\uDD79\n  ",
                " \uD83D\uDE08b\n\uD844\uDDAE'ſ\uD84F\uDDB8\uD84C\uDD2CƘ淚",
                "\uD81E\uDF7E  \uDBAE\uDD79\n\uD875\uDDB0蛇"
        );

        var bytePairEncodingOriginal = GptBytePairEncodingOriginal.getEncoder();
        var originalPattern = bytePairEncodingOriginal.pattern;
        IntStream.range(0, testStrings.size()).forEachOrdered(i -> {
            String testString = testStrings.get(i);
            System.out.println("Validating `" + normalizeStringForTesting(testString) + "`");

            // Splits
            List<String> expectedSplits = matches(testString, originalPattern);

            List<String> actualSplits = new ArrayList<>();
            Cl100kParser.split(testString, utf8Bytes -> {
                assert !utf8Bytes.isEmpty();
                actualSplits.add(new String(utf8Bytes.toArray(), UTF_8));
                return false;
            });
            assertEquals(expectedSplits, actualSplits);

            // Encoding
            var expectedTokens = bytePairEncodingOriginal.encode(testString);
            var actualTokens = ENCODING.encode(testString);
            assertEquals(expectedTokens, actualTokens.boxed());

            // Decoding
            var decoded = ENCODING.decode(actualTokens);
            assertTrue(testString.startsWith(decoded), decoded);
        });
    }

    private List<String> matches(String input, Pattern pattern) {
        List<String> tokens = new ArrayList<>();
        for (Matcher matcher = pattern.matcher(input); matcher.find(); ) {
            var group = matcher.group();
            tokens.add(group);
        }
        return tokens;
    }

    @Test
    void roundtrip3() {
        var text = "A wizard in a top hat riding a unicycle while juggling flaming pineapples";
        assertEquals(73, text.length());
        assertEquals(GptBytePairEncodingOriginal.getEncoder().encode(text).size(), ENCODING.encode(text).size());
    }

    @Test
    void fullMatch() {
        var encodingResult = ENCODING.encode("I'm:  0\n");
        assertEquals(7, encodingResult.size());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void cl100kBaseEncodesStableWithMaxTokensSet(String input) {
        var actual = getEncoding().decode(getEncoding().encode(input, 10).getTokens());

        assertTrue(input.startsWith(actual));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void cl100kBaseEncodeOrdinaryEncodesCorrectly(
            String input,
            String output
    ) {
        var expected = TestUtils.parseEncodingString(output);
        var actual = getEncoding().encodeOrdinary(input);

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void cl100kBaseEncodeOrdinaryEncodesCorrectly(
            String input,
            String output,
            String outputMaxTokens10
    ) {
        var expected = TestUtils.parseEncodingString(output);
        var expectedWithMaxTokens = TestUtils.parseEncodingString(outputMaxTokens10);
        var encodingResult = getEncoding().encodeOrdinary(input, 10);

        assertEquals(expectedWithMaxTokens, encodingResult.getTokens());
        assertEquals(expected.size() > expectedWithMaxTokens.size(), encodingResult.isTruncated());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void cl100kBaseEncodeOrdinaryEncodesStable(String input) {
        var actual = getEncoding().decode(getEncoding().encodeOrdinary(input));

        assertEquals(input, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void cl100kBaseEncodeOrdinaryEncodesStableWithMaxTokensSet(String input) {
        var actual = getEncoding().decode(getEncoding().encodeOrdinary(input, 10).getTokens());

        assertTrue(input.startsWith(actual));
    }

    @Test
    void cl100kBaseEncodeOrdinaryEncodesSpecialTokensCorrectly() {
        var input = "Hello<|endoftext|>, <|fim_prefix|> <|fim_middle|> world <|fim_suffix|> ! <|endofprompt|>";
        var actual = getEncoding().decode(getEncoding().encodeOrdinary(input));

        assertEquals(input, actual);
    }
}
