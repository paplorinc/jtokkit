package com.knuddels.jtokkit;

import org.junit.jupiter.api.Test;

import static com.knuddels.jtokkit.TokenEncoder.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenEncoderTest {
    @Test
    public void testCombineAndRetrieve() {
        var index = 1234;
        var rank = 567;

        var combined = combine(index, rank);

        assertEquals(index, index(combined));
        assertEquals(rank, rank(combined));

        var newRank = 89;
        var updated = setRank(combined, newRank);

        assertEquals(index, index(updated));
        assertEquals(newRank, rank(updated));
    }

    @Test
    public void testCombineAndRetrieveForAllPossibleValues() {
        var encoding = (GptBytePairEncoding) EncodingFactory.cl100kBase();
        encoding.encodedToDecoded.keySet().forEach(rank -> {
            for (var index = 0; index < 100; index++) {
                var combined = combine(index, rank);
                assertEquals(index, index(combined));
                assertEquals(rank, rank(combined));

                var updated = setRank(combined, MAX_RANK);
                assertEquals(index, index(updated));
                assertEquals(MAX_RANK, rank(updated));
            }
        });
    }
}