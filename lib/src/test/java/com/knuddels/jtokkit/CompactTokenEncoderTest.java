package com.knuddels.jtokkit;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.knuddels.jtokkit.CompactTokenEncoder.byteSize;
import static com.knuddels.jtokkit.CompactTokenEncoder.from;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompactTokenEncoderTest {
    @Test
    void testFromWithSingleByte() {
        var bytes = new byte[]{(byte) 0xA5};
        var result = from(bytes, 0, bytes.length);
        assertEquals(byteSize(result), bytes.length);
    }

    @Test
    void testFromWithMultipleBytes() {
        var bytes = new byte[]{(byte) 0xA5, (byte) 0xB4, (byte) 0xC3};
        var result = from(bytes, 0, bytes.length);
        assertEquals(byteSize(result), bytes.length);
    }

    @Test
    void testEncodeWithLongKey() {
        Map<byte[], Integer> encoder = new HashMap<>();
        encoder.put(new byte[]{(byte) 0xA5, (byte) 0xB4}, 456);
        var tokenEncoder = new CompactTokenEncoder(encoder);
        var key = from(new byte[]{(byte) 0xA5, (byte) 0xB4}, 0, 2);
        assertEquals(tokenEncoder.encode(key), 456);
    }
}
