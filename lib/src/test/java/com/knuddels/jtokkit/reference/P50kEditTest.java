package com.knuddels.jtokkit.reference;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingResult;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P50kEditTest {

    private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.P50K_EDIT);

    @ParameterizedTest
    @CsvFileSource(resources = "/p50k_edit_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void p50kEditEncodesCorrectly(
            final String input,
            final String output
    ) {
        final IntArrayList expected = TestUtils.parseEncodingString(output);
        final IntArrayList actual = ENCODING.encode(input);

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/p50k_edit_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void p50kEditEncodesStable(final String input) {
        final String actual = ENCODING.decode(ENCODING.encode(input));

        assertEquals(input, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/p50k_edit_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void p50kEditEncodesCorrectlyWithMaxTokensSet(
            final String input,
            final String output,
            final String outputMaxTokens10
    ) {
        final IntArrayList expected = TestUtils.parseEncodingString(output);
        final IntArrayList expectedWithMaxTokens = TestUtils.parseEncodingString(outputMaxTokens10);
        final EncodingResult encodingResult = ENCODING.encode(input, 10);

        assertEquals(expectedWithMaxTokens, encodingResult.getTokens());
        assertEquals(expected.size() > expectedWithMaxTokens.size(), encodingResult.isTruncated());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/p50k_edit_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void p50kEditEncodesStableWithMaxTokensSet(final String input) {
        final String actual = ENCODING.decode(ENCODING.encode(input, 10).getTokens());

        assertTrue(input.startsWith(actual));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/p50k_edit_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void p50kEditEncodeOrdinaryEncodesCorrectly(
            final String input,
            final String output
    ) {
        final IntArrayList expected = TestUtils.parseEncodingString(output);
        final IntArrayList actual = ENCODING.encodeOrdinary(input);

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/p50k_edit_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void p50kEditEncodeOrdinaryEncodesCorrectly(
            final String input,
            final String output,
            final String outputMaxTokens10
    ) {
        final IntArrayList expected = TestUtils.parseEncodingString(output);
        final IntArrayList expectedWithMaxTokens = TestUtils.parseEncodingString(outputMaxTokens10);
        final EncodingResult encodingResult = ENCODING.encodeOrdinary(input, 10);

        assertEquals(expectedWithMaxTokens, encodingResult.getTokens());
        assertEquals(expected.size() > expectedWithMaxTokens.size(), encodingResult.isTruncated());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/p50k_edit_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void p50kEditEncodeOrdinaryEncodesStable(final String input) {
        final String actual = ENCODING.decode(ENCODING.encodeOrdinary(input));

        assertEquals(input, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/p50k_edit_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void p50kEditEncodeOrdinaryEncodesStableWithMaxTokensSet(final String input) {
        final String actual = ENCODING.decode(ENCODING.encodeOrdinary(input, 10).getTokens());

        assertTrue(input.startsWith(actual));
    }

    @Test
    void p50kEditEncodeOrdinaryEncodesSpecialTokensCorrectly() {
        final String input = "Hello<|endoftext|>, <|fim_prefix|> <|fim_middle|> world <|fim_suffix|> ! <|endofprompt|>";
        final String actual = ENCODING.decode(ENCODING.encodeOrdinary(input));

        assertEquals(input, actual);
    }
}
