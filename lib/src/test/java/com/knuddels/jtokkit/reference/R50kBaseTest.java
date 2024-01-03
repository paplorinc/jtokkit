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

class R50kBaseTest {

    private static final Encoding ENCODING = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.R50K_BASE);

    @ParameterizedTest
    @CsvFileSource(resources = "/r50k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void r50kBaseEncodesCorrectly(
            String input,
            String output
    ) {
        IntArrayList expected = TestUtils.parseEncodingString(output);
        IntArrayList actual = ENCODING.encode(input);

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/r50k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void r50kBaseBaseEncodesStable(String input) {
        String actual = ENCODING.decode(ENCODING.encode(input));

        assertEquals(input, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/r50k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void r50kBaseBaseEncodesCorrectlyWithMaxTokensSet(
            String input,
            String output,
            String outputMaxTokens10
    ) {
        IntArrayList expected = TestUtils.parseEncodingString(output);
        IntArrayList expectedWithMaxTokens = TestUtils.parseEncodingString(outputMaxTokens10);
        EncodingResult encodingResult = ENCODING.encode(input, 10);

        assertEquals(expectedWithMaxTokens, encodingResult.getTokens());
        assertEquals(expected.size() > expectedWithMaxTokens.size(), encodingResult.isTruncated());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/r50k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void r50kBaseBaseEncodesStableWithMaxTokensSet(String input) {
        String actual = ENCODING.decode(ENCODING.encode(input, 10).getTokens());

        assertTrue(input.startsWith(actual));
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/r50k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void r50kBaseBaseEncodeOrdinaryEncodesCorrectly(
            String input,
            String output
    ) {
        IntArrayList expected = TestUtils.parseEncodingString(output);
        IntArrayList actual = ENCODING.encodeOrdinary(input);

        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/r50k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void r50kBaseBaseEncodeOrdinaryEncodesCorrectly(
            String input,
            String output,
            String outputMaxTokens10
    ) {
        IntArrayList expected = TestUtils.parseEncodingString(output);
        IntArrayList expectedWithMaxTokens = TestUtils.parseEncodingString(outputMaxTokens10);
        EncodingResult encodingResult = ENCODING.encodeOrdinary(input, 10);

        assertEquals(expectedWithMaxTokens, encodingResult.getTokens());
        assertEquals(expected.size() > expectedWithMaxTokens.size(), encodingResult.isTruncated());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/r50k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void r50kBaseBaseEncodeOrdinaryEncodesStable(String input) {
        String actual = ENCODING.decode(ENCODING.encodeOrdinary(input));

        assertEquals(input, actual);
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/r50k_base_encodings.csv", numLinesToSkip = 1, maxCharsPerColumn = 1_000_000)
    void r50kBaseBaseEncodeOrdinaryEncodesStableWithMaxTokensSet(String input) {
        String actual = ENCODING.decode(ENCODING.encodeOrdinary(input, 10).getTokens());

        assertTrue(input.startsWith(actual));
    }

    @Test
    void r50kBaseBaseEncodeOrdinaryEncodesSpecialTokensCorrectly() {
        String input = "Hello<|endoftext|>, <|fim_prefix|> <|fim_middle|> world <|fim_suffix|> ! <|endofprompt|>";
        String actual = ENCODING.decode(ENCODING.encodeOrdinary(input));

        assertEquals(input, actual);
    }
}
