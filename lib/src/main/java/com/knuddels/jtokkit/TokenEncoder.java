package com.knuddels.jtokkit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

final class TokenEncoder {
    private final Map<Number, Integer> longEncoders = new ConcurrentHashMap<>(); // TODO ImmutableLongIntMap
    private final Map<ImmutableByteArray, Integer> byteArrayEncoders = new ConcurrentHashMap<>();
    private final Map<Integer, byte[]> encodedToDecoded;

    public TokenEncoder(Map<byte[], Integer> encoder) {
        this.encodedToDecoded = new ConcurrentHashMap<>(encoder.size());

        encoder.forEach((k, v) -> {
            Object key = of(k);
            if (key instanceof Number) {
                longEncoders.put((Number) key, v);
            } else {
                byteArrayEncoders.put((ImmutableByteArray) key, v);
            }
            encodedToDecoded.put(v, k);
        });
    }

    public static int byteSize(Object payload) {
        if (payload instanceof Integer) {
            return 1 + (Integer.SIZE - Integer.numberOfLeadingZeros((Integer) payload) - 1) / Byte.SIZE;
        } else if (payload instanceof Long) {
            return 1 + (Long.SIZE - Long.numberOfLeadingZeros((Long) payload) - 1) / Byte.SIZE;
        } else {
            return ((ImmutableByteArray) payload).length();
        }
    }

    public static Object of(String payload) {
        byte[] bytes = payload.getBytes(UTF_8);
        Object result = of(bytes);
        assert Objects.equals(bytes.length, byteSize(result));
        assert Arrays.equals(bytes, asRawArray(result));
        assert Objects.equals(payload, asString(result));
        return result;
    }

    private static Object of(byte[] bytes) {
        switch (bytes.length) {
            case 1:
                return bytes[0] & 0xFF;
            case 2:
                return ((bytes[0] & 0xFF) << 8) |
                        (bytes[1] & 0xFF);
            case 3:
                return ((bytes[0] & 0xFF) << 16) |
                        ((bytes[1] & 0xFF) << 8) |
                        (bytes[2] & 0xFF);
            case 4:
                return ((bytes[0] & 0xFF) << 24) |
                        ((bytes[1] & 0xFF) << 16) |
                        ((bytes[2] & 0xFF) << 8) |
                        (bytes[3] & 0xFF);
            case 5:
                return ((long) (bytes[0] & 0xFF) << 32) |
                        ((long) (bytes[1] & 0xFF) << 24) |
                        ((long) (bytes[2] & 0xFF) << 16) |
                        ((long) (bytes[3] & 0xFF) << 8) |
                        ((long) (bytes[4] & 0xFF));
            case 6:
                return ((long) (bytes[0] & 0xFF) << 40) |
                        ((long) (bytes[1] & 0xFF) << 32) |
                        ((long) (bytes[2] & 0xFF) << 24) |
                        ((long) (bytes[3] & 0xFF) << 16) |
                        ((long) (bytes[4] & 0xFF) << 8) |
                        ((long) (bytes[5] & 0xFF));
            case 7:
                return ((long) (bytes[0] & 0xFF) << 48) |
                        ((long) (bytes[1] & 0xFF) << 40) |
                        ((long) (bytes[2] & 0xFF) << 32) |
                        ((long) (bytes[3] & 0xFF) << 24) |
                        ((long) (bytes[4] & 0xFF) << 16) |
                        ((long) (bytes[5] & 0xFF) << 8) |
                        ((long) (bytes[6] & 0xFF));
            case 8:
                return ((long) (bytes[0] & 0xFF) << 56) |
                        ((long) (bytes[1] & 0xFF) << 48) |
                        ((long) (bytes[2] & 0xFF) << 40) |
                        ((long) (bytes[3] & 0xFF) << 32) |
                        ((long) (bytes[4] & 0xFF) << 24) |
                        ((long) (bytes[5] & 0xFF) << 16) |
                        ((long) (bytes[6] & 0xFF) << 8) |
                        ((long) (bytes[7] & 0xFF));
            default:
                return new ImmutableByteArray(bytes, 0, bytes.length);
        }
    }

    // TODO specialize
    public static Object getBytesBetween(Object payload, int startIndex, int endIndex) {
        return ImmutableByteArray.from(asRawArray(payload))
                .getBytesBetween(startIndex, endIndex);
    }

    public static byte[] asRawArray(Object payload) {
        return asRawArray(payload, TokenEncoder.byteSize(payload));
    }

    private static byte[] asRawArray(Object payload, int length) {
        switch (length) {
            case 1:
            case 2:
            case 3:
            case 4: {
                byte[] bytes = new byte[length];
                for (int value = (Integer) payload; length > 0; ) {
                    bytes[--length] = (byte) (value & 0xFF);
                    value >>>= Byte.SIZE;
                }
                return bytes;
            }
            case 5:
            case 6:
            case 7:
            case 8: {
                byte[] bytes = new byte[length];
                for (long value = (Long) payload; length > 0; ) {
                    bytes[--length] = (byte) (value & 0xFF);
                    value >>>= Byte.SIZE;
                }
                return bytes;
            }
            default:
                return ((ImmutableByteArray) payload).getRawArray();
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
                    ? encodeOrDefault(TokenEncoder.getBytesBetween(payload, i, i + 2), Integer.MAX_VALUE)
                    : Integer.MAX_VALUE;
            parts.add(new GptBytePairEncoding.PieceIndexToRank(i, rank));
        }
        return parts;
    }

    public byte[] decodeIfPresent(Integer encodedToken) {
        return encodedToDecoded.get(encodedToken);
    }

    public boolean containsDecodedToken(Object payload) {
        if (payload instanceof Number) {
            return longEncoders.containsKey((Number) payload);
        } else {
            return byteArrayEncoders.containsKey((ImmutableByteArray) payload);
        }
    }

    public Integer encodeOrDefault(Object payload, Integer defaultValue) {
        Integer result;
        if (payload instanceof Number) {
            result = longEncoders.get((Number) payload);
        } else {
            result = byteArrayEncoders.get((ImmutableByteArray) payload);
        }
        return result != null
                ? result
                : Objects.requireNonNull(defaultValue, () -> "Unknown token for encoding: " + payload);
    }
}
