package com.knuddels.jtokkit;

import java.util.Arrays;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class ImmutableByteArray {
    private final byte[] array;

    /*
     * Creates a new instance of ImmutableByteArray from the given array.
     * The given array is not copied, so every calling method in this class must make sure
     * to never pass an array which reference leaked to the outside. Since some of our
     * construction methods already create new arrays, we do not want to copy here in this
     * constructor again.
     */
    ImmutableByteArray(final byte[] array) {
        assert array.length > 0 : "Empty byte array";
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

    /**
     * Returns the bytes of this array from startIndex (inclusive) to endIndex (exclusive). The returned array is a copy
     * of the original array.
     *
     * @param startIndex the index from which to start copying (inclusive)
     * @param endIndex   the index at which to stop copying (exclusive)
     * @return a new {@link ImmutableByteArray} containing the bytes from startIndex (inclusive) to endIndex (exclusive)
     * @throws IllegalArgumentException if startIndex is out of bounds, endIndex is out of bounds or endIndex is less than
     *                                  startIndex
     */
    public ImmutableByteArray getBytesBetween(final int startIndex, final int endIndex) {
        if (startIndex < 0 || startIndex >= array.length) {
            throw new IndexOutOfBoundsException("startIndex out of bounds: " + startIndex + " (" + this + ")");
        } else if (endIndex < 0 || endIndex > array.length) {
            throw new IndexOutOfBoundsException("endIndex out of bounds: " + endIndex + " (" + this + ")");
        } else if (startIndex >= endIndex) {
            throw new IllegalArgumentException("startIndex must be less than endIndex: " + startIndex + " >= " + endIndex);
        }
        if (length() == endIndex - startIndex) {
            return this;
        } else {
            final int length = endIndex - startIndex;
            final byte[] result = new byte[length];
            System.arraycopy(array, startIndex, result, 0, length);
            return new ImmutableByteArray(result);
        }
    }

    /**
     * Returns the raw array backing this {@link ImmutableByteArray}.
     *
     * @return the raw array backing this {@link ImmutableByteArray}
     */
    public byte[] getRawArrayUnsafe() {
        return array;
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
