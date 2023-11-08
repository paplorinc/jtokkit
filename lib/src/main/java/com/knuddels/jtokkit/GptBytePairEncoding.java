package com.knuddels.jtokkit;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingResult;
import com.knuddels.jtokkit.api.GptBytePairEncodingParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.knuddels.jtokkit.TokenEncoder.MAX_RANK;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Implementation of the byte pair encoding algorithm as used by the OpenAI tiktoken tokenizer.
 */
public class GptBytePairEncoding implements Encoding {

    private final String name;
    private final Pattern pattern;
    private final TokenEncoder encoder;
    private final StringEncoder specialTokensEncoder;

    /**
     * Creates a new instance of {@link GptBytePairEncoding}.
     *
     * @param params the parameters to use for the encoding
     */
    GptBytePairEncoding(GptBytePairEncodingParams params) {
        this.name = params.getName();
        this.pattern = params.getPattern();
        this.encoder = new TokenEncoder(params.getEncoder());
        this.specialTokensEncoder = new StringEncoder(params.getSpecialTokensEncoder());
    }

    @Override
    public List<Integer> encode(String text) {
        return encodeInternal(text, -1).getTokens();
    }

    @Override
    public EncodingResult encode(String text, int maxTokenCount) {
        return encodeInternal(text, maxTokenCount);
    }

    private EncodingResult encodeInternal(String text, int maxTokenCount) {
        if (text == null) {
            return new EncodingResult(Collections.emptyList(), false);
        }

        checkForSpecialTokens(text);

        return encodeOrdinaryInternal(text, maxTokenCount);
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
        return encodeOrdinaryInternal(text, -1).getTokens();
    }

    @Override
    public EncodingResult encodeOrdinary(String text, int maxTokenCount) {
        return encodeOrdinaryInternal(text, maxTokenCount);
    }

    private EncodingResult encodeOrdinaryInternal(String text, int maxTokenCount) {
        if (text == null) {
            return new EncodingResult(Collections.emptyList(), false);
        }

        List<Integer> out = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        int tokenCount = 0;
        while (matcher.find() && maxTokenCountNotReached(maxTokenCount, tokenCount)) {
            ImmutableByteArray match = TokenEncoder.of(matcher.group());
            int encoded = encoder.encode(match);
            if (encoded != MAX_RANK) {
                out.add(encoded);
                tokenCount++;
            } else {
                List<Integer> tokensToAdd = bytePairMerge(match);
                tokenCount += addTokens(out, tokensToAdd, maxTokenCount);
            }
        }

        if (maxTokenCount >= 0) {
            // Make sure we didn't break the multibyte character
            for (int tokensToRemove = 0; tokensToRemove <= out.size(); tokensToRemove++) {
                List<Integer> tokens = out.subList(0, out.size() - tokensToRemove);
                String decoded = decode(tokens);
                if (text.startsWith(decoded)) {
                    // If decoded text is equal to the head of the original text, we can safely return the tokens
                    return new EncodingResult(tokens, text.length() > decoded.length());
                }
            }
        }

        return new EncodingResult(out, false);
    }

    /**
     * Adds tokens from 'tokensToAdd' to 'out' until either 'maxTokenCount' is reached or 'tokensToAdd' is exhausted.
     *
     * @return the number of tokens added to 'out'
     */
    private int addTokens(List<Integer> out, List<Integer> tokensToAdd, int maxTokenCount) {
        if (maxTokenCount >= 0) {
            List<Integer> sublist = tokensToAdd.subList(0, Math.min(maxTokenCount - out.size(), tokensToAdd.size()));
            out.addAll(sublist);
            return sublist.size();
        }

        out.addAll(tokensToAdd);
        return tokensToAdd.size();
    }

