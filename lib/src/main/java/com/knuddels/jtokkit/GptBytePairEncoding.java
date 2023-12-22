package com.knuddels.jtokkit;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingResult;
import com.knuddels.jtokkit.api.GptBytePairEncodingParams;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class GptBytePairEncoding implements Encoding {

    private final String name;
    private final Pattern pattern;
    private final CompactTokenEncoder compactEncoder;
    private final TokenEncoder encoder;
    private final SpecialEncoder specialEncoder;
    private final Int2ObjectMap<byte[]> encodedToDecoded;

    GptBytePairEncoding(GptBytePairEncodingParams params) {
        this.name = params.getName();
        this.pattern = params.getPattern();
        this.specialEncoder = new SpecialEncoder(params.getSpecialTokensEncoder());

        this.compactEncoder = new CompactTokenEncoder(params.getEncoder());
        this.encoder = new TokenEncoder(params.getEncoder());
        assert compactEncoder.length() + encoder.length() == params.getEncoder().size()
                : compactEncoder.length() + "+" + encoder.length() + " != " + params.getEncoder().size();

        this.encodedToDecoded = new Int2ObjectOpenHashMap<>(params.getEncoder().size(), 0.4f);
        params.getEncoder().forEach((k, v) -> encodedToDecoded.put((int) v, k));
    }

    @Override
    public IntList encode(String text) {
        return encode(text, Integer.MAX_VALUE).getTokens();
    }

    @Override
    public EncodingResult encode(String text, int maxTokenCount) {
        return encodeInternal(text, maxTokenCount, true);
    }

    private EncodingResult encodeInternal(String text, int maxTokenCount, boolean keepEncodings) {
        if (text == null) {
            return new EncodingResult(IntArrayList.of(), -1, false);
        }

        specialEncoder.checkForSpecialTokens(text);

        return encodeOrdinaryInternal(text, maxTokenCount, keepEncodings);
    }

    @Override
    public IntList encodeOrdinary(String text) {
        return encodeOrdinary(text, Integer.MAX_VALUE).getTokens();
    }

    @Override
    public EncodingResult encodeOrdinary(String text, int maxTokenCount) {
        return encodeOrdinaryInternal(text, maxTokenCount, true);
    }

    private EncodingResult encodeOrdinaryInternal(String text, int maxTokenCount, boolean keepEncodings) {
        if (text == null) {
            return new EncodingResult(IntArrayList.of(), -1, false);
        }

        var out = new IntArrayList();
        var tokenCount = encodeOrdinaryInternal(text, maxTokenCount, keepEncodings, out);

        if (keepEncodings && maxTokenCount != Integer.MAX_VALUE) {
            // Make sure we didn't break the multibyte character
            for (var tokensToRemove = 0; tokensToRemove <= out.size(); tokensToRemove++) {
                var size = out.size() - tokensToRemove;
                var tokens = new IntArrayList(size);
                for (var i = 0; i < size; i++) {
                    tokens.add(out.getInt(i));
                }
                var decoded = decode(tokens);
                if (text.startsWith(decoded)) {
                    // If decoded text is equal to the head of the original text, we can safely return the tokens
                    return new EncodingResult(tokens, -1, text.length() > decoded.length());
                }
            }
        }

        return new EncodingResult(out, tokenCount, false);
    }

    int encodeOrdinaryInternal(String text, int maxTokenCount, boolean keepEncodings, IntArrayList out) {
        var tokenCount = 0;
        var ranks = new IntArrayList();
        for (var matcher = pattern.matcher(text); tokenCount < maxTokenCount && matcher.find(); ) {
            var bytes = ByteArrayList.wrap(matcher.group().getBytes(UTF_8));
            tokenCount += processTokens(maxTokenCount, keepEncodings, bytes, out, ranks);
        }
        return tokenCount;
    }

    int processTokens(int maxTokenCount, boolean keepEncodings, ByteArrayList utf8Bytes, IntArrayList out, IntArrayList ranks) {
        var size = utf8Bytes.size();
        if (CompactTokenEncoder.accepts(size)) {
            return compactEncoder.addTokensAndGetCount(maxTokenCount, keepEncodings, utf8Bytes, out, ranks);
        } else {
            assert TokenEncoder.accepts(size);
            return encoder.addTokensAndGetCount(compactEncoder, maxTokenCount, keepEncodings, utf8Bytes, out, ranks);
        }
    }

    @Override
    public long countBytes(String text) {
        var matchedCharacterCount = new long[]{0L};
        Cl100kParser.split(text, utf8Bytes -> {
            matchedCharacterCount[0] += utf8Bytes.size();
            return false;
        });
        return matchedCharacterCount[0];
    }

    @Override
    public int countTokens(String text) {
        return countTokens(text, Integer.MAX_VALUE);
    }

    @Override
    public int countTokens(String text, int maxValue) {
        return encodeInternal(text, maxValue, false).getTokenCount();
    }

    @Override
    public int countTokensOrdinary(String text) {
        return encodeOrdinary(text).size();
    }

    @Override
    public String decode(IntList tokens) {
        return new String(decodeBytes(tokens), UTF_8);
    }

    @Override
    public byte[] decodeBytes(IntList tokens) {
        ByteList out = new ByteArrayList(10 * tokens.size());
        tokens.forEach(token -> out.addElements(out.size(), decodeToken(token)));
        return out.toByteArray();
    }

    @Override
    public String getName() {
        return name;
    }

    private byte[] decodeToken(int token) {
        var decodedToken = encodedToDecoded.computeIfAbsent(token, specialEncoder::decodeIfPresent);
        return requireNonNull(decodedToken, "Unknown token for decoding: " + token);
    }
}
