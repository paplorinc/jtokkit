package com.knuddels.jtokkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

final class TokenEncoder {
    private final Map<Long, Integer> longEncoders = new HashMap<>(); // TODO ImmutableLongIntMap
    private final Map<ImmutableByteArray, Integer> immutableByteArrayEncoders = new HashMap<>();
    private final Map<Integer, byte[]> encodedToDecoded;

    public TokenEncoder(Map<byte[], Integer> encoder) {
        this.encodedToDecoded = new ConcurrentHashMap<>(encoder.size());

        encoder.forEach((k, v) -> {
            Object key = of(k);
            if (key instanceof Long) {
                longEncoders.put((Long) key, v);
            } else {
                immutableByteArrayEncoders.put((ImmutableByteArray) key, v);
            }
            encodedToDecoded.put(v, k);
        });
    }

    public static int byteSize(Object payload) {
        if (payload instanceof Long) {
            return 1 + (Long.SIZE - Long.numberOfLeadingZeros((Long) payload) - 1) / Byte.SIZE;
        } else {
            return ((ImmutableByteArray) payload).length();
        }
    }

    public static Object of(String payload) {
        byte[] bytes = payload.getBytes(UTF_8);
        Object result = of(bytes);
        assert Objects.equals(bytes.length, byteSize(result)) : "Expected byte size: " + bytes.length + ", Actual byte size: " + byteSize(result) + " for payload: " + payload;
        assert Arrays.equals(bytes, asRawArray(result)) : "Expected bytes and actual raw array do not match for payload: `" + payload + "`. Expected: " + Arrays.toString(bytes) + ", Actual: " + Arrays.toString(asRawArray(result));
        assert Objects.equals(payload, asString(result)) : "Expected string: `" + payload + "`, Actual string: " + asString(result) + " for result object: " + result;
        return result;
    }

    private static Object of(byte[] bytes) {
        if (bytes.length <= Byte.SIZE) {
            long result = bytes[0] & 0xFFL;
            for (int i = 1; i < bytes.length; i++) {
                result = (result << Byte.SIZE) | (bytes[i] & 0xFFL);
            }
            return result;
        } else {
            return new ImmutableByteArray(bytes);
        }
    }

    public static Object getSubToken(Object payload, int startIndex, int endIndex) {
        var length = byteSize(payload);
        var newLength = endIndex - startIndex;
        if (length == newLength) {
            return payload;
        } else if (newLength <= Long.BYTES) {
            if (payload instanceof ImmutableByteArray) {
                return getLongSubTokenForImmutableByteArray((ImmutableByteArray) payload, startIndex, endIndex);
            } else {
                return getLongSubToken(payload, startIndex, endIndex, length, newLength);
            }
        } else {
            var immutableByteArray = getImmutableByteArray(payload, startIndex, endIndex, length);
            assert immutableByteArray.length() == newLength :
                    "Expected length: " + newLength + ", but got: " + immutableByteArray.length() + " for payload: `" + payload + "` with indices: [" + startIndex + ", " + endIndex + "]";
            return immutableByteArray;
        }
    }

    private static long getLongSubTokenForImmutableByteArray(ImmutableByteArray payload, int startIndex, int endIndex) {
        byte[] rawArray = payload.getRawArrayUnsafe();
        long result = rawArray[startIndex] & 0xFFL;
        for (int i = startIndex + 1; i < endIndex; i++) {
            result = (result << Byte.SIZE) | (rawArray[i] & 0xFFL);
        }
        return result;
    }

    private static long getLongSubToken(Object payload, int startIndex, int endIndex, int length, int newLength) {
        var mask = -1L >>> (((Long.BYTES - length) + startIndex) * Byte.SIZE);
        var shift = (length - endIndex) * Byte.SIZE;
        long result = ((Long) payload & mask) >>> shift;

        assert byteSize(result) == newLength : "Expected byte size: " + newLength + ", but got: " + byteSize(result) + " for result: " + result;
        assert Arrays.equals(asRawArray(result, newLength), getImmutableByteArray(payload, startIndex, endIndex, length).getRawArrayUnsafe()) : "Expected raw array: " + Arrays.toString(getImmutableByteArray(payload, startIndex, endIndex, length).getRawArrayUnsafe()) + ", but got: " + Arrays.toString(asRawArray(result, newLength)) + " for payload: `" + payload + "` with indices: [" + startIndex + ", " + endIndex + "]";

        return result;
    }

    private static ImmutableByteArray getImmutableByteArray(Object payload, int startIndex, int endIndex, int length) {
        var rawArray = asRawArray(payload, length);
        var from = ImmutableByteArray.from(rawArray);
        return from.getBytesBetween(startIndex, endIndex);
    }

    public static byte[] asRawArray(Object payload) {
        return asRawArray(payload, byteSize(payload));
    }

    private static byte[] asRawArray(Object payload, int length) {
        if (length <= Long.BYTES) {
            // TODO specialize
            byte[] bytes = new byte[length];
            for (long value = (Long) payload; length > 0; ) {
                bytes[--length] = (byte) (value & 0xFF);
                value >>>= Byte.SIZE;
            }
            return bytes;
        } else {
            return ((ImmutableByteArray) payload).getRawArrayUnsafe();
        }
    }

    public static String asString(Object payload) {
        return new String(asRawArray(payload), UTF_8);
    }

    public List<GptBytePairEncoding.PieceIndexToRank> initializeParts(Object payload) {
        // TODO specialize
        int length = byteSize(payload);
        List<GptBytePairEncoding.PieceIndexToRank> parts = new ArrayList<>(length + 1);
        for (int i = 0; i < length + 1; i++) {
            int rank = i < length - 1
                    ? encodeOrDefault(getSubToken(payload, i, i + 2), Integer.MAX_VALUE)
                    : Integer.MAX_VALUE;
            parts.add(new GptBytePairEncoding.PieceIndexToRank(i, rank));
        }
        return parts;
    }

    public byte[] decodeIfPresent(Integer encodedToken) {
        return encodedToDecoded.get(encodedToken);
    }

    public boolean containsDecodedToken(Object payload) {
        if (payload instanceof Long) {
            return longEncoders.containsKey((Long) payload);
        } else {
            return immutableByteArrayEncoders.containsKey((ImmutableByteArray) payload);
        }
    }

    public Integer encodeOrDefault(Object payload, Integer defaultValue) {
        Integer result;
        if (payload instanceof Long) {
            result = longEncoders.get((Long) payload);
        } else {
            var immutableByteArray = (ImmutableByteArray) payload;
            if (immutableByteArray.length() <= Long.BYTES) {
                result = encodeOrDefault(of(immutableByteArray.getRawArrayUnsafe()), defaultValue); // TODO
            } else {
                result = immutableByteArrayEncoders.get(immutableByteArray);
            }
        }
        return result != null
                ? result
                : Objects.requireNonNull(defaultValue, () -> "Unknown token for encoding: " + payload);
    }
}
