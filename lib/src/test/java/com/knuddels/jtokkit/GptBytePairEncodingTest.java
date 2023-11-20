package com.knuddels.jtokkit;

import org.eclipse.collections.api.factory.primitive.IntLists;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static com.knuddels.jtokkit.EncodingFactory.loadMergeableRanks;
import static com.knuddels.jtokkit.reference.Cl100kBaseTestTest.TEXTS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GptBytePairEncodingTest {
//    public static void main(String[] args) {
//        var encoding = (GptBytePairEncoding) EncodingFactory.cl100kBase();
//        var sum = 0;
//        for (int i = 0; i < 10; i++) {
//            for (var text : TEXTS) {
//                sum += encoding.countTokens(text);
//            }
//        }
//        System.out.println(sum);
//    }

    @Test
    void countSplitChars() {
        GptBytePairEncoding encoding = (GptBytePairEncoding) EncodingFactory.cl100kBase();

        var expected = 0L;
        var sum = 0L;
        var max = 1;
        for (int i = 0; i < max; i++) {
            for (var text : TEXTS) {
                expected += text.length();
                sum += encoding.countSplitChars(text);
            }
        }
        assertEquals(max * 74_307_673, sum);
        assertEquals(expected, sum);
    }

    @Test
    void bytePairMerge3() {
        var encoding = (GptBytePairEncoding) EncodingFactory.cl100kBase();

        System.out.println(encoding.countTokens(" "));

        var sum = 0;
        for (var text : TEXTS) {
            var i = encoding.countTokens(text);
            sum += i;
        }
        System.out.println(sum);
        assertEquals(18_840_846, sum);

        var sum1 = 0;
        for (var x : TEXTS) {
            var size = encoding.encode(x).size();
            sum1 += size;
        }
        System.out.println(sum1);
        assertEquals(18_840_846, sum1);

        var ranks = loadMergeableRanks("cl100k_base.tiktoken");

        var sizes = 0;
        var skipped = new ArrayList<>();
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
                assertEquals(IntLists.immutable.of(intToken), encodedTokens, "Encoded tokens do not match expected value for stringToken: " + stringToken);

                var decodedToken = encoding.decode(encodedTokens);
                assertEquals(stringToken, decodedToken, "Decoded token does not match original stringToken: " + stringToken);
            } else {
                skipped.add(stringToken);
            }
        }
        System.out.println("Skipped " + skipped); // can these work with regexes?
    }

//    @Test
//    void bytePairMerge() {
//        var gptBytePairEncoding = (GptBytePairEncoding) EncodingFactory.cl100kBase();
//        var piece = ImmutableByteArray.from(" GUTENBERG");
//        var indexedRanks = gptBytePairEncoding.getIndexedRanks(piece, piece.length() + 1);
//        var tokenCount = gptBytePairEncoding.mergeBytesAndGetTokenCount(piece, piece.length() + 1, indexedRanks);
//        var result = gptBytePairEncoding.encodeToList(piece, tokenCount, indexedRanks);
//        assertEquals(gptBytePairEncoding.decode(result), " GUTENBERG");
//    }
}