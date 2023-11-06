package com.knuddels.jtokkit;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class StringEncoder {
    private final Map<Integer, String> encodedToDecoded;

    public StringEncoder(Map<String, Integer> encoder) {
        this.encodedToDecoded = new ConcurrentHashMap<>(encoder.size());

        for (Map.Entry<String, Integer> entry : encoder.entrySet()) {
            String key = entry.getKey();
            Integer value = entry.getValue();

            encodedToDecoded.put(value, key);
        }
    }


    public Optional<String> decodeIfPresent(final Integer encodedToken) {
        return Optional.ofNullable(encodedToDecoded.get(encodedToken));
    }

    public Collection<String> getDecodedTokens() {
        return encodedToDecoded.values();
    }
}
