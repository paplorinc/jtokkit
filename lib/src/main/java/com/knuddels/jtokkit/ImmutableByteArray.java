package com.knuddels.jtokkit;

import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;

class ImmutableByteArray {
    public final byte[] array;
    private final int start;
    private final int end;

    ImmutableByteArray(byte[] array, int start, int end) {
        this.array = array;
        this.start = start;
        this.end = end;
        assert !CompactTokenEncoder.accepts(length()) : "Small byte arrays are not supported";
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public int length() {
        return end - start;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other == null || getClass() != other.getClass()) {
            return false;
        } else {
            ImmutableByteArray that = (ImmutableByteArray) other;
            return Arrays.equals(this.array, start, end, that.array, that.start, that.end);
        }
    }

    @Override
    public int hashCode() {
        assert !CompactTokenEncoder.accepts(length()) : "Hashing small arrays is not supported";
        int result = 1;
        for (int i = start; i < end; i++) {
            result = 31 * result + array[i];
        }
        return result;
    }


    @Override
    public String toString() {
        return new String(array, start, length(), UTF_8);
    }
}
