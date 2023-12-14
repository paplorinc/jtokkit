package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

final class TokenEncoder {
    public static final int DUMMY_RANK = Integer.MAX_VALUE;
    public static final int MAX_RANK = Integer.MAX_VALUE - 1;
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

    public static int getMinRankIndex(IntArrayList ranks) {
        var minRankIndex = -1;
        var minRank = MAX_RANK;

        var i = 0;
        var length = ranks.size() - 3;
        for (; i < length - 2; i += 4) { // Unrolled loop
            {
                var r = ranks.getInt(i);
                if (r < minRank) {
                    minRankIndex = i;
                    minRank = r;
                }
            }
            {
                var r = ranks.getInt(i + 1);
                if (r < minRank) {
                    minRankIndex = i + 1;
                    minRank = r;
                }
            }
            {
                var r = ranks.getInt(i + 2);
                if (r < minRank) {
                    minRankIndex = i + 2;
                    minRank = r;
                }
            }
            {
                var r = ranks.getInt(i + 3);
                if (r < minRank) {
                    minRankIndex = i + 3;
                    minRank = r;
                }
            }
        }

        for (; i <= length; i++) {
            var r = ranks.getInt(i);
            if (r < minRank) {
                minRankIndex = i;
                minRank = r;
            }
        }

        return minRankIndex;
    }

    private static String getString(Map<Integer, byte[]> encodedToDecoded, long rank) {
        var bytes = encodedToDecoded.get(rank);
        return bytes == null ? "·" : new String(bytes, UTF_8);
    }

    public static int getNextIndex(IntArrayList ranks, int nextIndex) {
        while (nextIndex < ranks.size() && ranks.getInt(nextIndex) == DUMMY_RANK) {
            nextIndex++;
        }
        return nextIndex;
    }

    public static int getPreviousIndex(IntArrayList ranks, int previousIndex) {
        while (previousIndex >= 0 && ranks.getInt(previousIndex) == DUMMY_RANK) {
            previousIndex--;
        }
        return previousIndex;
    }

    int encode(ImmutableByteArray payload) {
        if (payload.length() <= maxTokenSize) {
            var result = encoders.get(payload); // getOrDefault is slower
            return result != null ? result : MAX_RANK;
        } else {
            return MAX_RANK;
        }
    }

    public int addTokensAndGetCount(CompactTokenEncoder compactTokenEncoder, int maxTokenCount, boolean keepEncodings, ByteArrayList utf8Bytes, IntList out, IntArrayList ranks) {
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
            return addTokensAndGetCountSmall(compactTokenEncoder, maxTokenCount, keepEncodings, out, ranks, match, length);
        }
    }

    private int addTokensAndGetCountSmall(CompactTokenEncoder compactTokenEncoder, int maxTokenCount, boolean keepEncodings, IntList out, IntArrayList ranks, ImmutableByteArray match, int length) {
        initRanks(compactTokenEncoder, match, length, ranks);
        var tokenCount = mergeBytesAndGetTokenCount(compactTokenEncoder, match, length, ranks);
        if (keepEncodings) {
            var start = 0;
            while (start < length && ranks.getInt(start) == DUMMY_RANK) {
                start++;
            }
            for (var end = 1; end < ranks.size() && out.size() < maxTokenCount; end++) {
                if (ranks.getInt(end) != DUMMY_RANK) {
                    var token = encode(compactTokenEncoder, match, start, end);
                    assert token != MAX_RANK : "Token should not be MAX_RANK";
                    out.add(token);
                    start = end;
                }
            }
        }
        return tokenCount;
    }

    void initRanks(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int tokenCount, IntArrayList ranks) {
        assert tokenCount > 1 : "Already filtered out";
        ranks.clear();
        ranks.ensureCapacity(tokenCount + 1);
        for (var i = 0; i < tokenCount - 1; i++) {
            ranks.add(encode(compactTokenEncoder, piece, i, i + 2));
        }
        ranks.add(MAX_RANK);
        ranks.add(MAX_RANK);
    }

    int mergeBytesAndGetTokenCount(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int remaining, IntArrayList ranks) {
        assert accepts(remaining);
        while (true) {
            var minRankIndex = getMinRankIndex(ranks);
            if (minRankIndex < 0) {
                break;
            }
            var previousIndex = getPreviousIndex(ranks, minRankIndex - 1);
            var nextIndex = getNextIndex(ranks, minRankIndex + 1);
            var nextNextIndex = getNextIndex(ranks, nextIndex + 1);
            var nextNextNextIndex = getNextIndex(ranks, nextNextIndex + 1);

            if (previousIndex >= 0) {
                var newPrevMinRank = nextNextIndex < ranks.size() ? encode(compactTokenEncoder, piece, previousIndex, nextNextIndex) : MAX_RANK;
                ranks.set(previousIndex, newPrevMinRank);
            }
            var newMinRank = nextNextNextIndex < ranks.size() ? encode(compactTokenEncoder, piece, minRankIndex, nextNextNextIndex) : MAX_RANK;
            ranks.set(minRankIndex, newMinRank);
            ranks.set(nextIndex, DUMMY_RANK);

            remaining--;
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