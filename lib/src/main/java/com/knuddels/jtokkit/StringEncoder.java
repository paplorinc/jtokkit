package com.knuddels.jtokkit;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

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


    public byte[] decodeIfPresent(Integer encodedToken) {
        var result = encodedToDecoded.get(encodedToken);
        return result != null ? result.getBytes(UTF_8) : null;
    }

    public Collection<String> getDecodedTokens() {
        return encodedToDecoded.values();
    }
}
