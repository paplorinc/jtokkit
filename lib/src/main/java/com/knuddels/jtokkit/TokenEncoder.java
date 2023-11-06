package com.knuddels.jtokkit;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.IntStream;

final class TokenEncoder {
    private final Map<ImmutableByteArray, Integer>[] groupedEncoder;
    private final Function<ImmutableByteArray, Integer> keyToIndex;
    private final Map<Integer, ImmutableByteArray> encodedToDecoded;

    public TokenEncoder(Map<byte[], Integer> encoder, Function<com.knuddels.jtokkit.ImmutableByteArray, java.lang.Integer> keyToLength, Function<byte[], com.knuddels.jtokkit.ImmutableByteArray> keyMapper) {
        var maxMapCount = 20;
        this.keyToIndex = keyToLength.andThen(integer -> Math.min(integer, maxMapCount) - 1);
        //noinspection unchecked
        this.groupedEncoder = (Map<com.knuddels.jtokkit.ImmutableByteArray, Integer>[]) IntStream.range(0, maxMapCount)
                .mapToObj(i -> new ConcurrentHashMap<com.knuddels.jtokkit.ImmutableByteArray, Integer>())
                .toArray(Map[]::new);

        this.encodedToDecoded = new ConcurrentHashMap<>();

        for (Map.Entry<byte[], Integer> entry : encoder.entrySet()) {
            com.knuddels.jtokkit.ImmutableByteArray key = keyMapper.apply(entry.getKey());
            int keyLength = keyToIndex.apply(key);
            Integer value = entry.getValue();

            groupedEncoder[keyLength].put(key, value);
            encodedToDecoded.put(value, key);
        }
        var UNSIGNED_BYTE_MAX = Byte.MAX_VALUE - Byte.MIN_VALUE + 1;
        var firstMap = groupedEncoder[0];
        if (firstMap.size() == UNSIGNED_BYTE_MAX) {
            int[] array = new int[UNSIGNED_BYTE_MAX];
            firstMap.forEach((immutableByteArray, integer) -> array[immutableByteArray.getFirstByte() - Byte.MIN_VALUE] = integer);
            groupedEncoder[0] = new ReadOnlyByteArrayMap(array);
            System.out.println();
        }
        System.out.println();
    }

    public boolean containsDecodedToken(ImmutableByteArray decodedToken) {
        return groupedEncoder[keyToIndex.apply(decodedToken)].containsKey(decodedToken);
    }

    public Integer encode(final ImmutableByteArray decodedToken) {
        Integer encoded = groupedEncoder[keyToIndex.apply(decodedToken)].get(decodedToken);
        if (encoded == null) {
            throw new IllegalArgumentException("Unknown token for encoding: " + decodedToken);
        }

        return encoded;
    }

    public Integer encodeOrDefault(ImmutableByteArray decodedToken, Integer defaultValue) {
        Integer result = groupedEncoder[keyToIndex.apply(decodedToken)].get(decodedToken);
        return result != null ? result : defaultValue;
    }

    public Optional<ImmutableByteArray> decodeIfPresent(final Integer encodedToken) {
        return Optional.ofNullable(encodedToDecoded.get(encodedToken));
    }
}
