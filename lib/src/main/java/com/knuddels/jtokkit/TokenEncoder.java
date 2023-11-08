package com.knuddels.jtokkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.knuddels.jtokkit.GptBytePairEncoding.PieceIndexToRank;
import static java.nio.charset.StandardCharsets.UTF_8;

final class TokenEncoder {
    public static final int MAX_RANK = Integer.MAX_VALUE;
    private final Map<ImmutableByteArray, Integer> immutableByteArrayEncoders = new ConcurrentHashMap<>(); // open addressing for optimal collisions
    private final Map<Integer, byte[]> encodedToDecoded;

    public TokenEncoder(Map<byte[], Integer> encoder) {
        this.encodedToDecoded = new ConcurrentHashMap<>(encoder.size());

        encoder.forEach((k, v) -> {
            immutableByteArrayEncoders.put(new ImmutableByteArray(k), v);
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


    public List<PieceIndexToRank> initializeParts(ImmutableByteArray payload) {
        int length = payload.length();
        List<PieceIndexToRank> parts = new ArrayList<>(length + 1);
        assert length > 1 : "Already filtered out";
        if (length == 2) {
            parts.add(new PieceIndexToRank(0, encode(payload)));
        } else {
            for (int i = 0; i < length - 1; i++) {
                ImmutableByteArray subToken = getSubToken(payload, i, i + 2);
                parts.add(new PieceIndexToRank(i, encode(subToken)));
            }
        }
        parts.add(new PieceIndexToRank(length - 1, MAX_RANK));
        parts.add(new PieceIndexToRank(length, MAX_RANK));
        return parts;
    }

    public byte[] decodeIfPresent(int encodedToken) {
        return encodedToDecoded.get(encodedToken);
    }

    public boolean containsDecodedToken(ImmutableByteArray payload) {
        return immutableByteArrayEncoders.containsKey(payload);
    }

    public int encode(ImmutableByteArray payload) {
        Integer result = immutableByteArrayEncoders.get(payload); // getOrDefault is slower
        return result != null ? result : MAX_RANK;
    }
}