    // TODO limit regex to max token size?
    @Override
    public int countTokens(String text) {
        return encode(text).size();
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

    /*
     * We use a custom implementation of the byte pair encoding algorithm as used by the OpenAI tokenizer. The
     * piece is merged according to the merging rules provided by OpenAI. An example of the algorithm:
     *
     * piece:  v   e   c   t   o   r
     * index:  0   1   2   3   4   5   6
     * ranks:  4   3   7   2   13  inf inf
     *
     * We don't modify piece directly. We instead create a list of tuples (index, rank) where index is the start index
     * of a byte pair and rank is it's merge rank. We call this list of tuples parts. The lowest rank is the byte pair
     * that will be merged next. In the example above, the lowest rank is 2, so we merge the byte pair at index 3.
     * To merge a byte pair at index i, we first update the ranks of the byte pairs that are affected by the merge, in this
     * case the byte pair at index 2 and the byte pair at index 3. Then we remove the byte pair at index i + 1 from the list.
     * In this case, this is the byte pair at index 4.
     *
     * piece:  v   e   c   to   r
     * index:  0   1   2   3    5   6
     * ranks:  4   3   5   9    inf inf
     *
     * We then repeat the process until there are no more byte pairs to merge, either because we have merged all byte pairs
     * and parts.size() is 1, or because there are no more merging rules that apply to our tokens. Let's assume there are merging
     * rules for "e + c", "to + r" and "v + ec":
     *
     * piece:  v   ec  to   r
     * index:  0   1   3    5   6
     * ranks:  4   11  12   inf inf
     *         ^
     *
     * piece:  vec to   r
     * index:  0   3    5   6
     * ranks:  inf 12   inf inf
     *             ^
     *
     * piece:  vec tor
     * index:  0   3   6
     * ranks:  inf inf inf
     *
     * We can extract the  tokens by simply taking piece.get(parts[0].index) until piece.get(parts[1].index - 1)
     * and piece.get(parts[1].index) until piece.get(parts[2].index - 1). Analogously for more than two parts.
     * Note that we do not actually modify the piece, but only the parts list. The above visualization is just for
     * illustration purposes.
     */
    List<Integer> bytePairMerge(ImmutableByteArray piece) {
        /*
         * piece:  v   e   c   t   o   r
         * index:  0   1   2   3   4   5   6
         * ranks:  4   3   7   2   13  inf inf
         */
        List<PieceIndexToRank> parts = encoder.initializeParts(piece);


        while (parts.size() > 1) {
            /*
             * piece:  v   e   c   t   o   r
             * index:  0   1   2   3   4   5   6
             * ranks:  4   3   7   2   13  inf inf
             *
             * minRankIndex = 3
             * minRank = 2
             */
            int minRankIndex = 0;
            int minRank = parts.get(0).rank;
            for (int i = 1; i < parts.size() - 2; i++) {
                PieceIndexToRank part = parts.get(i);
                int rank = part.rank;
                if (rank < minRank) {
                    minRank = rank;
                    minRankIndex = i;
                }
            }
            if (minRank == MAX_RANK) {
                break;
            }

            /*
             * piece:  v   e   c   to   r
             * index:  0   1   2   3    5   6
             * ranks:  4   3   5   9    inf inf
             */
            // Note that we calculate the rank of the byte pairs at minRankIndex and minRankIndex - 1 before removing
            // the merged byte pair. We use the skip parameter of the getRank function to calculate the rank of, in our
            // example, "t" + "o" + "r" and "c" + "t" + "o". The assumption made in the OpenAI implementation is that
            // removing first thrashes the cache, so it's better to calculate the rank of the byte pairs that are
            // affected by the merge before removing the merged byte pair. I did not verify, if this is actually the
            // case in java.
            parts.get(minRankIndex).rank = getRank(piece, parts, minRankIndex);
            if (minRankIndex > 0) {
                parts.get(minRankIndex - 1).rank = getRank(piece, parts, minRankIndex - 1);
            }
            parts.remove(minRankIndex + 1);
        }

        /*
         * piece:  vec tor
         * index:  0   3   6
         * ranks:  inf inf inf
         */
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < parts.size() - 1; i++) {
            ImmutableByteArray bytesBetween = TokenEncoder.getSubToken(piece, parts.get(i).index, parts.get(i + 1).index);
            out.add(encoder.encode(bytesBetween));
        }
        return out;
    }

    private int getRank(ImmutableByteArray piece, List<PieceIndexToRank> parts, int startIndex) {
        int endIndex = startIndex + 3;
        if (endIndex >= parts.size()) {
            return MAX_RANK;
        } else {
            int pieceStartIndex = parts.get(startIndex).index;
            int pieceEndIndex = parts.get(endIndex).index;
            ImmutableByteArray encoderIndex = TokenEncoder.getSubToken(piece, pieceStartIndex, pieceEndIndex);
            return encoder.encode(encoderIndex);
        }
    }


    private boolean maxTokenCountNotReached(int maxTokenCount, int tokenCount) {
        return maxTokenCount < 0 || tokenCount < maxTokenCount;
    }

    public byte[] decodeToken(int token) {
        byte[] decodedToken = encoder.decodeIfPresent(token);
        if (decodedToken != null) {
            return decodedToken;
        }

        String decodedSpecialToken = specialTokensEncoder.decodeIfPresent(token);
        if (decodedSpecialToken != null) {
            return decodedSpecialToken.getBytes(UTF_8);
        }

        throw new IllegalArgumentException("Unknown token for decoding: " + token);
    }

    static class PieceIndexToRank {
        final int index;
        int rank;

        PieceIndexToRank(int index, int rank) {
            this.index = index;
            this.rank = rank;
        }

        @Override
        public String toString() {
            return "PieceIndexToRank{index=" + index + ", rank=" + rank + '}';
        }
    }
}
