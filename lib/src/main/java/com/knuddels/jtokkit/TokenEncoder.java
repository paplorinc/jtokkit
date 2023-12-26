package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.Map;

import static com.knuddels.jtokkit.TokenEncoderLarge.calculateTokensLarge;

final class TokenEncoder {
    public static final int DUMMY_RANK = Integer.MAX_VALUE;
    public static final int MAX_RANK = Integer.MAX_VALUE - 1;
    private final Object2IntMap<?>[] encoders;
    private int VERY_LARGE_TOKENIZER_BYTE_THRESHOLD;
    private int length = 0;

    public TokenEncoder(Map<byte[], Integer> encoder) {
        if (!encoder.isEmpty()) {
            VERY_LARGE_TOKENIZER_BYTE_THRESHOLD = Integer.parseInt(System.getProperty("VERY_LARGE_TOKENIZER_BYTE_THRESHOLD", "1000"));
            var tempEncoders = new Int2ObjectAVLTreeMap<Object2IntMap<ByteArray>>();
            encoder.forEach((k, v) -> {
                if (accepts(k.length)) {
                    length++;
                    var key = new ByteArray(k, 0, k.length);
                    tempEncoders.computeIfAbsent(k.length, integer -> new Object2IntOpenHashMap<>()).put(key, (int) v);
                }
            });
            //noinspection unchecked
            encoders = new Object2IntMap[tempEncoders.lastIntKey() + 1];
            tempEncoders.forEach((k, v) -> {
                encoders[k] = new Object2IntOpenHashMap<>(v, .4f);
                encoders[k].defaultReturnValue(MAX_RANK);
            });
        } else {
            encoders = new Object2IntMap[0]; // for testing
        }
    }

    static boolean accepts(int length) {
        return !CompactTokenEncoder.accepts(length);
    }

    public static int getMinRankIndex(IntList ranks) {
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

    public static int getNextIndex(IntList ranks, int nextIndex) {
        while (nextIndex < ranks.size() && ranks.getInt(nextIndex) == DUMMY_RANK) {
            nextIndex++;
        }
        return nextIndex;
    }

    public static int getPreviousIndex(IntList ranks, int previousIndex) {
        while (previousIndex >= 0 && ranks.getInt(previousIndex) == DUMMY_RANK) {
            previousIndex--;
        }
        return previousIndex;
    }

    public int addTokensAndGetCount(CompactTokenEncoder compactTokenEncoder, int maxTokenCount, boolean keepEncodings, ByteArrayList utf8Bytes, IntList out, IntArrayList ranks) {
        assert accepts(utf8Bytes.size());
        var match = new ByteArray(utf8Bytes.elements(), 0, utf8Bytes.size());
        var encoded = encode(match);
        if (encoded != MAX_RANK) {
            if (keepEncodings) {
                out.add(encoded);
            }
            return 1;
        } else {
            var length = match.length();
            if (length < VERY_LARGE_TOKENIZER_BYTE_THRESHOLD) {
                return calculateTokensSmall(compactTokenEncoder, maxTokenCount, keepEncodings, out, ranks, match, length);
            } else {
                return calculateTokensLarge(this, compactTokenEncoder, maxTokenCount, keepEncodings, out, match, length);
            }
        }
    }

    private int calculateTokensSmall(CompactTokenEncoder compactTokenEncoder, int maxTokenCount, boolean keepEncodings, IntList out, IntArrayList ranks, ByteArray match, int length) {
        assert length > 1 : "Already filtered out";
        ranks.clear();
        ranks.ensureCapacity(length + 1);

        var validRanks = 0;
        var minRankIndex = -1;
        for (int i = 0, minRank = MAX_RANK; i < length + 1; i++) {
            var encoded = encode(compactTokenEncoder, match, i, i + 2);
            if (encoded != MAX_RANK) {
                validRanks++;
                if (encoded < minRank) {
                    minRankIndex = i;
                    minRank = encoded;
                }
            }
            ranks.add(encoded);
        }
        var tokenCount = mergeBytesAndGetTokenCount(compactTokenEncoder, match, length, ranks, validRanks, minRankIndex);
        if (keepEncodings) {
            for (int start = 0, end = 1; end < ranks.size() && out.size() < maxTokenCount; end++) {
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

    int mergeBytesAndGetTokenCount(CompactTokenEncoder compactTokenEncoder, ByteArray piece, int length, IntList ranks, int validRanks, int minRankIndex) {
        assert minRankIndex == getMinRankIndex(ranks);
        assert accepts(length);
        while (validRanks > 0) {
            assert minRankIndex >= 0;

            var previousIndex = getPreviousIndex(ranks, minRankIndex - 1);
            var nextIndex = getNextIndex(ranks, minRankIndex + 1);
            var nextNextIndex = getNextIndex(ranks, nextIndex + 1);
            var nextNextNextIndex = getNextIndex(ranks, nextNextIndex + 1);

            if (previousIndex >= 0) {
                assert ranks.getInt(previousIndex) != DUMMY_RANK;
                var newRank = encode(compactTokenEncoder, piece, previousIndex, nextNextIndex);
                int oldRank = ranks.set(previousIndex, newRank);
                if ((newRank == MAX_RANK) != (oldRank == MAX_RANK)) {
                    validRanks -= (newRank == MAX_RANK) ? 1 : -1;
                }
            }
            assert ranks.getInt(minRankIndex) != DUMMY_RANK;
            var newRank = encode(compactTokenEncoder, piece, minRankIndex, nextNextNextIndex);
            var oldRank = ranks.set(minRankIndex, newRank);
            if ((newRank == MAX_RANK) != (oldRank == MAX_RANK)) {
                validRanks--;
            }

            var oldDeletedRank = ranks.set(nextIndex, DUMMY_RANK);
            if (oldDeletedRank != MAX_RANK) {
                validRanks--;
            }

            length--;

            minRankIndex = getMinRankIndex(ranks);
        }
        assert getMinRankIndex(ranks) < 0;
        return length;
    }

    int encode(ByteArray payload) {
        if (payload.length() < encoders.length) {
            var encoder = encoders[payload.length()];
            if (encoder != null) {
                return encoder.getInt(payload);
            }
        }
        return MAX_RANK;
    }

    int encode(CompactTokenEncoder compactTokenEncoder, ByteArray piece, int start, int end) {
        if (end > piece.length()) {
            return MAX_RANK;
        } else if (end - start == piece.length()) {
            assert start == piece.getStart() && end == piece.getEnd();
            assert accepts(piece.length());
            return encode(piece);
        } else {
            if (CompactTokenEncoder.accepts(end - start)) {
                return compactTokenEncoder.encode(CompactTokenEncoder.from(piece.array, start + piece.getStart(), end + piece.getStart()));
            } else {
                return encode(new ByteArray(piece.array, start + piece.getStart(), end + piece.getStart()));
            }
        }
    }

    public int length() {
        return length;
    }
}