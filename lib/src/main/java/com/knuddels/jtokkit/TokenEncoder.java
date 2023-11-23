package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.knuddels.jtokkit.GptBytePairEncoding.*;

final class TokenEncoder {
    public static final int MAX_RANK = Integer.MAX_VALUE;
    private final Map<ImmutableByteArray, Integer> encoders;
    public int maxTokenSize = 0;

    public TokenEncoder(Map<byte[], Integer> encoder) {
        this.encoders = new ConcurrentHashMap<>(encoder.size());
        encoder.forEach((k, v) -> {
            if (accepts(k.length)) {
                maxTokenSize = Math.max(maxTokenSize, k.length);
                var key = from(k);
                encoders.put(key, v);
            }
        });
    }

    static boolean accepts(int length) {
        return !CompactTokenEncoder.accepts(length);
    }

    static ImmutableByteArray from(byte[] bytes) {
        return new ImmutableByteArray(bytes);
    }

    public static int getMinRankIndex(long[] indexedRanks, int size) {
        int minRankIndex = -1;
        int minRank = MAX_RANK;

        int i = 0;
        for (; i <= size - 4; i += 2) { // Unroll loop
            for (int j = 0; j < 2; j++) {
                int r = rank(indexedRanks[i + j]);
                if (r < minRank) {
                    minRankIndex = i + j;
                    minRank = r;
                }
            }
        }

        if (i < size - 2 && rank(indexedRanks[i]) < minRank) {
            return i;
        } else {
            return minRankIndex;
        }
    }

    int encode(ImmutableByteArray payload) {
        if (payload.length() > maxTokenSize) {
            return MAX_RANK;
        }
        Integer result = encoders.get(payload); // getOrDefault is slower
        return result != null ? result : MAX_RANK;
    }

    public int addTokensAndGetCount(CompactTokenEncoder compactTokenEncoder, int maxTokenCount, boolean keepEncodings, byte[] bytes, IntList out) {
        assert accepts(bytes.length);
        ImmutableByteArray match = from(bytes);
        int encoded = encode(match);
        if (encoded != MAX_RANK) {
            if (keepEncodings) {
                out.add(encoded);
            }
            return 1;
        } else {
            var byteSize = match.length();
            long[] indexedRanks = getIndexedRanks(compactTokenEncoder, match, byteSize + 1);
            int tokenCount = mergeBytesAndGetTokenCount(compactTokenEncoder, match, byteSize + 1, indexedRanks);
            if (keepEncodings) {
                IntList tokensToAdd = encodeToList(compactTokenEncoder, match, tokenCount, indexedRanks);
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

    long[] getIndexedRanks(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int tokenCount) {
        long[] indexedRanks = new long[tokenCount];
        assert tokenCount > 1 : "Already filtered out";
        for (int i = 0; i < tokenCount - 2; i++) {
            var encoded = encode(compactTokenEncoder, piece, i, i + 2);
            indexedRanks[i] = combine(i, encoded);
        }
        indexedRanks[tokenCount - 2] = combine(tokenCount - 2, MAX_RANK);
        indexedRanks[tokenCount - 1] = combine(tokenCount - 1, MAX_RANK);
        return indexedRanks;
    }

    int mergeBytesAndGetTokenCount(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int tokenCount, long[] indexedRanks) {
        assert tokenCount > 1;
        while (tokenCount > 1) {
            int minRankIndex = getMinRankIndex(indexedRanks, tokenCount);
            if (minRankIndex < 0) {
                break;
            }

            indexedRanks[minRankIndex] = setRank(indexedRanks[minRankIndex], getRank(compactTokenEncoder, piece, indexedRanks, minRankIndex, tokenCount));
            if (minRankIndex > 0) {
                indexedRanks[minRankIndex - 1] = setRank(indexedRanks[minRankIndex - 1], getRank(compactTokenEncoder, piece, indexedRanks, minRankIndex - 1, tokenCount));
            }
            System.arraycopy(indexedRanks, minRankIndex + 2, indexedRanks, minRankIndex + 1, tokenCount - minRankIndex - 2); // remaining ones will always be MAX_RANK values
            tokenCount--;
        }
        return tokenCount - 1;
    }

    IntList encodeToList(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int tokenCount, long[] indexedRanks) {
        IntList out = new IntArrayList(tokenCount);
        for (int i = 0; i < tokenCount; i++) {
            var start = index(indexedRanks[i]);
            int end = index(indexedRanks[i + 1]);
            var token = encode(compactTokenEncoder, piece, start, end);
            assert token != MAX_RANK;
            out.add(token);
        }
        return out;
    }

    private int encode(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int start, int end) {
        int length = end - start;
        if (length == piece.length()) {
            assert start == 0;
            assert accepts(piece.array.length);
            return encode(piece);
        } else {
            if (CompactTokenEncoder.accepts(length)) {
                return compactTokenEncoder.encode(CompactTokenEncoder.from(piece.array, start, end));
            } else {
                byte[] result = new byte[length];
                System.arraycopy(piece.array, start, result, 0, length);
                ImmutableByteArray bytesBetween = new ImmutableByteArray(result);
                return encode(bytesBetween);
            }
        }
    }

    private int getRank(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, long[] parts, int startIndex, int size) {
        int endIndex = startIndex + 3;
        if (endIndex >= size || endIndex - startIndex > maxTokenSize) {
            return MAX_RANK;
        } else {
            int pieceStartIndex = index(parts[startIndex]);
            int pieceEndIndex = index(parts[endIndex]);
            return encode(compactTokenEncoder, piece, pieceStartIndex, pieceEndIndex);
        }
    }

    public int length() {
        return encoders.size();
    }
}