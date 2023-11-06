package com.knuddels.jtokkit;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingResult;
import com.knuddels.jtokkit.api.GptBytePairEncodingParams;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        return encodeInternal(text, null).getTokens();
    }

    @Override
    public EncodingResult encode(String text, int maxTokens) {
        return encodeInternal(text, maxTokens);
    }

    private EncodingResult encodeInternal(String text, Integer maxTokens) {
        if (text == null) {
            return new EncodingResult(Collections.emptyList(), false);
        }

        checkForSpecialTokens(text);

        return encodeOrdinaryInternal(text, maxTokens);
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
        return encodeOrdinaryInternal(text, null).getTokens();
    }

    @Override
    public EncodingResult encodeOrdinary(String text, int maxTokens) {
        return encodeOrdinaryInternal(text, maxTokens);
    }

    private EncodingResult encodeOrdinaryInternal(String text, Integer maxTokens) {
        if (text == null) {
            return new EncodingResult(Collections.emptyList(), false);
        }

        List<Integer> out = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        int tokenCount = 0;
        while (matcher.find() && maxTokenCountNotReached(maxTokens, tokenCount)) {
            Object match = TokenEncoder.of(matcher.group());
            if (encoder.containsDecodedToken(match)) {
                out.add(encoder.encodeOrDefault(match, null));
                tokenCount++;
            } else {
                List<Integer> tokensToAdd = bytePairMerge(match);
                tokenCount += addTokens(out, tokensToAdd, maxTokens);
            }
        }

        if (maxTokens != null) {
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
     * Adds tokens from 'tokensToAdd' to 'out' until either 'maxTokens' is reached or 'tokensToAdd' is exhausted.
     *
     * @return the number of tokens added to 'out'
     */
    private int addTokens(List<Integer> out, List<Integer> tokensToAdd, Integer maxTokens) {
        if (maxTokens != null) {
            List<Integer> sublist = tokensToAdd.subList(0, Math.min(maxTokens - out.size(), tokensToAdd.size()));
            out.addAll(sublist);
            return sublist.size();
        }

        out.addAll(tokensToAdd);
        return tokensToAdd.size();
    }

    // TODO limit regex to max token size?
    @Override
    public int countTokens(String text) {
//		return encode(text).size();
        if (text == null) {
            return 0;
        } else {
            checkForSpecialTokens(text);
            int tokenCount = 0;
            for (Matcher matcher = pattern.matcher(text); matcher.find(); ) {
                Object match = TokenEncoder.of(matcher.group());
                if (encoder.containsDecodedToken(match)) {
                    tokenCount++;
                } else {
                    tokenCount += bytePairMerge2(match);
                }
            }

            return tokenCount;
        }
    }

    @Override
    public int countTokensOrdinary(String text) {
        return encodeOrdinary(text).size();
    }

    @Override
    public String decode(List<Integer> tokens) {
        return new String(decodeBytes(tokens), StandardCharsets.UTF_8);
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
    List<Integer> bytePairMerge(Object piece) {
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
            int minRank = Integer.MAX_VALUE;
            for (int i = 0; i < parts.size() - 1; i++) {
                int rank = parts.get(i).rank;
                if (rank < minRank) {
                    minRank = rank;
                    minRankIndex = i;
                }
            }

            /*
             * piece:  v   e   c   to   r
             * index:  0   1   2   3    5   6
             * ranks:  4   3   5   9    inf inf
             */
            if (minRank != Integer.MAX_VALUE) {
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
            } else {
                break;
            }
        }

        /*
         * piece:  vec tor
         * index:  0   3   6
         * ranks:  inf inf inf
         */
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < parts.size() - 1; i++) {
            var bytesBetween = TokenEncoder.getBytesBetween(piece, parts.get(i).index, parts.get(i + 1).index);
            out.add(encoder.encodeOrDefault(bytesBetween, null));
        }
        return out;
    }

    int bytePairMerge2(Object piece) {
        List<PieceIndexToRank> parts = encoder.initializeParts(piece);
        int result = parts.size() - 1;
        while (parts.size() > 1) {
//            var out = IntStream.range(0, parts.size() - 1)
//                    .mapToObj(i -> "%s(%d)".formatted(new String(piece.getBytesBetween(parts.get(i).index, parts.get(i + 1).index).getRawArray()), parts.get(i).rank))
//                    .collect(joining("\t"));
//            System.out.println(out);

            int minRankIndex = findMinRankIndex(parts);
            PieceIndexToRank minRandPart = parts.get(minRankIndex);
            if (minRandPart.rank == Integer.MAX_VALUE) {
                break;
            }

            minRandPart.rank = getRank(piece, parts, minRankIndex);
            if (minRankIndex > 0) {
                parts.get(minRankIndex - 1).rank = getRank(piece, parts, minRankIndex - 1);
            }
            parts.remove(minRankIndex + 1);
            result--;
        }

        return result;
    }

    private int getRank(Object piece, List<PieceIndexToRank> parts, int startIndex) {
        int endIndex = startIndex + 3;
        return endIndex >= parts.size()
                ? Integer.MAX_VALUE
                : doGetRank(piece, parts, startIndex, endIndex);
    }

    private int doGetRank(Object piece, List<PieceIndexToRank> parts, int startIndex, int endIndex) {
        int pieceStartIndex = parts.get(startIndex).index;
        int pieceEndIndex = parts.get(endIndex).index;
        Object encoderIndex = TokenEncoder.getBytesBetween(piece, pieceStartIndex, pieceEndIndex);
        return encoder.encodeOrDefault(encoderIndex, Integer.MAX_VALUE);
    }

    private int findMinRankIndex(List<PieceIndexToRank> parts) {
        int minRankIndex = 0;
        int minRank = Integer.MAX_VALUE;
        for (int i = 0; i < parts.size() - 1; i++) {
            PieceIndexToRank part = parts.get(i);
            int rank = part.rank;
            if (rank < minRank) {
                minRank = rank;
                minRankIndex = i;
            }
        }
        return minRankIndex;
    }


    private boolean maxTokenCountReached(Integer maxTokenCount, int tokenCount) {
        return maxTokenCount != null && maxTokenCount.compareTo(tokenCount) <= 0;
    }

    private boolean maxTokenCountNotReached(Integer maxTokenCount, int tokenCount) {
        return !maxTokenCountReached(maxTokenCount, tokenCount);
    }

    public byte[] decodeToken(Integer token) {
        byte[] decodedToken = encoder.decodeIfPresent(token);
        if (decodedToken != null) {
            return decodedToken;
        }

        String decodedSpecialToken = specialTokensEncoder.decodeIfPresent(token);
        if (decodedSpecialToken != null) {
            return decodedSpecialToken.getBytes(StandardCharsets.UTF_8);
        }

        throw new IllegalArgumentException("Unknown token for decoding: " + token);
    }

    static class PieceIndexToRank {
        private final int index;
        private int rank;

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
