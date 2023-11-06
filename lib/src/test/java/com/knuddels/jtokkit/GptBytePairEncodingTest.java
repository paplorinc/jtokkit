package com.knuddels.jtokkit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.knuddels.jtokkit.EncodingFactory.loadMergeableRanks;
import static com.knuddels.jtokkit.reference.Cl100kBaseTestTest.TEXTS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
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
        var skipped = new ArrayList<>();
        var sizes = 0;
        var collect = ranks.entrySet().stream().sorted(comparingInt(a -> a.getKey().length)).collect(toList());
        for (var entry : collect) {
            var key = entry.getKey();

            if (sizes != key.length) {
                sizes = key.length;
                System.out.println("Testing size: " + sizes);
            }
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
                skipped.add(stringToken);
            }
        }
        System.out.println("Skipped " + skipped); // can these work with regexes?


        assertEquals(17815362, TEXTS.stream().mapToInt(encoding::countTokens).sum());
        assertEquals(17815362, TEXTS.stream().mapToInt(x -> encoding.encode(x).size()).sum());
    }
}