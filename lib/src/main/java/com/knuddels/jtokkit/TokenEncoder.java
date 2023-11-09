package com.knuddels.jtokkit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

final class TokenEncoder {
    public static final int MAX_RANK = Integer.MAX_VALUE;
    private final Map<ImmutableByteArray, Integer> byteArrayEncoders;
    private final Map<Integer, byte[]> encodedToDecoded;

    public TokenEncoder(Map<byte[], Integer> encoder) {
        this.byteArrayEncoders = new ConcurrentHashMap<>(encoder.size()); // open addressing for optimal collisions
        this.encodedToDecoded = new ConcurrentHashMap<>(encoder.size());
        encoder.forEach((k, v) -> {
            assert v != MAX_RANK;
            byteArrayEncoders.put(new ImmutableByteArray(k), v);
            encodedToDecoded.put(v, k);
        });
    }

    public static ImmutableByteArray of(String payload) {
        byte[] bytes = payload.getBytes(UTF_8);
        return new ImmutableByteArray(bytes);
    }

    public static ImmutableByteArray getSubToken(ImmutableByteArray payload, int startIndex, int endIndex) {
        int length = payload.length();
        int newLength = endIndex - startIndex;
        if (length == newLength) {
            return payload;
        } else {
            ImmutableByteArray immutableByteArray = getImmutableByteArray(payload, startIndex, endIndex);
            assert immutableByteArray.length() == newLength :
                    "Expected length: " + newLength + ", but got: " + immutableByteArray.length() + " for payload: `" + payload + "` with indices: [" + startIndex + ", " + endIndex + "]";
            return immutableByteArray;
        }
    }

    private static ImmutableByteArray getImmutableByteArray(ImmutableByteArray payload, int startIndex, int endIndex) {
        return payload.getBytesBetween(startIndex, endIndex);
    }

    public byte[] decodeIfPresent(int encodedToken) {
        return encodedToDecoded.get(encodedToken);
    }

    public int encode(ImmutableByteArray payload) {
        Integer result = byteArrayEncoders.get(payload); // getOrDefault is slower
        return result != null ? result : MAX_RANK;
    }
}