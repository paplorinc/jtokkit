package com.knuddels.jtokkit;

import com.knuddels.jtokkit.reference.Cl100kBaseTest;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static com.knuddels.jtokkit.EncodingFactory.loadMergeableRanks;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GptBytePairEncodingTest {
    @Test
    void countBytes() {
        GptBytePairEncoding encoding = (GptBytePairEncoding) EncodingFactory.cl100kBase();

        var expected = 0L;
        var actual = 0L;
        for (int i = 0; i < 1; i++) {
            for (var text : Cl100kBaseTest.getTexts("../")) {
                expected += text.getBytes(UTF_8).length;
                actual += encoding.countBytes(text);
            }
        }
        assertEquals(expected, actual);
    }

    @Test
    void bytePairMerge2() {
        System.out.println("bytePairMerge2");
        var encoding = (GptBytePairEncoding) EncodingFactory.cl100kBase();

        var sum = 0;
        for (var text : Cl100kBaseTest.getTexts("../")) {
            sum += encoding.countTokens(text);
        }
        System.out.println("most used characters:");
        assertEquals(25_876_787, sum);
    }

    @Test
    void bytePairMerge3() {
        var encoding = (GptBytePairEncoding) EncodingFactory.cl100kBase();

        System.out.println(encoding.encode("a b c d e f g h i j k", 5));

        var sum1 = 0;
        for (var x : Cl100kBaseTest.getTexts("../")) {
            sum1 += encoding.encode(x).size();
        }
        System.out.println(sum1);
        assertEquals(25_876_787, sum1);

        var ranks = loadMergeableRanks("cl100k_base.tiktoken");

        var sizes = 0;
        var skipped = new ArrayList<>();
        var collect = ranks.entrySet().stream().sorted(comparingInt(a -> a.getKey().length)).collect(toList());
        for (var entry : collect) {
            var key = entry.getKey();

            if (sizes != key.length) {
                sizes = key.length;
//                System.out.println("Testing size: " + sizes);
            }
            var stringToken = new String(key, UTF_8);
            if (Arrays.equals(key, stringToken.getBytes(UTF_8))) {
//                System.out.println("Testing: " + stringToken);
                var intToken = entry.getValue();

                // Ensure countTokens returns the expected count
                var tokenCount = encoding.countTokens(stringToken);
                assertEquals(1, tokenCount, "Token count does not equal 1 for stringToken: " + stringToken);

                // Check the encoding process
                var encodedTokens = encoding.encode(stringToken);
                assertEquals(IntArrayList.of(intToken), encodedTokens, "Encoded tokens do not match expected value for stringToken: " + stringToken);

                var decodedToken = encoding.decode(encodedTokens);
                assertEquals(stringToken, decodedToken, "Decoded token does not match original stringToken: " + stringToken);
            } else {
                skipped.add(stringToken);
            }
        }
        System.out.println("Skipped " + skipped); // can these work with regexes?
    }
}