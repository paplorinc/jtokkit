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

public class GptBytePairEncoding implements Encoding {

    private final String name;
    private final Pattern pattern;
    private final TokenEncoder encoder;
    private final StringEncoder specialTokensEncoder;

    GptBytePairEncoding(GptBytePairEncodingParams params) {
        this.name = params.getName();
        this.pattern = params.getPattern();
        this.encoder = new TokenEncoder(params.getEncoder());
        this.specialTokensEncoder = new StringEncoder(params.getSpecialTokensEncoder());
    }

    private static int getMinRankIndex(long[] indexedRanks, int size) {
        int minRankIndex = 0;
        int minRank = rank(indexedRanks[minRankIndex]);
        for (int i = 1; i < size - 2; i++) {
            int rank = rank(indexedRanks[i]);
            if (rank < minRank) {
                minRankIndex = i;
                minRank = rank;
            }
        }
        return minRankIndex;
    }

    private static int index(long indexedRank) {
        return (int) (indexedRank >>> Integer.SIZE);
    }

    private static int rank(long indexedRank) {
        return (int) indexedRank;
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

    List<Integer> bytePairMerge(ImmutableByteArray piece) {
        int size = piece.length() + 1;
        long[] indexedRanks = new long[size];
        assert size - 1 > 1 : "Already filtered out";
        if (size - 1 == 2) {
            indexedRanks[0] = combine(0, encoder.encode(piece));
        } else {
            for (int i = 0; i < size - 2; i++) {
                ImmutableByteArray subToken = TokenEncoder.getSubToken(piece, i, i + 2);
                indexedRanks[i] = combine(i, encoder.encode(subToken));
            }
        }
        indexedRanks[size - 2] = combine(size - 2, MAX_RANK);
        indexedRanks[size - 1] = combine(size - 1, MAX_RANK);

        while (size > 1) {
            int minRankIndex = getMinRankIndex(indexedRanks, size); // TODO return indexedRank long
            int minRank = rank(indexedRanks[minRankIndex]);
            if (minRank == MAX_RANK) {
                break;
            }

            indexedRanks[minRankIndex] = setRank(indexedRanks[minRankIndex], getRank(piece, indexedRanks, minRankIndex, size));
            if (minRankIndex > 0) {
                indexedRanks[minRankIndex - 1] = setRank(indexedRanks[minRankIndex - 1], getRank(piece, indexedRanks, minRankIndex - 1, size));
            }
            System.arraycopy(indexedRanks, minRankIndex + 2, indexedRanks, minRankIndex + 1, size - minRankIndex - 2); // remaining ones will always be MAX_RANK values
            size--;
        }

        List<Integer> out = new ArrayList<>(size);
        for (int i = 0; i < size - 1; i++) {
            var start = index(indexedRanks[i]);
            int end = index(indexedRanks[i + 1]);
            ImmutableByteArray bytesBetween = TokenEncoder.getSubToken(piece, start, end);
            out.add(encoder.encode(bytesBetween));
        }
        return out;
    }

    private long combine(long index, int rank) {
        return (index << Integer.SIZE) | rank;
    }

    private long setRank(long indexedRank, int rank) {
        return indexedRank & (-1L << Integer.SIZE) | rank;
    }

    private int getRank(ImmutableByteArray piece, long[] parts, int startIndex, int size) {
        int endIndex = startIndex + 3;
        if (endIndex >= size) {
            return MAX_RANK;
        } else {
            int pieceStartIndex = index(parts[startIndex]);
            int pieceEndIndex = index(parts[endIndex]);
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
}
