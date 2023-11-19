package com.knuddels.jtokkit.reference;

import com.knuddels.jtokkit.EncodingFactory;
import com.knuddels.jtokkit.api.Encoding;
import org.eclipse.collections.api.factory.primitive.IntLists;
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

    public static final List<String> TEXTS = loadData("../benchmark/data");
    private static final Encoding ENCODING = EncodingFactory.cl100kBase();

    public static List<String> loadData(final String folder) {
        try {
            var folderPath = Paths.get(folder);
            var fileContents = new ArrayList<String>();
            try (var files = Files.walk(folderPath)) {
                files.forEach(file -> {
                    if (Files.isRegularFile(file) && file.toString().endsWith(".txt")) {
                        try {
                            fileContents.add(Files.readString(file, UTF_8));
                        } catch (IOException exception) {
                            throw new RuntimeException("Error while reading file at " + file, exception);
                        }
                    }
                });
            }

            fileContents.addAll(getBasePromptsKeys());

            return fileContents;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> getBasePromptsKeys() throws IOException {
        var result = new ArrayList<String>();
        var csvPath = Paths.get("../lib/src/test/resources/base_prompts.csv");
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
        var encodingResult = ENCODING.encode("Unicode snowman: ☃️", 10);
        assertEquals(IntLists.immutable.of(35020, 12056, 1543, 25, 26182, 225, 31643), encodingResult.getTokens());
    }

    @Test
    void other() {
//        var encodingResult = getOriginalEncoder().encode("\uDBAB\uDC2C䠸\uD820\uDC8E", 10);
        var encodingResult = ENCODING.encode("\uDBAB\uDC2C䠸\uD820\uDC8E", 10);
        assertEquals(IntLists.immutable.of(175, 118, 108, 105, 160, 254, 116), encodingResult.getTokens());
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
