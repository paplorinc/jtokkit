package com.knuddels.jtokkit.reference;

import com.knuddels.jtokkit.Cl100kParser;
import com.knuddels.jtokkit.EncodingFactory;
import com.knuddels.jtokkit.GptBytePairEncodingOriginal;
import com.knuddels.jtokkit.api.Encoding;
import it.unimi.dsi.fastutil.ints.Int2LongAVLTreeMap;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Cl100kBaseTest {

    private static final Encoding ENCODING = EncodingFactory.cl100kBase();

    public static List<String> getTexts(String prefix) {
        return loadData(prefix, "benchmark/data");
    }

    public static List<String> loadData(String prefix, String folder) {
        try {
            var folderPath = Paths.get(prefix, folder);
            var fileContents = new ArrayList<String>();
            try (var files = Files.walk(folderPath)) {
                files.forEach(file -> {
                    if (Files.isRegularFile(file)) {
                        try {
                            fileContents.add(Files.readString(file, UTF_8));
                        } catch (IOException exception) {
                            throw new RuntimeException("Error while reading file at " + file, exception);
                        }
                    }
                });
            }

            fileContents.addAll(getBasePromptsKeys(prefix));

            return fileContents;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> getBasePromptsKeys(String prefix) throws IOException {
        var result = new ArrayList<String>();
        var csvPath = Paths.get(prefix + "lib/src/test/resources/base_prompts.csv");
        try (var br = Files.newBufferedReader(csvPath, UTF_8)) {
            br.readLine(); // Skip header

            String line;
            while ((line = br.readLine()) != null) {
                var values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                // Add only the first column, handling quoted strings
                result.add(values[0].replaceAll("\"", ""));
            }
        }
        return result;
    }

    static String normalizeStringForTesting(String testString) {
        return testString
                .replaceAll("\\r", "\\\\r")
                .replaceAll("\\n", "\\\\n")
                .replaceAll(" ", "␣");
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    public void cl100kBaseEncodesCorrectly(
            final String input,
            final String output
    ) {
        var expected = TestUtils.parseEncodingString(output);
        var actual = ENCODING.encode(input);

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    public void cl100kBaseEncodesStable(final String input) {
        var actual = ENCODING.decode(ENCODING.encode(input));

        assertEquals(input, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    public void cl100kBaseEncodesCorrectlyWithMaxTokensSet(
            final String input,
            final String output,
            final String outputMaxTokens10
    ) {
        var expected = TestUtils.parseEncodingString(output);
        var expectedWithMaxTokens = TestUtils.parseEncodingString(outputMaxTokens10);
        var encodingResult = ENCODING.encode(input, 10);

        assertEquals(expectedWithMaxTokens, encodingResult.getTokens(), "Encoding result does not match expected value for input: " + input);
        assertEquals(expected.size() > expectedWithMaxTokens.size(), encodingResult.isTruncated());
    }

    @Disabled
    @Test
    public void encodingSpeeds() {
        var input = new StringBuilder();
        var measurements = new Int2LongAVLTreeMap();

        var iterations = 20;
        for (double i = 1.0; i < 3_000; i = Math.max(i + 1, i * 1.01)) {
            while (input.length() < i) {
                input.append("a");
            }
            String inputString = input.toString();

            for (int j = 0; j < 10 * iterations; j++) {
                var warmup = ENCODING.encode(inputString);
                assert !warmup.isEmpty();
            }
            var startTime = System.nanoTime();
            for (int j = 0; j < iterations; j++) {
                var encodingResult = ENCODING.encode(inputString);
                assert !encodingResult.isEmpty();
            }
            var endTime = System.nanoTime();
            measurements.put((int) i, ((endTime - startTime) / iterations));
        }
        measurements.forEach((i, t) -> System.out.println(i + "\t" + t));
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
                actualSplits.add(new String(utf8Bytes.toByteArray(), UTF_8));
                return false;
            });
            assertEquals(expectedSplits, actualSplits);

            // Encoding
            var expectedTokens = bytePairEncodingOriginal.encode(testString);
            var actualTokens = ENCODING.encode(testString);
            assertEquals(expectedTokens, actualTokens.stream().toList());

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
    public void cl100kBaseEncodesStableWithMaxTokensSet(final String input) {
        var actual = ENCODING.decode(ENCODING.encode(input, 10).getTokens());

        assertTrue(input.startsWith(actual));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    public void cl100kBaseEncodeOrdinaryEncodesCorrectly(
            final String input,
            final String output
    ) {
        var expected = TestUtils.parseEncodingString(output);
        var actual = ENCODING.encodeOrdinary(input);

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    public void cl100kBaseEncodeOrdinaryEncodesCorrectly(
            final String input,
            final String output,
            final String outputMaxTokens10
    ) {
        var expected = TestUtils.parseEncodingString(output);
        var expectedWithMaxTokens = TestUtils.parseEncodingString(outputMaxTokens10);
        var encodingResult = ENCODING.encodeOrdinary(input, 10);

        assertEquals(expectedWithMaxTokens, encodingResult.getTokens());
        assertEquals(expected.size() > expectedWithMaxTokens.size(), encodingResult.isTruncated());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    public void cl100kBaseEncodeOrdinaryEncodesStable(final String input) {
        var actual = ENCODING.decode(ENCODING.encodeOrdinary(input));

        assertEquals(input, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    public void cl100kBaseEncodeOrdinaryEncodesStableWithMaxTokensSet(final String input) {
        var actual = ENCODING.decode(ENCODING.encodeOrdinary(input, 10).getTokens());

        assertTrue(input.startsWith(actual));
    }

    @Test
    public void cl100kBaseEncodeOrdinaryEncodesSpecialTokensCorrectly() {
        var input = "Hello<|endoftext|>, <|fim_prefix|> <|fim_middle|> world <|fim_suffix|> ! <|endofprompt|>";
        var tokens = ENCODING.encodeOrdinary(input);
        var actual = ENCODING.decode(tokens);

        assertEquals(input, actual);
    }
}
