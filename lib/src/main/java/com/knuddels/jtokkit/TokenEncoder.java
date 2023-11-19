package com.knuddels.jtokkit;

import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.factory.primitive.IntLists;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.knuddels.jtokkit.GptBytePairEncoding.*;

final class TokenEncoder {
    public static final int MAX_RANK = Integer.MAX_VALUE;
    private final Map<ImmutableByteArray, Integer> encoders;

    public TokenEncoder(Map<byte[], Integer> encoder) {

        this.encoders = new ConcurrentHashMap<>(encoder.size());
        encoder.forEach((k, v) -> {
            if (accepts(k.length)) {
                encoders.put(from(k), v);
            }
        });
    }

    static boolean accepts(int length) {
        return length > Long.BYTES;
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

    private int encode(ImmutableByteArray payload) {
        Integer result = encoders.get(payload); // getOrDefault is slower
        return result != null ? result : MAX_RANK;
    }

    public int addTokensAndGetCount(LongTokenEncoder longTokenEncoder, int maxTokenCount, boolean keepEncodings, byte[] bytes, MutableIntList out) {
        ImmutableByteArray match = from(bytes);
        int encoded = encode(match);
        if (encoded != MAX_RANK) {
            if (keepEncodings) {
                out.add(encoded);
            }
            return 1;
        } else {
            int size = match.length() + 1;
            long[] indexedRanks = getIndexedRanks(longTokenEncoder, match, size);
            int tokenCount = mergeBytesAndGetTokenCount(longTokenEncoder, match, size, indexedRanks);
            if (keepEncodings) {
                IntList tokensToAdd = encodeToList(longTokenEncoder, match, tokenCount, indexedRanks);
                var remaining = maxTokenCount - out.size();
                if (remaining < tokensToAdd.size()) {
                    for (int i = 0; i < remaining; i++) {
                        out.add(tokensToAdd.get(i));
                    }
                } else {
                    out.addAll(tokensToAdd);
                }
            }
            return tokenCount;
        }
    }

    long[] getIndexedRanks(LongTokenEncoder longTokenEncoder, ImmutableByteArray piece, int tokenCount) {
        long[] indexedRanks = new long[tokenCount];
        assert tokenCount > 1 : "Already filtered out";
        if (tokenCount == 3) {
            assert LongTokenEncoder.accepts(piece.array.length);
            int encoded = longTokenEncoder.encode(LongTokenEncoder.from(piece.array, 0, piece.array.length));
            indexedRanks[0] = combine(0, encoded);
        } else {
            for (int i = 0; i < tokenCount - 2; i++) {
                var encoded = encode(longTokenEncoder, piece, i, i + 2);
                indexedRanks[i] = combine(i, encoded);
            }
        }
        indexedRanks[tokenCount - 2] = combine(tokenCount - 2, MAX_RANK);
        indexedRanks[tokenCount - 1] = combine(tokenCount - 1, MAX_RANK);
        return indexedRanks;
    }

    int mergeBytesAndGetTokenCount(LongTokenEncoder longTokenEncoder, ImmutableByteArray piece, int tokenCount, long[] indexedRanks) {
        assert tokenCount > 1;
        while (tokenCount > 1) {
            int minRankIndex = getMinRankIndex(indexedRanks, tokenCount);
            int minRank = rank(indexedRanks[minRankIndex]);
            if (minRank == MAX_RANK) {
                break;
            }

            indexedRanks[minRankIndex] = setRank(indexedRanks[minRankIndex], getRank(longTokenEncoder, piece, indexedRanks, minRankIndex, tokenCount));
            if (minRankIndex > 0) {
                indexedRanks[minRankIndex - 1] = setRank(indexedRanks[minRankIndex - 1], getRank(longTokenEncoder, piece, indexedRanks, minRankIndex - 1, tokenCount));
            }
            System.arraycopy(indexedRanks, minRankIndex + 2, indexedRanks, minRankIndex + 1, tokenCount - minRankIndex - 2); // remaining ones will always be MAX_RANK values
            tokenCount--;
        }
        return tokenCount - 1;
    }

    IntList encodeToList(LongTokenEncoder longTokenEncoder, ImmutableByteArray piece, int tokenCount, long[] indexedRanks) {
        MutableIntList out = IntLists.mutable.withInitialCapacity(tokenCount);
        for (int i = 0; i < tokenCount; i++) {
            var start = index(indexedRanks[i]);
            int end = index(indexedRanks[i + 1]);
            var encode = encode(longTokenEncoder, piece, start, end);
            out.add(encode);
        }
        return out;
    }

    private int encode(LongTokenEncoder longTokenEncoder, ImmutableByteArray piece, int start, int end) {
        int length = end - start;
        if (length == piece.length()) {
            assert start == 0;
            assert accepts(piece.array.length);
            return encode(piece);
        } else {
            if (LongTokenEncoder.accepts(length)) {
                return longTokenEncoder.encode(LongTokenEncoder.from(piece.array, start, start + length));
            } else {
                byte[] result = new byte[length];
                System.arraycopy(piece.array, start, result, 0, length);
                ImmutableByteArray bytesBetween = new ImmutableByteArray(result);
                return encode(bytesBetween);
            }
        }
    }

    private int getRank(LongTokenEncoder longTokenEncoder, ImmutableByteArray piece, long[] parts, int startIndex, int size) {
        int endIndex = startIndex + 3;
        if (endIndex >= size) {
            return MAX_RANK;
        } else {
            int pieceStartIndex = index(parts[startIndex]);
            int pieceEndIndex = index(parts[endIndex]);
            return encode(longTokenEncoder, piece, pieceStartIndex, pieceEndIndex);
        }
    }

    public int length() {
        return encoders.size();
    }
}