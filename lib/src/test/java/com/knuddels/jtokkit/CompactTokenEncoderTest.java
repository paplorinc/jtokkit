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
        byte[] bytes = new byte[]{(byte) 0xA5};
        long result = from(bytes, 0, bytes.length);
        assertEquals(byteSize(result), bytes.length);
        assertArrayEquals(toByteArray(result), bytes);
    }

    @Test
    void testFromWithMultipleBytes() {
        byte[] bytes = new byte[]{(byte) 0xA5, (byte) 0xB4, (byte) 0xC3};
        long result = from(bytes, 0, bytes.length);
        assertEquals(byteSize(result), bytes.length);
        assertArrayEquals(toByteArray(result), bytes);
    }

    @Test
    void testGetSubToken() {
        byte[] bytes = new byte[]{(byte) 0xA5, (byte) 0xB4, (byte) 0xC3};
        long fullToken = from(bytes, 0, bytes.length);
        long subToken = getSubToken(fullToken, 1, 3);
        byte[] expectedSubArray = new byte[]{(byte) 0xB4, (byte) 0xC3};
        assertArrayEquals(toByteArray(subToken), expectedSubArray);
    }

    @Test
    void testEncodeWithLongKey() {
        Map<byte[], Integer> encoder = new HashMap<>();
        encoder.put(new byte[]{(byte) 0xA5, (byte) 0xB4}, 456);
        CompactTokenEncoder tokenEncoder = new CompactTokenEncoder(encoder);
        long key = from(new byte[]{(byte) 0xA5, (byte) 0xB4}, 0, 2);
        assertEquals(tokenEncoder.encode(key), 456);
    }
}
