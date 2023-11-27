package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

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
                var key = new ImmutableByteArray(k, 0, k.length);
                encoders.put(key, v);
            }
        });
    }

    static boolean accepts(int length) {
        return !CompactTokenEncoder.accepts(length);
    }

    public static int getMinRankIndex(long[] indexedRanks, int last) {
        int minRankIndex = -1;
        int minRank = MAX_RANK;

        int i = 0;
        for (; i <= last - 4; i += 4) { // Unrolled loop
            {
                int r = rank(indexedRanks[i]);
                if (r < minRank) {
                    minRankIndex = i;
                    minRank = r;
                }
            }
            {
                int r = rank(indexedRanks[i + 1]);
                if (r < minRank) {
                    minRankIndex = i + 1;
                    minRank = r;
                }
            }
            {
                int r = rank(indexedRanks[i + 2]);
                if (r < minRank) {
                    minRankIndex = i + 2;
                    minRank = r;
                }
            }
            {
                int r = rank(indexedRanks[i + 3]);
                if (r < minRank) {
                    minRankIndex = i + 3;
                    minRank = r;
                }
            }
        }

        for (; i <= last - 2; i += 2) { // Unrolled loop
            {
                int r = rank(indexedRanks[i]);
                if (r < minRank) {
                    minRankIndex = i;
                    minRank = r;
                }
            }
            {
                int r = rank(indexedRanks[i + 1]);
                if (r < minRank) {
                    minRankIndex = i + 1;
                    minRank = r;
                }
            }
        }

        if (i < last && rank(indexedRanks[i]) < minRank) {
            return i;
        } else {
            return minRankIndex;
        }
    }

    int encode(ImmutableByteArray payload) {
        if (payload.length() <= maxTokenSize) {
            Integer result = encoders.get(payload); // getOrDefault is slower
            return result != null ? result : MAX_RANK;
        } else {
            return MAX_RANK;
        }
    }

    public int addTokensAndGetCount(CompactTokenEncoder compactTokenEncoder, int maxTokenCount, boolean keepEncodings, byte[] bytes, IntList out) {
        assert accepts(bytes.length);
        ImmutableByteArray match = new ImmutableByteArray(bytes, 0, bytes.length);
        int encoded = encode(match);
        if (encoded != MAX_RANK) {
            if (keepEncodings) {
                out.add(encoded);
            }
            return 1;
        } else {
            var byteSize = match.length();
            long[] indexedRanks = getIndexedRanks(compactTokenEncoder, match, byteSize);
            int tokenCount = mergeBytesAndGetTokenCount(compactTokenEncoder, match, byteSize, indexedRanks);
            if (keepEncodings) {
                IntList tokensToAdd = encodeToList(compactTokenEncoder, match, tokenCount, indexedRanks);
                var remaining = maxTokenCount - out.size();
                if (remaining < tokensToAdd.size()) {
                    for (int i = 0; i < remaining; i++) {
                        out.add(tokensToAdd.getInt(i));
                    }
                } else {
                    out.addAll(tokensToAdd);
                }
            }
            return tokenCount;
        }
    }

    long[] getIndexedRanks(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int tokenCount) {
        assert tokenCount > 1 : "Already filtered out";
        long[] indexedRanks = new long[tokenCount + 1];
        for (int i = 0; i < tokenCount - 1; i++) {
            var encoded = encode(compactTokenEncoder, piece, i, i + 2);
            indexedRanks[i] = combine(i, encoded);
        }
        indexedRanks[tokenCount - 1] = combine(tokenCount - 1, MAX_RANK);
        indexedRanks[tokenCount] = combine(tokenCount, MAX_RANK);
        return indexedRanks;
    }

    int mergeBytesAndGetTokenCount(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int remaining, long[] indexedRanks) {
        assert remaining > 1;
        while (remaining > 0) {
            int minRankIndex = getMinRankIndex(indexedRanks, remaining - 1);
            if (minRankIndex < 0) {
                break;
            }

            var newMinRank = getRank(compactTokenEncoder, piece, indexedRanks, minRankIndex, minRankIndex + 3, remaining);
            indexedRanks[minRankIndex] = setRank(indexedRanks[minRankIndex], newMinRank);
            if (minRankIndex > 0) {
                var newPrevMinRank = getRank(compactTokenEncoder, piece, indexedRanks, minRankIndex - 1, minRankIndex + 2, remaining);
                indexedRanks[minRankIndex - 1] = setRank(indexedRanks[minRankIndex - 1], newPrevMinRank);
            }

            remaining--;
            assert IntStream.range(remaining, indexedRanks.length).allMatch(i -> rank(indexedRanks[i]) == MAX_RANK); // remaining ones will always be MAX_RANK values
            System.arraycopy(indexedRanks, minRankIndex + 2, indexedRanks, minRankIndex + 1, remaining - minRankIndex);
        }
        return remaining;
    }

    IntList encodeToList(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int tokenCount, long[] indexedRanks) {
        IntList out = new IntArrayList();
        int start = 0;
        for (int i = 1; i < tokenCount + 1; i++) {
            int end = index(indexedRanks[i]);

            var token = encode(compactTokenEncoder, piece, start, end);
            assert token != MAX_RANK;
            out.add(token);

            start = end;
        }
        return out;
    }

    private int encode(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int start, int end) {
        int length = end - start;
        if (length == piece.length()) {
            assert start == piece.getStart() && end == piece.getEnd();
            assert accepts(piece.length());
            return encode(piece);
        } else {
            start += piece.getStart();
            end += piece.getStart();
            if (CompactTokenEncoder.accepts(length)) {
                return compactTokenEncoder.encode(CompactTokenEncoder.from(piece.array, start, end));
            } else {
                return encode(new ImmutableByteArray(piece.array, start, end));
            }
        }
    }

    private int getRank(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, long[] parts, int startIndex, int endIndex, int size) {
        if (endIndex <= size) {
            int pieceStartIndex = index(parts[startIndex]);
            int pieceEndIndex = index(parts[endIndex]);
            return encode(compactTokenEncoder, piece, pieceStartIndex, pieceEndIndex);
        } else {
            return MAX_RANK;
        }
    }

    public int length() {
        return encoders.size();
    }
}