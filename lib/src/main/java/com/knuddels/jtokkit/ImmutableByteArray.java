package com.knuddels.jtokkit;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

final class ImmutableByteArray {
    private final byte[] array;
    private final int start;
    private final int end;


    /*
     * Creates a new instance of ImmutableByteArray from the given array.
     * The given array is not copied, so every calling method in this class must make sure
     * to never pass an array which reference leaked to the outside. Since some of our
     * construction methods already create new arrays, we do not want to copy here in this
     * constructor again.
     */
    ImmutableByteArray(final byte[] array, final int start, final int end) {
        this.array = array;
        this.start = start;
        this.end = end;
    }

    /**
     * Creates a new instance of {@link ImmutableByteArray} from the given {@code string}.
     *
     * @param string the string to convert to a byte array
     * @return a new {@link ImmutableByteArray} containing the bytes of the given string
     */
    public static ImmutableByteArray from(final String string) {
        Objects.requireNonNull(string, "String must not be null");
        final byte[] array = string.getBytes(StandardCharsets.UTF_8);
        return new ImmutableByteArray(array, 0, array.length);
    }

    /**
     * Creates a new instance of {@link ImmutableByteArray} from the given {@code array}.
     *
     * @param array the array to copy
     * @return a new {@link ImmutableByteArray} containing the bytes of the given array
     */
    public static ImmutableByteArray from(final byte[] array) {
        Objects.requireNonNull(array, "Byte array must not be null");
        return new ImmutableByteArray(array.clone(), 0, array.length);
    }

    /**
     * Returns the length of this array.
     *
     * @return the length of this array.
     */
    public int length() {
        return end - start;
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
        if (startIndex < 0 || startIndex >= length()) {
            throw new IndexOutOfBoundsException("startIndex out of bounds: " + startIndex + " (" + this + ")");
        } else if (endIndex < 0 || endIndex > length()) {
            throw new IndexOutOfBoundsException("endIndex out of bounds: " + endIndex + " (" + this + ")");
        } else if (startIndex >= endIndex) {
            throw new IllegalArgumentException("startIndex must be less than endIndex: " + startIndex + " >= " + endIndex);
        }
        if (length() == endIndex - startIndex) {
            return this;
        } else {
            return new ImmutableByteArray(array, start + startIndex, start + endIndex);
        }
    }

    /**
     * Returns a copy of the raw array backing this {@link ImmutableByteArray}.
     *
     * @return a copy of the raw array backing this {@link ImmutableByteArray}
     */
    public byte[] getRawArray() {
        final byte[] result = new byte[length()];
        System.arraycopy(array, start, result, 0, length());
        return result;
    }

    public byte getFirstByte() {
        return array[start];
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other == null || getClass() != other.getClass()) {
            return false;
        }

        final ImmutableByteArray that = (ImmutableByteArray) other;
        return Arrays.equals(this.array, this.start, this.end, that.array, that.start, that.end);
    }

    @Override
    public int hashCode() {
        int result = array[start];
        for (int i = start + 1; i < end; i++) {
            result = 31 * result + array[i];
        }
        return result;
    }

    @Override
    public String toString() {
        return Arrays.toString(Arrays.copyOfRange(array, start, end));
    }
}
