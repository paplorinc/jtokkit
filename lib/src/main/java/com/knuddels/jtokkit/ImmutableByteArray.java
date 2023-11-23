package com.knuddels.jtokkit;

import java.util.Arrays;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class ImmutableByteArray {
    final byte[] array;

    /*
     * Creates a new instance of ImmutableByteArray from the given array.
     * The given array is not copied, so every calling method in this class must make sure
     * to never pass an array which reference leaked to the outside. Since some of our
     * construction methods already create new arrays, we do not want to copy here in this
     * constructor again.
     */
    ImmutableByteArray(final byte[] array) {
        assert array.length > Long.BYTES : "Invalid byte array";
        this.array = array;
    }

    /**
     * Creates a new instance of {@link ImmutableByteArray} from the given {@code string}.
     *
     * @param string the string to convert to a byte array
     * @return a new {@link ImmutableByteArray} containing the bytes of the given string
     */
    public static ImmutableByteArray from(final String string) {
        Objects.requireNonNull(string, "String must not be null");
        return new ImmutableByteArray(string.getBytes(UTF_8));
    }

    /**
     * Creates a new instance of {@link ImmutableByteArray} from the given {@code array}.
     *
     * @param array the array to copy
     * @return a new {@link ImmutableByteArray} containing the bytes of the given array
     */
    public static ImmutableByteArray from(final byte[] array) {
        Objects.requireNonNull(array, "Byte array must not be null");
        return new ImmutableByteArray(array.clone());
    }

    /**
     * Returns the length of this array.
     *
     * @return the length of this array.
     */
    public int length() {
        return array.length;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        } else if (other == null || getClass() != other.getClass()) {
            return false;
        }

        final ImmutableByteArray that = (ImmutableByteArray) other;
        return Arrays.equals(array, that.array);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }

    @Override
    public String toString() {
        return new String(array, UTF_8);
    }
}
