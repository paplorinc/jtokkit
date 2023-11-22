package com.knuddels.jtokkit;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingResult;
import com.knuddels.jtokkit.api.GptBytePairEncodingParams;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.bytes.ByteList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.knuddels.jtokkit.TokenEncoder.MAX_RANK;
import static java.lang.Character.charCount;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class GptBytePairEncoding implements Encoding {

    private final String name;
    private final Pattern pattern;
    private final StringEncoder specialTokensEncoder;

    private final CompactTokenEncoder compactTokenEncoder;
    private final TokenEncoder tokenEncoder;

    private final Map<Integer, byte[]> encodedToDecoded;

    GptBytePairEncoding(GptBytePairEncodingParams params) {
        this.name = params.getName();
        this.pattern = params.getPattern();
        this.specialTokensEncoder = new StringEncoder(params.getSpecialTokensEncoder());

        this.compactTokenEncoder = new CompactTokenEncoder(params.getEncoder());
        this.tokenEncoder = new TokenEncoder(params.getEncoder());
        assert compactTokenEncoder.length() + tokenEncoder.length() == params.getEncoder().size()
                : compactTokenEncoder.length() + "+" + tokenEncoder.length() + " != " + params.getEncoder().size();

        this.encodedToDecoded = new ConcurrentHashMap<>(params.getEncoder().size());
        params.getEncoder().forEach((k, v) -> encodedToDecoded.put(v, k));
    }

    public static int index(long indexedRank) {
        return (int) (indexedRank >>> Integer.SIZE);
    }

    public static int rank(long indexedRank) {
        return (int) indexedRank;
    }

    public static long combine(long index, int rank) {
        return (index << Integer.SIZE) | rank;
    }

    public static long setRank(long indexedRank, int rank) {
        return indexedRank & (-1L << Integer.SIZE) | rank;
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
        for (String specialToken : specialTokensEncoder.getDecodedTokens()) {
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

        IntArrayList out = new IntArrayList();
        int[] tokenCount = {0};
        if ("cl100k_base".equals(name)) {
            var codepoints = text.codePoints().toArray();
            Parser.split(codepoints, (start, end) -> {
                byte[] bytes = new String(codepoints, start, end - start).getBytes(UTF_8);
                processTokens(maxTokenCount, keepEncodings, bytes, tokenCount, out);
                return tokenCount[0] >= maxTokenCount;
            });
        } else {
            for (Matcher matcher = pattern.matcher(text); tokenCount[0] < maxTokenCount && matcher.find(); ) {
                byte[] bytes = matcher.group().getBytes(UTF_8);
                processTokens(maxTokenCount, keepEncodings, bytes, tokenCount, out);
            }
        }

        if (maxTokenCount != Integer.MAX_VALUE) {
            // Make sure we didn't break the multibyte character
            for (int tokensToRemove = 0; tokensToRemove <= out.size(); tokensToRemove++) {
                var size = out.size() - tokensToRemove;
                IntArrayList tokens = new IntArrayList(size);
                for (int i = 0; i < size; i++) {
                    tokens.add(out.getInt(i));
                }
                // TODO optimize
                String decoded = decode(tokens);
                if (text.startsWith(decoded)) {
                    // If decoded text is equal to the head of the original text, we can safely return the tokens
                    return new EncodingResult(tokens, -1, text.length() > decoded.length());
                }
            }
        }

        return new EncodingResult(out, tokenCount[0], false);
    }

    private void processTokens(int maxTokenCount, boolean keepEncodings, byte[] bytes, int[] tokenCount, IntArrayList out) {
        if (CompactTokenEncoder.accepts(bytes.length)) {
            tokenCount[0] += compactTokenEncoder.addTokensAndGetCount(maxTokenCount, keepEncodings, bytes, out);
        } else if (TokenEncoder.accepts(bytes.length)) {
            tokenCount[0] += tokenEncoder.addTokensAndGetCount(compactTokenEncoder, maxTokenCount, keepEncodings, bytes, out);
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public long countSplitChars(String text) {
        long[] matchedCharacterCount = {0L};
        var codepoints = text.codePoints().toArray();
        Parser.split(codepoints, (start, end) -> {
            for (int i = start; i < end; i++) {
                matchedCharacterCount[0] += charCount(codepoints[i]);
            }
            return false;
        });
        return matchedCharacterCount[0];
    }

    // TODO limit regex to max token size?
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
            byte[] decodedToken = decodeToken(token);
            for (byte b : decodedToken) {
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
        assert token != MAX_RANK;
        byte[] decodedToken = encodedToDecoded.computeIfAbsent(token, specialTokensEncoder::decodeIfPresent);
        return requireNonNull(decodedToken);
    }
}
