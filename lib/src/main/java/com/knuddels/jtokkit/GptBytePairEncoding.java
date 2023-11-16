package com.knuddels.jtokkit;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingResult;
import com.knuddels.jtokkit.api.GptBytePairEncodingParams;
import org.eclipse.collections.api.factory.primitive.ByteLists;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableByteList;
import org.eclipse.collections.api.list.primitive.MutableIntList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.knuddels.jtokkit.TokenEncoder.MAX_RANK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class GptBytePairEncoding implements Encoding {

    private final String name;
    private final Pattern pattern;
    private final StringEncoder specialTokensEncoder;
    private final LongTokenEncoder longTokenEncoder;
    private final TokenEncoder tokenEncoder;

    private final Map<Integer, byte[]> encodedToDecoded;

    GptBytePairEncoding(GptBytePairEncodingParams params) {
        this.name = params.getName();
        this.pattern = params.getPattern();
        this.specialTokensEncoder = new StringEncoder(params.getSpecialTokensEncoder());

        this.longTokenEncoder = new LongTokenEncoder(params.getEncoder());
        this.tokenEncoder = new TokenEncoder(params.getEncoder());
//        assert longTokenEncoder.length() + tokenEncoder.length() == params.getEncoder().size();

        this.encodedToDecoded = new ConcurrentHashMap<>(params.getEncoder().size());
        params.getEncoder().forEach((k, v) -> {
            encodedToDecoded.put(v, k);
        });
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
            return new EncodingResult(IntLists.immutable.empty(), -1, false);
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
            return new EncodingResult(IntLists.immutable.empty(), -1, false);
        }

        MutableIntList out = IntLists.mutable.empty();
        int tokenCount = 0;
        for (Matcher matcher = pattern.matcher(text); tokenCount < maxTokenCount && matcher.find(); ) {
            String group = matcher.group();
            // System.out.println("Matched: `" + group + "`");
            byte[] bytes = group.getBytes(UTF_8);
            if (LongTokenEncoder.accepts(bytes)) {
                tokenCount += longTokenEncoder.addTokensAndGetCount(tokenEncoder, maxTokenCount, keepEncodings, bytes, out);
            } else if (TokenEncoder.accepts(bytes)) {
                tokenCount += tokenEncoder.addTokensAndGetCount(longTokenEncoder, maxTokenCount, keepEncodings, bytes, out);
            } else {
                throw new IllegalStateException();
            }
        }

        if (maxTokenCount != Integer.MAX_VALUE) {
            // Make sure we didn't break the multibyte character
            for (int tokensToRemove = 0; tokensToRemove <= out.size(); tokensToRemove++) {
                MutableIntList tokens = IntLists.mutable.empty();
                for (int i = 0; i < out.size() - tokensToRemove; i++) {
                    tokens.add(out.get(i));
                }
                // TODO optimize
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
            matchedCharacterCount += matcher.end() - matcher.start();
        }
        return matchedCharacterCount;
    }

    // TODO limit regex to max token size?
    @Override
    public int countTokens(String text) {
        return encodeInternal(text, Integer.MAX_VALUE, false).getTokenCount();
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
    public byte[] decodeBytes(IntList tokens) { // TODO sized
        MutableByteList out = ByteLists.mutable.withInitialCapacity(2 * tokens.size());
        tokens.forEach(token -> {
            byte[] decodedToken = decodeToken(token);
            for (byte b : decodedToken) {
                out.add(b);
            }
        });
        return out.toArray();
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
