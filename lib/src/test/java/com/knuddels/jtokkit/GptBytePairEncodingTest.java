package com.knuddels.jtokkit;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.knuddels.jtokkit.EncodingFactory.loadMergeableRanks;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GptBytePairEncodingTest {

    @Test
    void bytePairMerge() {
        var gptBytePairEncoding = (GptBytePairEncoding) EncodingFactory.cl100kBase();
        var result = gptBytePairEncoding.bytePairMerge(ImmutableByteArray.from(" GUTENBERG"));
        assertEquals(gptBytePairEncoding.decode(result), " GUTENBERG");
    }

    @Test
    void bytePairMerge2() {
        var gptBytePairEncoding = (GptBytePairEncoding) EncodingFactory.cl100kBase();
        var result = gptBytePairEncoding.bytePairMerge2(ImmutableByteArray.from(" GUTENBERG"));
        assertEquals(result, 5);
    }

    @Test
    void bytePairMerge3() {
        var ranks = loadMergeableRanks("cl100k_base.tiktoken");

        var encoding = (GptBytePairEncoding) EncodingFactory.cl100kBase();
        var skipped = 0;
        for (var entry : ranks.entrySet()) {
            var key = entry.getKey();
            var stringToken = new String(key, UTF_8);
            if (Arrays.equals(key, stringToken.getBytes(UTF_8))) {
                System.out.println("Testing: " + stringToken);
                var intToken = entry.getValue();

                // Ensure countTokens returns the expected count
                var tokenCount = encoding.countTokens(stringToken);
                assertEquals(1, tokenCount, "Token count does not equal 1 for stringToken: " + stringToken);

                // Check the encoding process
                var encodedTokens = encoding.encode(stringToken);
                assertEquals(List.of(intToken), encodedTokens, "Encoded tokens do not match expected value for stringToken: " + stringToken);

                var decodedToken = encoding.decode(encodedTokens);
                assertEquals(stringToken, decodedToken, "Decoded token does not match original stringToken: " + stringToken);
            } else {
                System.out.println("Skipping: " + Arrays.toString(key));
                skipped++;
            }
        }
        System.out.println("Skipped " + skipped); // can these work?
    }
}