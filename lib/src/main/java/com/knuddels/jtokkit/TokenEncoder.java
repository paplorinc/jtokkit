package com.knuddels.jtokkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.knuddels.jtokkit.GptBytePairEncoding.*;

final class TokenEncoder {
    public static final int MAX_RANK = Integer.MAX_VALUE;
    private final Map<ImmutableByteArray, Integer> byteArrayEncoders;

    public TokenEncoder(Map<byte[], Integer> encoder) {

        this.byteArrayEncoders = new ConcurrentHashMap<>(encoder.size());
        encoder.forEach((k, v) -> {
            if (accepts(k)) {
                byteArrayEncoders.put(from(k), v);
            }
        });
    }

    static boolean accepts(byte[] bytes) {
        return true; // TODO !LongTokenEncoder.accepts(bytes);
    }

    static ImmutableByteArray from(byte[] bytes) {
        return new ImmutableByteArray(bytes);
    }

    public static int getMinRankIndex(long[] indexedRanks, int size) {
        int minRankIndex = 0;
        int minRank = rank(indexedRanks[minRankIndex]);
        for (int i = 1; i < size - 2; i++) {
            int r = rank(indexedRanks[i]);
            if (r < minRank) {
                minRankIndex = i;
                minRank = r;
            }
        }
        return minRankIndex;
    }

    ImmutableByteArray getSubToken(ImmutableByteArray payload, int startIndex, int endIndex) {
        int length = payload.length();
        int newLength = endIndex - startIndex;
        if (length == newLength) {
            return payload;
        } else {
            // TODO encode as `long`, if applicable
            ImmutableByteArray immutableByteArray = payload.getBytesBetween(startIndex, endIndex);
            assert immutableByteArray.length() == newLength : "Expected length: " + newLength + ", but got: " + immutableByteArray.length() + " for payload: `" + payload + "` with indices: [" + startIndex + ", " + endIndex + "]";
            return immutableByteArray;
        }
    }

    public int encode(ImmutableByteArray payload) {
        Integer result = byteArrayEncoders.get(payload); // getOrDefault is slower
        return result != null ? result : MAX_RANK;
    }

    public int length() {
        return byteArrayEncoders.size();
    }

    int addTokensAndGetCount(LongTokenEncoder longTokenEncoder, int maxTokenCount, boolean keepEncodings, byte[] bytes, List<Integer> out) {
        ImmutableByteArray match = from(bytes);
        int encoded = encode(match);
//        assert !LongTokenEncoder.accepts(bytes) || encoded == longTokenEncoder.encode(LongTokenEncoder.from(bytes)) : "Expected: " + longTokenEncoder.encode(LongTokenEncoder.from(bytes)) + ", but got: " + encoded;
        if (encoded != MAX_RANK) {
            if (keepEncodings) {
                out.add(encoded);
            }
            return 1;
        } else {
            int size = match.length() + 1;
            long[] indexedRanks = getIndexedRanks(match, size);
            int tokenCount = mergeBytesAndGetTokenCount(match, size, indexedRanks);
            if (keepEncodings) {
                List<Integer> tokensToAdd = encodeToList(match, tokenCount, indexedRanks);
                List<Integer> tokens = maxTokenCount >= 0
                        ? tokensToAdd.subList(0, Math.min(maxTokenCount - out.size(), tokensToAdd.size()))
                        : tokensToAdd;
                out.addAll(tokens);
            }
            return tokenCount;
        }
    }

    long[] getIndexedRanks(ImmutableByteArray piece, int tokenCount) {
        long[] indexedRanks = new long[tokenCount];
        assert tokenCount > 1 : "Already filtered out";
        if (tokenCount == 3) {
            indexedRanks[0] = combine(0, encode(piece));
        } else {
            for (int i = 0; i < tokenCount - 2; i++) {
                ImmutableByteArray subToken = getSubToken(piece, i, i + 2);
                indexedRanks[i] = combine(i, encode(subToken));
            }
        }
        indexedRanks[tokenCount - 2] = combine(tokenCount - 2, MAX_RANK);
        indexedRanks[tokenCount - 1] = combine(tokenCount - 1, MAX_RANK);
        return indexedRanks;
    }

    int mergeBytesAndGetTokenCount(ImmutableByteArray piece, int tokenCount, long[] indexedRanks) {
        assert tokenCount > 1;
        while (tokenCount > 1) {
            int minRankIndex = getMinRankIndex(indexedRanks, tokenCount);
            int minRank = rank(indexedRanks[minRankIndex]);
            if (minRank == MAX_RANK) {
                break;
            }

            indexedRanks[minRankIndex] = setRank(indexedRanks[minRankIndex], getRank(piece, indexedRanks, minRankIndex, tokenCount));
            if (minRankIndex > 0) {
                indexedRanks[minRankIndex - 1] = setRank(indexedRanks[minRankIndex - 1], getRank(piece, indexedRanks, minRankIndex - 1, tokenCount));
            }
            System.arraycopy(indexedRanks, minRankIndex + 2, indexedRanks, minRankIndex + 1, tokenCount - minRankIndex - 2); // remaining ones will always be MAX_RANK values
            tokenCount--;
        }
        return tokenCount - 1;
    }

    private int getRank(ImmutableByteArray piece, long[] parts, int startIndex, int size) {
        int endIndex = startIndex + 3;
        if (endIndex >= size) {
            return MAX_RANK;
        } else {
            int pieceStartIndex = index(parts[startIndex]);
            int pieceEndIndex = index(parts[endIndex]);
            ImmutableByteArray encoderIndex = getSubToken(piece, pieceStartIndex, pieceEndIndex);
            return encode(encoderIndex);
        }
    }

    List<Integer> encodeToList(ImmutableByteArray piece, int tokenCount, long[] indexedRanks) {
        List<Integer> out = new ArrayList<>(tokenCount);
        for (int i = 0; i < tokenCount; i++) {
            var start = index(indexedRanks[i]);
            int end = index(indexedRanks[i + 1]);
            ImmutableByteArray bytesBetween = getSubToken(piece, start, end);
            out.add(encode(bytesBetween));
        }
        return out;
    }
}