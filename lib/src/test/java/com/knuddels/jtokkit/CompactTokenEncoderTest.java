package com.knuddels.jtokkit;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.knuddels.jtokkit.CompactTokenEncoder.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompactTokenEncoderTest {
    @Test
    void testFromWithSingleByte() {
        var bytes = new byte[]{(byte) 0xA5};
        var result = from(bytes, 0, bytes.length);
        assertEquals(byteSize(result), bytes.length);
        assertArrayEquals(toByteArray(result), bytes);
    }

    @Test
    void testFromWithMultipleBytes() {
        var bytes = new byte[]{(byte) 0xA5, (byte) 0xB4, (byte) 0xC3};
        var result = from(bytes, 0, bytes.length);
        assertEquals(byteSize(result), bytes.length);
        assertArrayEquals(toByteArray(result), bytes);
    }

    @Test
    void testGetSubToken() {
        var bytes = new byte[]{(byte) 0xA5, (byte) 0xB4, (byte) 0xC3};
        var fullToken = from(bytes, 0, bytes.length);
        var subToken = getSubToken(fullToken, 1, 3);
        var expectedSubArray = new byte[]{(byte) 0xB4, (byte) 0xC3};
        assertArrayEquals(toByteArray(subToken), expectedSubArray);
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
