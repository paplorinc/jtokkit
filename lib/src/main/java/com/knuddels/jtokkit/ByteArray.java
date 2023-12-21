package com.knuddels.jtokkit;

import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;

class ByteArray {
    public final byte[] array;
    private final int start;
    private final int end;

    ByteArray(byte[] array, int start, int end) {
        this.array = array;
        this.start = start;
        this.end = end;
        if (end > array.length) {
            throw new IllegalArgumentException("end must be smaller than array.length");
        } else if (!TokenEncoder.accepts(length())) {
            throw new IllegalArgumentException("ByteArray must be at least " + Long.BYTES + " bytes long");
        }
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
        } else if ((other == null) || (getClass() != other.getClass())) {
            return false;
        } else {
            var that = (ByteArray) other;
            return Arrays.equals(this.array, start, end, that.array, that.start, that.end);
        }
    }

    @Override
    public int hashCode() {
        assert TokenEncoder.accepts(length()) : "Hashing small arrays is not supported";
        int result = ((array[start] << 24) | (array[start + 1] << 16) | (array[start + 2] << 8) | array[start + 3])
                ^ ((array[start + 4] << 24) | (array[start + 5] << 16) | (array[start + 6] << 8) | array[start + 7]);

        int i = start + 8;
        for (; i <= (end - 4); i += 4) {
            result ^= (array[i] << 24) | (array[i + 1] << 16) | (array[i + 2] << 8) | array[i + 3];
        }
        if (i < end) {
            result ^= array[i];
        }
        return result;
    }


    @Override
    public String toString() {
        return new String(array, start, length(), UTF_8);
    }
}
