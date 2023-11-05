package com.knuddels.jtokkit;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toSet;

/**
 * A TokenEncoder is used to encode and decode tokens. It is initialized with a map
 * that contains the decoded tokens as keys and the encoded tokens as values. The
 * TokenEncoder can then be used to encode and decode tokens.
 *
 * @param <K> the type of the decoded tokens
 * @param <V> the type of the encoded tokens
 */
final class TokenEncoder<K, V> {

    private final Map<K, V>[] groupedEncoder;
    private final Function<K, Integer> keyToIndex;
    private final Map<V, K> encodedToDecoded;

    /**
     * Creates a new TokenEncoder with the given input map. The keys of the map are
     * the decoded tokens and the values are the encoded tokens.
     *
     * @param input the input map
     */
    public TokenEncoder(Map<K, V> input, Function<K, Integer> keyToIndex) {
        this(input, keyToIndex, Function.identity());
    }

    /**
     * Creates a new TokenEncoder with the given input map. The keys of the map are
     * the decoded tokens and the values are the encoded tokens. The keyMapper is
     * applied to the keys of the input map before they are added to the internal
     * maps.
     *
     * @param encoder   the input map
     * @param keyMapper the key mapper
     */
    public <T> TokenEncoder(Map<T, V> encoder, Function<K, Integer> keyToLength, Function<T, K> keyMapper) {
        var maxMapCount = 20;
        this.keyToIndex = keyToLength.andThen(integer -> Math.min(integer, maxMapCount) - 1);
        //noinspection unchecked
        this.groupedEncoder = (Map<K, V>[]) IntStream.range(0, maxMapCount)
                .mapToObj(i -> new ConcurrentHashMap<K, V>())
                .toArray(Map[]::new);
        this.encodedToDecoded = new ConcurrentHashMap<>(encoder.size());

        for (Map.Entry<T, V> entry : encoder.entrySet()) {
            K key = keyMapper.apply(entry.getKey());
            int keyLength = keyToIndex.apply(key);
            V value = entry.getValue();

            groupedEncoder[keyLength].put(key, value);
            encodedToDecoded.put(value, key);
        }
        var UNSIGNED_BYTE_MAX = Byte.MAX_VALUE - Byte.MIN_VALUE + 1;
        var firstMap = groupedEncoder[0];
        if (firstMap.size() == UNSIGNED_BYTE_MAX) {
            int[] array = new int[UNSIGNED_BYTE_MAX];
            firstMap.forEach((k, v) -> array[((ImmutableByteArray) k).getFirstByte() - Byte.MIN_VALUE] = (Integer) v);
            groupedEncoder[0] = (Map<K, V>) new ReadOnlyByteArrayMap(array);
            System.out.println();
        }
        System.out.println();
    }

    /**
     * Checks if the given decoded token is contained in this encoder.
     *
     * @param decodedToken the decoded token
     * @return true if the decoded token is contained in this encoder, false otherwise
     */
    public boolean containsDecodedToken(K decodedToken) {
        return groupedEncoder[keyToIndex.apply(decodedToken)].containsKey(decodedToken);
    }

    /**
     * Encodes the given decoded token.
     *
     * @param decodedToken the decoded token
     * @return the encoded token
     * @throws IllegalArgumentException if the decoded token is not contained in this encoder
     */
    public V encode(final K decodedToken) {
        V encoded = groupedEncoder[keyToIndex.apply(decodedToken)].get(decodedToken);
        if (encoded == null) {
            throw new IllegalArgumentException("Unknown token for encoding: " + decodedToken);
        }

        return encoded;
    }

    /**
     * Encodes the given decoded token if it is contained in this encoder. Otherwise,
     * an empty optional is returned.
     *
     * @param decodedToken the decoded token
     * @return the encoded token or an empty optional
     */
    public V encodeOrDefault(K decodedToken, V defaultValue) {
        V result = groupedEncoder[keyToIndex.apply(decodedToken)].get(decodedToken);
        return result != null ? result : defaultValue;
    }

    /**
     * Decodes the given encoded token if it is contained in this encoder. Otherwise,
     * an empty optional is returned.
     *
     * @param encodedToken the encoded token
     * @return the decoded token or an empty optional
     */
    public Optional<K> decodeIfPresent(final V encodedToken) {
        return Optional.ofNullable(encodedToDecoded.get(encodedToken));
    }

    /**
     * Returns a set of all decoded tokens contained in this encoder.
     *
     * @return a set of all decoded tokens
     */
    public Set<K> getDecodedTokens() {
        return Arrays.stream(groupedEncoder).flatMap(x -> x.keySet().stream()).collect(toSet()); // TODO slow
    }
}
