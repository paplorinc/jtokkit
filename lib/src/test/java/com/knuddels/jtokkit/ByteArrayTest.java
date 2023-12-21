package com.knuddels.jtokkit;

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ByteArrayTest {

    @Test
    public void canBeUsedAsKeyInMap() {
        var array = "1, 2, 3, 4, 5, 6, 7, 8".getBytes(UTF_8);
        final ByteArray key1 = new ByteArray(array, 0, array.length);
        final ByteArray key2 = new ByteArray(array, 0, array.length);

        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    public void getLengthIsCorrect() {
        var array = new byte[]{0, 1, 2, 3, 4, 5, 6, 7};
        final ByteArray byteArray = new ByteArray(array, 0, array.length);
        assertEquals(8, byteArray.length());
    }

    @Test
    public void getBytesBetweenReturnsCorrectSliceOfArray() {
        var array = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        final ByteArray byteArray = new ByteArray(array, 0, array.length);

        var expected = new byte[]{2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertEquals(new ByteArray(expected, 0, expected.length), new ByteArray(byteArray.array, 1, array.length));
    }
}
