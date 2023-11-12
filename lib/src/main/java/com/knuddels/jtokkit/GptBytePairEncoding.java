package com.knuddels.jtokkit;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingResult;
import com.knuddels.jtokkit.api.GptBytePairEncodingParams;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.knuddels.jtokkit.TokenEncoder.MAX_RANK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;

public class GptBytePairEncoding implements Encoding {

    private final String name;
    private final Pattern pattern;
    private final StringEncoder specialTokensEncoder;
    private final LongTokenEncoder longTokenEncoder;
    private final TokenEncoder tokenEncoder;

    GptBytePairEncoding(GptBytePairEncodingParams params) {
        this.name = params.getName();
        this.pattern = params.getPattern();
        this.specialTokensEncoder = new StringEncoder(params.getSpecialTokensEncoder());
        this.longTokenEncoder = new LongTokenEncoder(params.getEncoder());
        this.tokenEncoder = new TokenEncoder(params.getEncoder());
        assert longTokenEncoder.length() + tokenEncoder.length() == params.getEncoder().size();
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
    public List<Integer> encode(String text) {
        return encodeInternal(text, -1, true).getTokens();
    }

    @Override
    public EncodingResult encode(String text, int maxTokenCount) {
        return encodeInternal(text, maxTokenCount, true);
    }

    private EncodingResult encodeInternal(String text, int maxTokenCount, boolean keepEncodings) {
        if (text == null) {
            return new EncodingResult(emptyList(), -1, false);
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
    public List<Integer> encodeOrdinary(String text) {
        return encodeOrdinaryInternal(text, -1, true).getTokens();
    }

    @Override
    public EncodingResult encodeOrdinary(String text, int maxTokenCount) {
        return encodeOrdinaryInternal(text, maxTokenCount, true);
    }

    private EncodingResult encodeOrdinaryInternal(String text, int maxTokenCount, boolean keepEncodings) {
        if (text == null) {
            return new EncodingResult(emptyList(), -1, false);
        }

        List<Integer> out = new ArrayList<>();
        int tokenCount = 0;
        for (Matcher matcher = pattern.matcher(text); matcher.find() && maxTokenCountNotReached(maxTokenCount, tokenCount); ) {
            var group = matcher.group();
            byte[] bytes = group.getBytes(UTF_8);
            if (LongTokenEncoder.accepts(bytes)) {
                tokenCount += longTokenEncoder.addTokensAndGetCount(maxTokenCount, keepEncodings, bytes, out);
            } else {
                tokenCount += tokenEncoder.addTokensAndGetCount(maxTokenCount, keepEncodings, bytes, out);
            }
        }

        if (maxTokenCount >= 0) {
            // Make sure we didn't break the multibyte character
            for (int tokensToRemove = 0; tokensToRemove <= out.size(); tokensToRemove++) {
                List<Integer> tokens = out.subList(0, out.size() - tokensToRemove);
                String decoded = decode(tokens);
                if (text.startsWith(decoded)) {
                    // If decoded text is equal to the head of the original text, we can safely return the tokens
                    return new EncodingResult(tokens, -1, text.length() > decoded.length());
                }
            }
        }

        return new EncodingResult(out, tokenCount, false);
    }

    @Override
    public long countSplitChars(String text) {
        long matchedCharacterCount = 0;
        for (Matcher matcher = pattern.matcher(text); matcher.find(); ) {
            matchedCharacterCount += matcher.group().length();
        }
        return matchedCharacterCount;
    }

    // TODO limit regex to max token size?
    @Override
    public int countTokens(String text) {
        return encodeInternal(text, -1, false).getTokenCount();
    }

    @Override
    public int countTokensOrdinary(String text) {
        return encodeOrdinary(text).size();
    }

    @Override
    public String decode(List<Integer> tokens) {
        return new String(decodeBytes(tokens), UTF_8);
    }

    @Override
    public byte[] decodeBytes(List<Integer> tokens) {
        List<Byte> out = new ArrayList<>();
        for (int token : tokens) {
            byte[] decodedToken = decodeToken(token);
            for (byte b : decodedToken) {
                out.add(b);
            }
        }

        byte[] outArray = new byte[out.size()];
        for (int i = 0; i < out.size(); i++) {
            outArray[i] = out.get(i);
        }
        return outArray;
    }

    @Override
    public String getName() {
        return name;
    }

    private boolean maxTokenCountNotReached(int maxTokenCount, int tokenCount) {
        return maxTokenCount < 0 || tokenCount < maxTokenCount;
    }

    public byte[] decodeToken(int token) {
        assert token != MAX_RANK;
        byte[] decodedToken = longTokenEncoder.decodeIfPresent(token);
        if (decodedToken == null) {
            decodedToken = tokenEncoder.decodeIfPresent(token);
            if (decodedToken == null) {
                decodedToken = specialTokensEncoder.decodeIfPresent(token);
            }
        }
        return Objects.requireNonNull(decodedToken);
    }
}
