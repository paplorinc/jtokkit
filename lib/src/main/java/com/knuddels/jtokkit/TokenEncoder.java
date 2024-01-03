package com.knuddels.jtokkit;

import com.knuddels.jtokkit.api.IntArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.HashMap;
import java.util.Map;

import static com.knuddels.jtokkit.TokenEncoderLarge.calculateTokensLarge;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyMap;

public final class TokenEncoder {
    public static final int MAX_RANK = MAX_VALUE - 1;
    public static final String VERY_LARGE_TOKENIZER_BYTE_THRESHOLD_KEY = "VERY_LARGE_TOKENIZER_BYTE_THRESHOLD";
    static final int DUMMY_RANK = MAX_VALUE;
    private final Object2IntMap<ByteArrayWrapper>[] encoders;
    private final Map<Integer, byte[]> decoder;
    private int length = 0;

    private int VERY_LARGE_TOKENIZER_BYTE_THRESHOLD;

    TokenEncoder(Map<byte[], Integer> encoder) {
        if (!encoder.isEmpty()) {
            VERY_LARGE_TOKENIZER_BYTE_THRESHOLD = parseInt(System.getProperty(VERY_LARGE_TOKENIZER_BYTE_THRESHOLD_KEY, "1000"));
            Int2ObjectAVLTreeMap<Map<ByteArrayWrapper, Integer>> tempEncoders = new Int2ObjectAVLTreeMap<>();
            encoder.forEach((k, v) -> {
                if (accepts(k.length)) {
                    length++;
                    ByteArrayWrapper key = new ByteArrayWrapper(k);
                    tempEncoders.computeIfAbsent(k.length, integer -> new Object2IntOpenHashMap<>()).put(key, v);
                }
            });
            //noinspection unchecked
            encoders = new Object2IntMap[tempEncoders.lastIntKey() + 1];
            tempEncoders.forEach((k, v) -> {
                encoders[k] = new Object2IntOpenHashMap<>(v, .4f);
                encoders[k].defaultReturnValue(MAX_RANK);
            });

            this.decoder = new HashMap<>(encoder.size());
            encoder.forEach((k, v) -> decoder.put(v, k));
        } else {
            //noinspection unchecked
            encoders = new Object2IntMap[0]; // for testing
            decoder = emptyMap();
        }
    }

    static boolean accepts(int size) {
        return !CompactTokenEncoder.accepts(size);
    }

    static int getMinRankIndex(IntArrayList ranks) {
        int minRankIndex = -1;
        int minRank = MAX_RANK;

        int i = 0;
        int length = ranks.size() - 3;
        for (; i < length - 2; i += 4) { // Unrolled loop
            {
                int r = ranks.get(i);
                if (r < minRank) {
                    minRankIndex = i;
                    minRank = r;
                }
            }
            {
                int r = ranks.get(i + 1);
                if (r < minRank) {
                    minRankIndex = i + 1;
                    minRank = r;
                }
            }
            {
                int r = ranks.get(i + 2);
                if (r < minRank) {
                    minRankIndex = i + 2;
                    minRank = r;
                }
            }
            {
                int r = ranks.get(i + 3);
                if (r < minRank) {
                    minRankIndex = i + 3;
                    minRank = r;
                }
            }
        }

        for (; i <= length; i++) {
            int r = ranks.get(i);
            if (r < minRank) {
                minRankIndex = i;
                minRank = r;
            }
        }

        return minRankIndex;
    }

    public static int getNextIndex(IntArrayList ranks, int nextIndex) {
        while (nextIndex < ranks.size() && ranks.get(nextIndex) == DUMMY_RANK) {
            nextIndex++;
        }
        return nextIndex;
    }

    public static int getPreviousIndex(IntArrayList ranks, int previousIndex) {
        while (previousIndex >= 0 && ranks.get(previousIndex) == DUMMY_RANK) {
            previousIndex--;
        }
        return previousIndex;
    }

    int addTokensAndGetCount(CompactTokenEncoder compactEncoder, int maxTokenCount, boolean keepEncodings, byte[] byteArray, int size, IntArrayList out, IntArrayList ranks) {
        assert accepts(size) : size + " is not accepted";
        ByteArrayWrapper match = new ByteArrayWrapper(byteArray, 0, size);
        int encoded = encode(match);
        if (encoded != MAX_RANK) {
            if (keepEncodings) {
                out.add(encoded);
            }
            return 1;
        } else {
            int matchLength = match.length();
            if (matchLength < VERY_LARGE_TOKENIZER_BYTE_THRESHOLD) {
                return calculateTokensSmall(compactEncoder, maxTokenCount, keepEncodings, out, ranks, match, matchLength);
            } else {
                return calculateTokensLarge(this, compactEncoder, maxTokenCount, keepEncodings, out, match, matchLength);
            }
        }
    }

