package com.knuddels.jtokkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

final class TokenEncoder {
    private final Map<Long, Integer> longEncoders = new ConcurrentHashMap<>(); // TODO ImmutableLongIntMap
    private final Map<ImmutableByteArray, Integer> byteArrayEncoders = new ConcurrentHashMap<>();
    private final Map<Integer, byte[]> encodedToDecoded;

    public TokenEncoder(Map<byte[], Integer> encoder) {
        this.encodedToDecoded = new ConcurrentHashMap<>(encoder.size());

        encoder.forEach((k, v) -> {
            Object key = of(k);
            if (key instanceof Long) {
                longEncoders.put((Long) key, v);
            } else {
                byteArrayEncoders.put((ImmutableByteArray) key, v);
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
        switch (bytes.length) {
            case 1:
                return (long) (bytes[0] & 0xFF);
            case 2:
                return (long) (bytes[0] & 0xFF) << 8 |
                        (long) (bytes[1] & 0xFF);
            case 3:
                return (long) (bytes[0] & 0xFF) << 16 |
                        (long) (bytes[1] & 0xFF) << 8 |
                        (long) (bytes[2] & 0xFF);
            case 4:
                return (long) (bytes[0] & 0xFF) << 24 |
                        (long) (bytes[1] & 0xFF) << 16 |
                        (long) (bytes[2] & 0xFF) << 8 |
                        (long) (bytes[3] & 0xFF);
            case 5:
                return (long) (bytes[0] & 0xFF) << 32 |
                        (long) (bytes[1] & 0xFF) << 24 |
                        (long) (bytes[2] & 0xFF) << 16 |
                        (long) (bytes[3] & 0xFF) << 8 |
                        (long) (bytes[4] & 0xFF);
            case 6:
                return (long) (bytes[0] & 0xFF) << 40 |
                        (long) (bytes[1] & 0xFF) << 32 |
                        (long) (bytes[2] & 0xFF) << 24 |
                        (long) (bytes[3] & 0xFF) << 16 |
                        (long) (bytes[4] & 0xFF) << 8 |
                        (long) (bytes[5] & 0xFF);
            case 7:
                return (long) (bytes[0] & 0xFF) << 48 |
                        (long) (bytes[1] & 0xFF) << 40 |
                        (long) (bytes[2] & 0xFF) << 32 |
                        (long) (bytes[3] & 0xFF) << 24 |
                        (long) (bytes[4] & 0xFF) << 16 |
                        (long) (bytes[5] & 0xFF) << 8 |
                        (long) (bytes[6] & 0xFF);
            case 8:
                return (long) (bytes[0] & 0xFF) << 56 |
                        (long) (bytes[1] & 0xFF) << 48 |
                        (long) (bytes[2] & 0xFF) << 40 |
                        (long) (bytes[3] & 0xFF) << 32 |
                        (long) (bytes[4] & 0xFF) << 24 |
                        (long) (bytes[5] & 0xFF) << 16 |
                        (long) (bytes[6] & 0xFF) << 8 |
                        (long) (bytes[7] & 0xFF);
            default:
                return new ImmutableByteArray(bytes);
        }
    }

    // TODO specialize
    public static Object getSubToken(Object payload, int startIndex, int endIndex) {
        var length = TokenEncoder.byteSize(payload);
        var newLength = endIndex - startIndex;
        if (length == newLength) {
            return payload;
        } else if (newLength <= Long.BYTES) {
            if (payload instanceof ImmutableByteArray) {
                return of(getImmutableByteArray(payload, startIndex, endIndex, length).getRawArrayUnsafe()); // TODO optimize
            } else {
                long result = (Long) payload;
                result &= -1L >>> (Long.BYTES - length + startIndex) * Byte.SIZE;
                result >>>= (length - endIndex) * Byte.SIZE;

                var rawArray = asRawArray(result);
                var rawArray1 = getImmutableByteArray(payload, startIndex, endIndex, length).getRawArrayUnsafe();
                assert byteSize(result) == newLength : "Expected byte size: " + newLength + ", but got: " + byteSize(result) + " for result: " + result;
                assert Arrays.equals(rawArray, rawArray1) : "Expected raw array: " + Arrays.toString(rawArray1) + ", but got: " + Arrays.toString(rawArray) + " for payload: `" + payload + "` with indices: [" + startIndex + ", " + endIndex + "]";

                return result;
            }

        } else {
            var immutableByteArray = getImmutableByteArray(payload, startIndex, endIndex, length);
            assert immutableByteArray.length() == newLength :
                    "Expected length: " + newLength + ", but got: " + immutableByteArray.length() + " for payload: `" + payload + "` with indices: [" + startIndex + ", " + endIndex + "]";
            return immutableByteArray;
        }
    }

    private static ImmutableByteArray getImmutableByteArray(Object payload, int startIndex, int endIndex, int length) {
        var rawArray = asRawArray(payload, length);
        var from = ImmutableByteArray.from(rawArray);
        return from.getBytesBetween(startIndex, endIndex);
    }

    public static byte[] asRawArray(Object payload) {
        return asRawArray(payload, TokenEncoder.byteSize(payload));
    }

    private static byte[] asRawArray(Object payload, int length) {
        switch (length) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8: {
                // TODO specialize
                byte[] bytes = new byte[length];
                for (long value = (Long) payload; length > 0; ) {
                    bytes[--length] = (byte) (value & 0xFF);
                    value >>>= Byte.SIZE;
                }
                return bytes;
            }
            default:
                return ((ImmutableByteArray) payload).getRawArrayUnsafe();
        }
    }

    public static String asString(Object payload) {
        return new String(asRawArray(payload), UTF_8);
    }

    public List<GptBytePairEncoding.PieceIndexToRank> initializeParts(Object payload) {
        // TODO specialize
        int length = TokenEncoder.byteSize(payload);
        List<GptBytePairEncoding.PieceIndexToRank> parts = new ArrayList<>(length + 1);
        for (int i = 0; i < length + 1; i++) {
            int rank = i < length - 1
                    ? encodeOrDefault(TokenEncoder.getSubToken(payload, i, i + 2), Integer.MAX_VALUE)
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
            return byteArrayEncoders.containsKey((ImmutableByteArray) payload);
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
                result = byteArrayEncoders.get(immutableByteArray);
            }
        }
        return result != null
                ? result
                : Objects.requireNonNull(defaultValue, () -> "Unknown token for encoding: " + payload);
    }
}
