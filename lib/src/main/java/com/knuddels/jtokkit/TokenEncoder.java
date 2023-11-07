package com.knuddels.jtokkit;

import org.eclipse.collections.api.factory.primitive.LongIntMaps;
import org.eclipse.collections.api.map.primitive.ImmutableLongIntMap;
import org.eclipse.collections.api.map.primitive.MutableLongIntMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.knuddels.jtokkit.GptBytePairEncoding.PieceIndexToRank;
import static java.nio.charset.StandardCharsets.UTF_8;

final class TokenEncoder {
    public static final int MAX_RANK = Integer.MAX_VALUE;
    private final ImmutableLongIntMap longEncoders;
    private final Map<ImmutableByteArray, Integer> immutableByteArrayEncoders = new HashMap<>();
    private final Map<Integer, byte[]> encodedToDecoded;

    public TokenEncoder(Map<byte[], Integer> encoder) {
        this.encodedToDecoded = new ConcurrentHashMap<>(encoder.size());

        MutableLongIntMap tempLongEncoders = LongIntMaps.mutable.ofInitialCapacity(encoder.size());
        encoder.forEach((k, v) -> {
            Object key = of(k);
            if (key instanceof Long) {
                tempLongEncoders.put((Long) key, v);
            } else {
                immutableByteArrayEncoders.put((ImmutableByteArray) key, v);
            }
            encodedToDecoded.put(v, k);
        });
        this.longEncoders = tempLongEncoders.toImmutable();
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
        assert bytes.length > 0 : "Empty byte array";
        if (bytes.length <= Long.BYTES) {
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
        int length = byteSize(payload);
        int newLength = endIndex - startIndex;
        if (length == newLength) {
            return payload;
        } else if (newLength <= Long.BYTES) {
            if (payload instanceof ImmutableByteArray) {
                return getLongSubTokenForImmutableByteArray((ImmutableByteArray) payload, startIndex, endIndex);
            } else {
                return subTokenLong((long) payload, startIndex, endIndex, length, newLength);
            }
        } else {
            ImmutableByteArray immutableByteArray = getImmutableByteArray(payload, startIndex, endIndex, length);
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

    private static long subTokenLong(long payload, int startIndex, int endIndex, int length, int newLength) {
        long mask = -1L >>> (Long.BYTES - length + startIndex) * Byte.SIZE;
        int shift = (length - endIndex) * Byte.SIZE;
        long result = (payload & mask) >>> shift;

        assert Arrays.equals(asRawArray(result, newLength), getImmutableByteArray(payload, startIndex, endIndex, length).getRawArrayUnsafe()) : "Expected raw array: " + Arrays.toString(getImmutableByteArray(payload, startIndex, endIndex, length).getRawArrayUnsafe()) + ", but got: " + Arrays.toString(asRawArray(result, newLength)) + " for payload: `" + payload + "` with indices: [" + startIndex + ", " + endIndex + "]";
        assert byteSize(result) == newLength : "Expected byte size: " + newLength + ", but got: " + byteSize(result) + " for result: " + result;

        return result;
    }

    private static ImmutableByteArray getImmutableByteArray(Object payload, int startIndex, int endIndex, int length) {
        byte[] rawArray = asRawArray(payload, length);
        ImmutableByteArray from = ImmutableByteArray.from(rawArray);
        return from.getBytesBetween(startIndex, endIndex);
    }

    public static byte[] asRawArray(Object payload) {
        return asRawArray(payload, byteSize(payload));
    }

    private static byte[] asRawArray(Object payload, int length) {
        if (length <= Long.BYTES) {
            byte[] bytes = new byte[length];
            for (long value = (Long) payload; length > 0; ) {
                bytes[--length] = (byte) (value & 0xFFL);
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

    public List<PieceIndexToRank> initializeParts(Object payload) {
        int length = byteSize(payload);
        List<PieceIndexToRank> parts = new ArrayList<>(length + 1);
        assert length > 1 : "Already filtered out";
        if (length == 2) {
            parts.add(new PieceIndexToRank(0, encodeOrDefault(payload, MAX_RANK)));
        } else if (length <= Long.BYTES) {
            for (int i = 0; i < length - 1; i++) {
                long result = subTokenLong((long) payload, i, i + 2, length, 2);
                parts.add(new PieceIndexToRank(i, encodeOrDefault(result, MAX_RANK)));
            }
        } else {
            for (int i = 0; i < length - 1; i++) {
                Object subToken = getSubToken(payload, i, i + 2);
                parts.add(new PieceIndexToRank(i, encodeOrDefault(subToken, MAX_RANK)));
            }
        }
        parts.add(new PieceIndexToRank(length - 1, MAX_RANK));
        parts.add(new PieceIndexToRank(length, MAX_RANK));
        return parts;
    }

    public byte[] decodeIfPresent(Integer encodedToken) {
        return encodedToDecoded.get(encodedToken);
    }

    public boolean containsDecodedToken(Object payload) {
        if (payload instanceof Long) {
            return longEncoders.containsKey((long) payload);
        } else {
            return immutableByteArrayEncoders.containsKey((ImmutableByteArray) payload);
        }
    }

    public Integer encodeOrDefault(Object payload, Integer defaultValue) {
        if (payload instanceof Long) {
            return defaultValue == null
                    ? longEncoders.getOrThrow((long) payload)
                    : longEncoders.getIfAbsent((long) payload, defaultValue);
        } else {
            ImmutableByteArray value = (ImmutableByteArray) payload;
            assert (value.length() > Long.BYTES);
            Integer result = immutableByteArrayEncoders.get(value);
            return result != null
                    ? result
                    : Objects.requireNonNull(defaultValue, () -> "Unknown token for encoding: " + payload);
        }
    }
}