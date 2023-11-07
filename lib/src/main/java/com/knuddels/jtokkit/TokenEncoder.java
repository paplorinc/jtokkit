package com.knuddels.jtokkit;

import org.eclipse.collections.api.factory.primitive.LongIntMaps;
import org.eclipse.collections.api.map.primitive.ImmutableLongIntMap;
import org.eclipse.collections.api.map.primitive.MutableLongIntMap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.knuddels.jtokkit.GptBytePairEncoding.PieceIndexToRank;
import static java.nio.charset.StandardCharsets.UTF_8;

final class TokenEncoder {
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
            return 1 + (64 - Long.numberOfLeadingZeros((Long) payload) - 1) / 8;
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
        if (bytes.length <= 8) {
            long result = bytes[0] & 0xFFL;
            for (int i = 1; i < bytes.length; i++) {
                result = (result << 8) | (bytes[i] & 0xFFL);
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
        } else if (newLength <= 8) {
            if (payload instanceof ImmutableByteArray) {
                return getLongSubTokenForImmutableByteArray((ImmutableByteArray) payload, startIndex, endIndex);
            } else {
                return getLongSubToken(payload, startIndex, endIndex, length, newLength);
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
            result = (result << 8) | (rawArray[i] & 0xFFL);
        }
        return result;
    }

    private static long getLongSubToken(Object payload, int startIndex, int endIndex, int length, int newLength) {
        var result = subToken((Long) payload, startIndex, endIndex, length);

        assert byteSize(result) == newLength : "Expected byte size: " + newLength + ", but got: " + byteSize(result) + " for result: " + result;
        assert Arrays.equals(asRawArray(result, newLength), getImmutableByteArray(payload, startIndex, endIndex, length).getRawArrayUnsafe()) : "Expected raw array: " + Arrays.toString(getImmutableByteArray(payload, startIndex, endIndex, length).getRawArrayUnsafe()) + ", but got: " + Arrays.toString(asRawArray(result, newLength)) + " for payload: `" + payload + "` with indices: [" + startIndex + ", " + endIndex + "]";

        return result;
    }

    private static long subToken(long payload, int startIndex, int endIndex, int length) {
        long mask = -1L >>> (Long.BYTES - length + startIndex) * Byte.SIZE;
        int shift = (length - endIndex) * Byte.SIZE;
        return (payload & mask) >>> shift;
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
        if (length <= 8) {
            byte[] bytes = new byte[length];
            for (long value = (Long) payload; length > 0; ) {
                bytes[--length] = (byte) (value & 0xFF);
                value >>>= 8;
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
            parts.add(new PieceIndexToRank(0, encodeOrDefault(payload, Integer.MAX_VALUE)));
        } else if (length <= Long.BYTES) {
            for (int i = 0; i < length - 1; i++) {
                var result = subToken((Long) payload, i, i + 2, length);
                parts.add(new PieceIndexToRank(i, encodeOrDefault(result, Integer.MAX_VALUE)));
            }
        } else {
            for (int i = 0; i < length - 1; i++) {
                var subToken = getSubToken(payload, i, i + 2);
                parts.add(new PieceIndexToRank(i, encodeOrDefault(subToken, Integer.MAX_VALUE)));
            }
        }
        parts.add(new PieceIndexToRank(length - 1, Integer.MAX_VALUE));
        parts.add(new PieceIndexToRank(length, Integer.MAX_VALUE));
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
        if (payload instanceof Long) {
            long value = (Long) payload;
            return defaultValue == null
                    ? longEncoders.getOrThrow(value)
                    : longEncoders.getIfAbsent(value, defaultValue);
        } else {
            ImmutableByteArray value = (ImmutableByteArray) payload;
            assert (value.length() > 8);
            Integer result = immutableByteArrayEncoders.get(value);
            return result != null
                    ? result
                    : Objects.requireNonNull(defaultValue, () -> "Unknown token for encoding: " + payload);
        }
    }
}
