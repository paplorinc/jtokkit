package com.knuddels.jtokkit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GptBytePairEncodingTest {

    @Test
    void bytePairMerge() {
        var gptBytePairEncoding = (GptBytePairEncoding) EncodingFactory.cl100kBase();
        var result = gptBytePairEncoding.bytePairMerge(ImmutableByteArray.from(" GUTENBERG"));
        assertEquals(gptBytePairEncoding.decode(result), 5);
    }

    @Test
    void bytePairMerge2() {
        var gptBytePairEncoding = (GptBytePairEncoding) EncodingFactory.cl100kBase();
        var result = gptBytePairEncoding.bytePairMerge2(ImmutableByteArray.from(" GUTENBERG"));
        assertEquals(result, 5);
    }
}