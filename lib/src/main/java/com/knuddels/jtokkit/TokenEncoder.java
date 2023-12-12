package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.knuddels.jtokkit.GptBytePairEncoding.*;
import static java.nio.charset.StandardCharsets.UTF_8;

final class TokenEncoder {
    public static final int DUMMY_RANK = Integer.MAX_VALUE;
    public static final int MAX_RANK = DUMMY_RANK - 1;
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
        for (; i <= last - 1; i += 2) { // Unrolled loop
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

        if (i < last + 1 && rank(indexedRanks[i]) < minRank) {
            return i;
        } else {
            return minRankIndex;
        }
    }

    private static String getString(Map<Integer, byte[]> encodedToDecoded, long indexedRank) {
        var bytes = encodedToDecoded.get(rank(indexedRank));
        return bytes == null ? "·" : new String(bytes, UTF_8);
    }

    private static int getNextIndex(long[] indexedRanks, int nextIndex) {
        while (nextIndex < indexedRanks.length && indexedRanks[nextIndex] == DUMMY_RANK) {
            nextIndex++;
        }
        return nextIndex;
    }

    private static int getPreviousIndex(long[] indexedRanks, int previousIndex) {
        while (previousIndex >= 0 && indexedRanks[previousIndex] == DUMMY_RANK) {
            previousIndex--;
        }
        return previousIndex;
    }

    int encode(ImmutableByteArray payload) {
        if (payload.length() <= maxTokenSize) {
            Integer result = encoders.get(payload); // getOrDefault is slower
            return result != null ? result : MAX_RANK;
        } else {
            return MAX_RANK;
        }
    }

    public int addTokensAndGetCount(Map<Integer, byte[]> encodedToDecoded, CompactTokenEncoder compactTokenEncoder, int maxTokenCount, boolean keepEncodings, ByteArrayList utf8Bytes, IntList out) {
        assert accepts(utf8Bytes.size());
        ImmutableByteArray match = new ImmutableByteArray(utf8Bytes.elements(), 0, utf8Bytes.size());
        int encoded = encode(match);
        if (encoded != MAX_RANK) {
            if (keepEncodings) {
                out.add(encoded);
            }
            return 1;
        } else {
            var length = match.length();
            long[] indexedRanks = getIndexedRanks(compactTokenEncoder, match, length);
            int tokenCount = mergeBytesAndGetTokenCount(encodedToDecoded, compactTokenEncoder, match, length, indexedRanks);
            if (keepEncodings) {
//                System.out.println("addTokensAndGetCount resulted in:");
                int start = 0;
                while (start < length && indexedRanks[start] == DUMMY_RANK) {
                    start++;
                }
                for (int i = 0; i < indexedRanks.length - 1 && out.size() < maxTokenCount; i++) {
                    var indexedRank = indexedRanks[i + 1];
                    if (indexedRank != DUMMY_RANK) {
                        int end = index(indexedRank);

                        var token = encode(compactTokenEncoder, match, start, end);
                        assert token != MAX_RANK;
                        out.add(token);
//                        System.out.println("`" + getString(encodedToDecoded, token) + "`");

                        start = end;
                    }
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

    int mergeBytesAndGetTokenCount(Map<Integer, byte[]> encodedToDecoded, CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int remaining, long[] indexedRanks) {
        assert remaining > 1;
        int minRankIndex;
//        System.out.println("mergeBytesAndGetTokenCount called for `" + piece + "`");

        while (true) {
//            var ranks = stream(indexedRanks).mapToObj(indexedRank -> getString(encodedToDecoded, indexedRank)).toList();
//            System.out.println(ranks);
            minRankIndex = getMinRankIndex(indexedRanks, indexedRanks.length - 3);
            if (minRankIndex < 0) {
                break;
            }
            var previousIndex = getPreviousIndex(indexedRanks, minRankIndex - 1);
            var nextIndex = getNextIndex(indexedRanks, minRankIndex + 1);
            var nextNextIndex = getNextIndex(indexedRanks, nextIndex + 1);
            var nextNextNextIndex = getNextIndex(indexedRanks, nextNextIndex + 1);

            var newMinRank = getRank(compactTokenEncoder, piece, indexedRanks, minRankIndex, nextNextNextIndex);
            indexedRanks[minRankIndex] = setRank(indexedRanks[minRankIndex], newMinRank);
            if (previousIndex >= 0) {
                var newPrevMinRank = getRank(compactTokenEncoder, piece, indexedRanks, previousIndex, nextNextIndex);
                indexedRanks[previousIndex] = setRank(indexedRanks[previousIndex], newPrevMinRank);
            }

            remaining--;
            indexedRanks[nextIndex] = DUMMY_RANK;
        }
        return remaining;
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

    private int getRank(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, long[] indexedRanks, int startIndex, int endIndex) {
        if (endIndex < indexedRanks.length) {
            int pieceStartIndex = index(indexedRanks[startIndex]);
            int pieceEndIndex = index(indexedRanks[endIndex]);
            return encode(compactTokenEncoder, piece, pieceStartIndex, pieceEndIndex);
        } else {
            return MAX_RANK;
        }
    }

    public int length() {
        return encoders.size();
    }
}