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

    final Int2ObjectMap<byte[]> encodedToDecoded;
    private final String name;
    private final Pattern pattern;
    private final StringEncoder specialTokensEncoder;
    private final CompactTokenEncoder compactTokenEncoder;
    private final TokenEncoder tokenEncoder;
//    GptBytePairEncodingOriginal bytePairEncodingOriginal = GptBytePairEncodingOriginal.getEncoder(); // TODO used for testing

    GptBytePairEncoding(GptBytePairEncodingParams params) {
        this.name = params.getName();
        this.pattern = params.getPattern();
        this.specialTokensEncoder = new StringEncoder(params.getSpecialTokensEncoder());

        this.compactTokenEncoder = new CompactTokenEncoder(params.getEncoder());
        this.tokenEncoder = new TokenEncoder(params.getEncoder());
        assert compactTokenEncoder.length() + tokenEncoder.length() == params.getEncoder().size()
                : compactTokenEncoder.length() + "+" + tokenEncoder.length() + " != " + params.getEncoder().size();

        this.encodedToDecoded = new Int2ObjectOpenHashMap<>(params.getEncoder().size());
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

        checkForSpecialTokens(text);

        return encodeOrdinaryInternal(text, maxTokenCount, keepEncodings);
    }

    private void checkForSpecialTokens(String text) {
        for (var specialToken : specialTokensEncoder.getDecodedTokens()) {
            if (text.contains(specialToken)) {
                throw new UnsupportedOperationException("Encoding special tokens is not supported yet.");
            }
        }
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

//        Matcher[] reference = {null};
//        assert (reference[0] = bytePairEncodingOriginal.pattern.matcher(text)) != null;

        var out = new IntArrayList();
        var ranks = new IntArrayList();
        var tokenCount = 0;
        if ("cl100k_base".equals(name)) {
            tokenCount = Cl100kParser.split(text, maxTokenCount, (utf8Bytes, start, end) -> {
//                var s = new String(utf8Bytes, start, end - start, UTF_8);
//                System.out.println("`" + s + "`");
//                assert reference[0].find() : "`" + s + "` in `" + text + "`";
//                assert Arrays.equals(reference[0].group().getBytes(UTF_8), Arrays.copyOfRange(utf8Bytes, start, end)) : "`" + reference[0].group() + "` != `" + s + "` in `" + text + "`";
                return processTokens(maxTokenCount, keepEncodings, utf8Bytes, start, end, out, ranks);
            });
        } else {
            for (var matcher = pattern.matcher(text); tokenCount < maxTokenCount && matcher.find(); ) {
                var bytes = matcher.group().getBytes(UTF_8);
                tokenCount += processTokens(maxTokenCount, keepEncodings, bytes, 0, bytes.length, out, ranks);
            }
        }

        if (maxTokenCount != Integer.MAX_VALUE) {
            // Make sure we didn't break the multibyte character
            for (var tokensToRemove = 0; tokensToRemove <= out.size(); tokensToRemove++) {
                var size = out.size() - tokensToRemove;
                var tokens = new IntArrayList(size);
                for (var i = 0; i < size; i++) {
                    tokens.add(out.getInt(i));
                }
                // TODO optimize
                var decoded = decode(tokens);
                if (text.startsWith(decoded)) {
                    // If decoded text is equal to the head of the original text, we can safely return the tokens
                    return new EncodingResult(tokens, -1, text.length() > decoded.length());
                }
            }
        }

        return new EncodingResult(out, tokenCount, false);
    }

    private int processTokens(int maxTokenCount, boolean keepEncodings, byte[] utf8Bytes, int start, int end, IntArrayList out, IntArrayList ranks) {
        int size = end - start;
        if (CompactTokenEncoder.accepts(size)) {
            return compactTokenEncoder.addTokensAndGetCount(maxTokenCount, keepEncodings, utf8Bytes, start, end, out, ranks);
        } else {
            assert TokenEncoder.accepts(size);
            return tokenEncoder.addTokensAndGetCount(compactTokenEncoder, maxTokenCount, keepEncodings, utf8Bytes, start, end, out, ranks);
        }
    }

    @Override
    public long countBytes(String text) {
        var matchedCharacterCount = new long[]{0L};
        Cl100kParser.split(text, Integer.MAX_VALUE, (utf8Bytes, start, end) -> {
            matchedCharacterCount[0] += end - start;
            return 0;
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
        ByteList out = new ByteArrayList(2 * tokens.size());
        tokens.forEach(token -> {
            var decodedToken = decodeToken(token);
            for (var b : decodedToken) {
                out.add(b);
            }
        });
        return out.toByteArray();
    }

    @Override
    public String getName() {
        return name;
    }

    public byte[] decodeToken(int token) {
        var decodedToken = encodedToDecoded.computeIfAbsent(token, specialTokensEncoder::decodeIfPresent);
        return requireNonNull(decodedToken);
    }
}
