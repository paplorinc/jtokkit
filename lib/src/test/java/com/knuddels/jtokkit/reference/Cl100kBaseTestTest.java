package com.knuddels.jtokkit.reference;

import com.knuddels.jtokkit.EncodingFactory;
import com.knuddels.jtokkit.GptBytePairEncoding;
import com.knuddels.jtokkit.api.Encoding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Cl100kBaseTestTest {

    public static final List<String> TEXTS = loadData("../benchmark/data");
    private static final Encoding ENCODING = EncodingFactory.cl100kBase();

    public static List<String> loadData(final String folder) {
        try {
            final var folderPath = Paths.get(folder);
            final var fileContents = new ArrayList<String>();
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

    @Test
    void test() throws IOException {
        for (var text : TEXTS) {
            var oldEncoding = (GptBytePairEncoding) EncodingFactory.cl100kBaseOriginal();
            var encode = oldEncoding.encode(text);

            var newEncoding = (GptBytePairEncoding) ENCODING;
            var newEncode = newEncoding.encode(text);
            if (!Objects.equals(encode, newEncode)) {
                var diff = new HashSet<>(encode);
                diff.removeAll(newEncode);
                var collect = diff.stream().map(x -> oldEncoding.decode(List.of(x))).collect(Collectors.toList());
                System.out.println(collect);

                var diff2 = new HashSet<>(newEncode);
                diff2.removeAll(encode);
                var collect2 = diff2.stream().map(x -> newEncoding.decode(List.of(x))).collect(Collectors.toList());
                System.out.println(collect2);

                System.out.println("Old: " + encode);
                System.out.println("New: " + newEncode);
            }
            assertEquals(encode, newEncode);
        }
    }

    @Test
    public void xxx() {
        var encoding = (GptBytePairEncoding) ENCODING;
        var actual = TEXTS.stream().mapToInt(encoding::countTokens).sum();
        assertEquals(18760595, actual);

        var actual1 = TEXTS.stream()
                .flatMap(x -> encoding.encode(x).stream().map(y -> encoding.decodeToken(y).length))
                .collect(groupingBy(identity(), counting()));

        System.out.println(actual1);
        System.out.println(214495 + 110470 + 43433 + 20046 + 8473 + 2596 + 785 + 158 + 49 + 215 + 35 + 34 + 53 + 42 + 44 + 25 + 41 + 73 + 29 + 36 + 26 + 26 + 47 + 33 + 18 + 11 + 7 + 10 + 13 + 15 + 15 + 11 + 6 + 12 + 12 + 14 + 16 + 8 + 9 + 10 + 4 + 6 + 3 + 8 + 4 + 30 + 1 + 6 + 5 + 1 + 1 + 1 + 25 + 1);


        //params.getEncoder
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    public void cl100kBaseEncodesCorrectly(
            final String input,
            final String output
    ) {
        final var expected = TestUtils.parseEncodingString(output);
        final var actual = ENCODING.encode(input);

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    public void cl100kBaseEncodesStable(final String input) {
        final var actual = ENCODING.decode(ENCODING.encode(input));

        assertEquals(input, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    public void cl100kBaseEncodesCorrectlyWithMaxTokensSet(
            final String input,
            final String output,
            final String outputMaxTokens10
    ) {
        final var expected = TestUtils.parseEncodingString(output);
        final var expectedWithMaxTokens = TestUtils.parseEncodingString(outputMaxTokens10);
        final var encodingResult = ENCODING.encode(input, 10);

        assertEquals(expectedWithMaxTokens, encodingResult.getTokens(), "Encoding result does not match expected value for input: " + input);
        assertEquals(expected.size() > expectedWithMaxTokens.size(), encodingResult.isTruncated());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    public void cl100kBaseEncodesStableWithMaxTokensSet(final String input) {
        final var actual = ENCODING.decode(ENCODING.encode(input, 10).getTokens());

        assertTrue(input.startsWith(actual));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    public void cl100kBaseEncodeOrdinaryEncodesCorrectly(
            final String input,
            final String output
    ) {
        final var expected = TestUtils.parseEncodingString(output);
        final var actual = ENCODING.encodeOrdinary(input);

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    public void cl100kBaseEncodeOrdinaryEncodesCorrectly(
            final String input,
            final String output,
            final String outputMaxTokens10
    ) {
        final var expected = TestUtils.parseEncodingString(output);
        final var expectedWithMaxTokens = TestUtils.parseEncodingString(outputMaxTokens10);
        final var encodingResult = ENCODING.encodeOrdinary(input, 10);

        assertEquals(expectedWithMaxTokens, encodingResult.getTokens());
        assertEquals(expected.size() > expectedWithMaxTokens.size(), encodingResult.isTruncated());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    public void cl100kBaseEncodeOrdinaryEncodesStable(final String input) {
        final var actual = ENCODING.decode(ENCODING.encodeOrdinary(input));

        assertEquals(input, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/cl100k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    public void cl100kBaseEncodeOrdinaryEncodesStableWithMaxTokensSet(final String input) {
        final var actual = ENCODING.decode(ENCODING.encodeOrdinary(input, 10).getTokens());

        assertTrue(input.startsWith(actual));
    }

    @Test
    public void cl100kBaseEncodeOrdinaryEncodesSpecialTokensCorrectly() {
        final var input = "Hello<|endoftext|>, <|fim_prefix|> <|fim_middle|> world <|fim_suffix|> ! <|endofprompt|>";
        final var actual = ENCODING.decode(ENCODING.encodeOrdinary(input));

        assertEquals(input, actual);
    }
}
