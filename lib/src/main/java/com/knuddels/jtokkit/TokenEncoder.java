package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

final class TokenEncoder {
    public static final int MAX_RANK = (1 << 20) - 1;
    private static final int DUMMY_RANK = Integer.MAX_VALUE;
    private final Map<ImmutableByteArray, Integer> encoders;
    public int minTokenSize = Integer.MAX_VALUE;
    public int maxTokenSize = 0;

    public TokenEncoder(Map<byte[], Integer> encoder) {
        this.encoders = new ConcurrentHashMap<>(encoder.size());
        encoder.forEach((k, v) -> {
            if (accepts(k.length)) {
                minTokenSize = Math.min(minTokenSize, k.length);
                maxTokenSize = Math.max(maxTokenSize, k.length);
                var key = new ImmutableByteArray(k, 0, k.length);
                encoders.put(key, v);
            }
        });
    }

    static boolean accepts(int length) {
        return !CompactTokenEncoder.accepts(length);
    }

    private static int getMinRankIndex(int[] ranks, int last) {
        var minRankIndex = -1;
        var minRank = MAX_RANK;

        var i = 0;
        for (; i <= last - 1; i += 2) { // Unrolled loop
            {
                var r = ranks[i];
                if (r < minRank) {
                    minRankIndex = i;
                    minRank = r;
                }
            }
            {
                var r = ranks[i + 1];
                if (r < minRank) {
                    minRankIndex = i + 1;
                    minRank = r;
                }
            }
        }

        if (i < last + 1 && ranks[i] < minRank) {
            return i;
        } else {
            return minRankIndex;
        }
    }

    private static String getString(Map<Integer, byte[]> encodedToDecoded, long rank) {
        var bytes = encodedToDecoded.get(rank);
        return bytes == null ? "·" : new String(bytes, UTF_8);
    }

    private static int getNextIndex(int[] ranks, int nextIndex) {
        while (nextIndex < ranks.length && ranks[nextIndex] == DUMMY_RANK) {
            nextIndex++;
        }
        return nextIndex;
    }

    private static int getPreviousIndex(int[] ranks, int previousIndex) {
        while (previousIndex >= 0 && ranks[previousIndex] == DUMMY_RANK) {
            previousIndex--;
        }
        return previousIndex;
    }

    int encode(ImmutableByteArray payload) {
        assert payload.length() >= minTokenSize;
        if (payload.length() <= maxTokenSize) {
            var result = encoders.get(payload); // getOrDefault is slower
            return result != null ? result : MAX_RANK;
        } else {
            return MAX_RANK;
        }
    }

    public int addTokensAndGetCount(Map<Integer, byte[]> encodedToDecoded, CompactTokenEncoder compactTokenEncoder, int maxTokenCount, boolean keepEncodings, ByteArrayList utf8Bytes, IntList out) {
        assert accepts(utf8Bytes.size());
        var match = new ImmutableByteArray(utf8Bytes.elements(), 0, utf8Bytes.size());
        var encoded = encode(match);
        if (encoded != MAX_RANK) {
            if (keepEncodings) {
                out.add(encoded);
            }
            return 1;
        } else {
            var length = match.length();
            var ranks = getRanks(compactTokenEncoder, match, length);
            var tokenCount = mergeBytesAndGetTokenCount(encodedToDecoded, compactTokenEncoder, match, length, ranks);
            if (keepEncodings) {
//                System.out.println("addTokensAndGetCount resulted in:");
                var start = 0;
                while (start < length && ranks[start] == DUMMY_RANK) {
                    start++;
                }
                for (var i = 0; i < ranks.length - 1 && out.size() < maxTokenCount; i++) {
                    var end = i + 1;
                    if (ranks[end] != DUMMY_RANK) {
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

    int[] getRanks(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int tokenCount) {
        assert tokenCount > 1 : "Already filtered out";
        var ranks = new int[tokenCount + 1];
        for (var i = 0; i < tokenCount - 1; i++) {
            ranks[i] = encode(compactTokenEncoder, piece, i, i + 2);
        }
        ranks[tokenCount - 1] = MAX_RANK;
        ranks[tokenCount] = MAX_RANK;
        return ranks;
    }

    int mergeBytesAndGetTokenCount(Map<Integer, byte[]> encodedToDecoded, CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int remaining, int[] ranks) {
        assert remaining > 1;
        int minRankIndex;
//        System.out.println("mergeBytesAndGetTokenCount called for `" + piece + "`");

        while (true) {
            minRankIndex = getMinRankIndex(ranks, ranks.length - 3);
            if (minRankIndex < 0) {
                break;
            }
            var previousIndex = getPreviousIndex(ranks, minRankIndex - 1);
            var nextIndex = getNextIndex(ranks, minRankIndex + 1);
            var nextNextIndex = getNextIndex(ranks, nextIndex + 1);
            var nextNextNextIndex = getNextIndex(ranks, nextNextIndex + 1);

            var newMinRank = nextNextNextIndex < ranks.length ? encode(compactTokenEncoder, piece, minRankIndex, nextNextNextIndex) : MAX_RANK;
            ranks[minRankIndex] = newMinRank;
            if (previousIndex >= 0) {
                var newPrevMinRank = nextNextIndex < ranks.length ? encode(compactTokenEncoder, piece, previousIndex, nextNextIndex) : MAX_RANK;
                ranks[previousIndex] = newPrevMinRank;
            }

            remaining--;
            ranks[nextIndex] = DUMMY_RANK;
        }
        return remaining;
    }

    private int encode(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int start, int end) {
        var length = end - start;
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

    public int length() {
        return encoders.size();
    }
}