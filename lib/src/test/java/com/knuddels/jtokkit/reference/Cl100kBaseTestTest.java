package com.knuddels.jtokkit.reference;

import com.knuddels.jtokkit.EncodingFactory;
import com.knuddels.jtokkit.GptBytePairEncodingOriginal;
import com.knuddels.jtokkit.api.Encoding;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Cl100kBaseTestTest {

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

    @Test
    void snowman() {
        var original = "Unicode snowman: ☃️";
        var encodingResult = ENCODING.encode(original, 10);
        assertEquals(IntArrayList.of(35020, 12056, 1543, 25, 26182, 225, 31643), encodingResult.getTokens());

        var decoded = ENCODING.decode(encodingResult.getTokens());
        assertTrue(original.startsWith(decoded), decoded);
    }

    @Test
    void mixed() {
        var original = "Mixed script: 你好 world! \uD83C\uDF0D";
        var encodingResult = ENCODING.encode(original, 10);
        assertEquals(IntArrayList.of(87533, 5429, 25, 220, 57668, 53901, 1917, 0), encodingResult.getTokens());

        var decoded = ENCODING.decode(encodingResult.getTokens());
        assertTrue(original.startsWith(decoded), decoded);
    }

    @Test
    void mixed2() {
        var original = "مرحبا بالعالم! كيف حالك؟ \uD83D\uDE0E";
        var encodingResult = ENCODING.encode(original, 10);
        assertEquals(IntArrayList.of(10386, 11318, 30925, 22071, 5821, 28946, 32482, 24102, 32482, 10386), encodingResult.getTokens());

        var decoded = ENCODING.decode(encodingResult.getTokens());
        assertTrue(original.startsWith(decoded), decoded);
    }

    @Disabled // TODO
    @Test
    void roundtrip() {
        var original = GptBytePairEncodingOriginal.getEncoder();
        var input = List.of(188, 172, 105, 246, 236, 172, 96, 107);
        var text = original.decode(input);
        System.out.println(text);

//        var encodingResult = original.encode(text);
        var encodingResult = ENCODING.encode(text);
        assertEquals(new IntArrayList(input), encodingResult);

        var roundtrip = ENCODING.decode(new IntArrayList(encodingResult));
        assertEquals(text, roundtrip);
    }

    @Test
    void roundtrip2() {
        var text = "Many words map to one token, but some don't: indivisible.\n" +
                "\n" +
                "Unicode characters like emojis may be split into many tokens containing the underlying bytes: \uD83E\uDD1A\uD83C\uDFFE\n" +
                "\n" +
                "Sequences of characters commonly found next to each other may be grouped together: 1234567890";
        assertEquals(252, text.length());
        assertEquals(57, GptBytePairEncodingOriginal.getEncoder().encode(text).size());
        assertEquals(57, ENCODING.encode(text).size());
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