    private int calculateTokensSmall(CompactTokenEncoder compactEncoder, int maxTokenCount, boolean keepEncodings, IntArrayList out, IntArrayList ranks, ByteArrayWrapper match, int length) {
        assert length > 1 : "Already filtered out";
        ranks.clear();
        ranks.ensureCapacity(length + 1);

        int validRanks = 0;
        int minRankIndex = -1;
        for (int i = 0, minRank = MAX_RANK; i < length + 1; i++) {
            int encoded = encode(compactEncoder, match, i, i + 2);
            if (encoded != MAX_RANK) {
                validRanks++;
                if (encoded < minRank) {
                    minRankIndex = i;
                    minRank = encoded;
                }
            }
            ranks.add(encoded);
        }
        int tokenCount = mergeBytesAndGetTokenCount(compactEncoder, match, length, ranks, validRanks, minRankIndex);
        if (keepEncodings) {
            for (int start = 0, end = 1; end < ranks.size() && out.size() < maxTokenCount; end++) {
                if (ranks.get(end) != DUMMY_RANK) {
                    int token = encode(compactEncoder, match, start, end);
                    assert token != MAX_RANK : "Token should not be MAX_RANK";
                    out.add(token);
                    start = end;
                }
            }
        }
        return tokenCount;
    }

    int mergeBytesAndGetTokenCount(CompactTokenEncoder compactEncoder, ByteArrayWrapper piece, int length, IntArrayList ranks, int validRanks, int minRankIndex) {
        assert minRankIndex == getMinRankIndex(ranks);
        assert accepts(length);
        while (validRanks > 0) {
            assert minRankIndex >= 0;

            int previousIndex = getPreviousIndex(ranks, minRankIndex - 1);
            int nextIndex = getNextIndex(ranks, minRankIndex + 1);
            int nextNextIndex = getNextIndex(ranks, nextIndex + 1);
            int nextNextNextIndex = getNextIndex(ranks, nextNextIndex + 1);

            if (previousIndex >= 0) {
                assert ranks.get(previousIndex) != DUMMY_RANK;
                int newRank = encode(compactEncoder, piece, previousIndex, nextNextIndex);
                int oldRank = ranks.set(previousIndex, newRank);
                if ((newRank == MAX_RANK) != (oldRank == MAX_RANK)) {
                    validRanks -= (newRank == MAX_RANK) ? 1 : -1;
                }
            }
            assert ranks.get(minRankIndex) != DUMMY_RANK;
            int newRank = encode(compactEncoder, piece, minRankIndex, nextNextNextIndex);
            int oldRank = ranks.set(minRankIndex, newRank);
            if ((newRank == MAX_RANK) != (oldRank == MAX_RANK)) {
                validRanks--;
            }

            int oldDeletedRank = ranks.set(nextIndex, DUMMY_RANK);
            if (oldDeletedRank != MAX_RANK) {
                validRanks--;
            }

            length--;

            minRankIndex = getMinRankIndex(ranks);
        }
        assert getMinRankIndex(ranks) < 0;
        return length;
    }

    private int encode(ByteArrayWrapper payload) {
        if (payload.length() < encoders.length) {
            Map<ByteArrayWrapper, Integer> encoder = encoders[payload.length()];
            if (encoder != null) {
                Integer result = encoder.get(payload);
                if (result != null) {
                    return result;
                }
            }
        }
        return MAX_RANK;
    }

    int encode(CompactTokenEncoder compactEncoder, ByteArrayWrapper piece, int start, int end) {
        if (end > piece.length()) {
            return MAX_RANK;
        } else if (end - start == piece.length()) {
            assert start == piece.start && end == piece.end;
            assert accepts(piece.length());
            return encode(piece);
        } else {
            if (CompactTokenEncoder.accepts(end - start)) {
                return compactEncoder.encode(CompactTokenEncoder.from(piece.array, start + piece.start, end + piece.start));
            } else {
                return encode(new ByteArrayWrapper(piece.array, start + piece.start, end + piece.start));
            }
        }
    }

    public byte[] decodeToken(int token, SpecialEncoder specialEncoder) {
        return decoder.computeIfAbsent(token, specialEncoder::decodeIfPresent);
    }

    public int length() {
        return length;
    }
}