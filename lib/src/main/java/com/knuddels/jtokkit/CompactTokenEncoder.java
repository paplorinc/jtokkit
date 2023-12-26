package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Arrays;
import java.util.Map;

import static com.knuddels.jtokkit.TokenEncoder.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.IntStream.range;

public class CompactTokenEncoder {
    private int[] byteEncoders;
    private Long2IntMap[] longEncoders;
    private int length = 0;

    public CompactTokenEncoder(Map<byte[], Integer> encoder) {
        if (!encoder.isEmpty()) {
            byteEncoders = new int[1 << Byte.SIZE];
            Arrays.fill(byteEncoders, MAX_RANK);

            longEncoders = new Long2IntMap[Long.BYTES];
            range(2, longEncoders.length).forEach(i -> longEncoders[i] = new Long2IntOpenHashMap());
            assert longEncoders[0] == longEncoders[1];

            encoder.forEach((k, v) -> {
                assert v >= 0 : "Negative token: " + new String(k, UTF_8);
                if (accepts(k.length)) {
                    length++;

                    var key = from(k, 0, k.length);
                    if (k.length == 1) {
                        var index = (int) key >> Byte.SIZE; // drop length
                        assert byteEncoders[index] == MAX_RANK : "Duplicate token: " + new String(k, UTF_8);
                        byteEncoders[index] = v;
                    } else {
                        longEncoders[k.length].put(key, (int) v);
                    }
                    assert encode(key) == v;
                }
            });

            for (var i = 2; i < longEncoders.length; i++) {
                longEncoders[i] = new Long2IntOpenHashMap(longEncoders[i], .4f);
                longEncoders[i].defaultReturnValue(MAX_RANK);
            }
            assert longEncoders[0] == longEncoders[1];
        }
    }

    static boolean accepts(int length) {
        return length < Long.BYTES; // first byte is size
    }

    static long from(byte[] bytes, int start, int end) {
        var length = end - start;
        assert length > 0 : "Too small byte array: " + new String(bytes, UTF_8);
        assert accepts(length) : "Too big byte array: " + new String(bytes, start, end, UTF_8);

        if (length == 1) {
            return ((bytes[start] & 0xFFL) << Byte.SIZE) | length;
        } else {
            var result1 = bytes[start] & 0xFFL;
            var result2 = bytes[start + 1] & 0xFFL;
            for (start += 2; start <= end - 2; start += 2) {
                result1 = (result1 << (Byte.SIZE * 2)) | (bytes[start] & 0xFFL);
                result2 = (result2 << (Byte.SIZE * 2)) | (bytes[start + 1] & 0xFFL);
            }

            result1 = (result1 << Byte.SIZE) | result2;
            if (start < end) {
                return (((result1 << Byte.SIZE) | (bytes[start] & 0xFFL)) << Byte.SIZE) | length;
            } else {
                return (result1 << Byte.SIZE) | length;
            }
        }
    }

    public static int byteSize(long payload) {
        return (byte) payload;
    }

    static long getSubToken(long payload, int startIndex, int endIndex) {
        var byteSize = byteSize(payload);
        var newLength = endIndex - startIndex;
        assert newLength < byteSize;
        var shift = (1 + byteSize - endIndex) * Byte.SIZE;
        var mask = -1L >>> -(newLength * Byte.SIZE);
        var result = (payload >>> shift) & mask;
        result = (result << Byte.SIZE) | newLength;

        assert newLength == byteSize(result) : "Expected byte size: " + newLength + ", but got: " + byteSize(result) + " for result: " + result;

        return result;
    }

    int addTokensAndGetCount(int maxTokenCount, boolean keepEncodings, ByteArrayList utf8Bytes, IntList out, IntArrayList ranks) {
        assert accepts(utf8Bytes.size());
        var match = from(utf8Bytes.elements(), 0, utf8Bytes.size());
        var encoded = encode(match);
        if (encoded != MAX_RANK) {
            if (keepEncodings) {
                out.add(encoded);
            }
            return 1;
        } else {
            var length = byteSize(match);

            return calculateTokens(maxTokenCount, keepEncodings, out, ranks, match, length);
        }
    }

    private int calculateTokens(int maxTokenCount, boolean keepEncodings, IntList out, IntArrayList ranks, long match, int length) {
        assert length > 1 && length < Long.BYTES;
        ranks.clear();
        ranks.ensureCapacity(length + 1);

        var validRanks = 0;
        var minRankIndex = -1;
        for (int i = 0, minRank = MAX_RANK; i < length + 1; i++) {
            var encoded = encode(match, i, i + 2);
            if (encoded != MAX_RANK) {
                validRanks++;
                if (encoded < minRank) {
                    minRankIndex = i;
                    minRank = encoded;
                }
            }
            ranks.add(encoded);
        }
        var tokenCount = mergeBytesAndGetTokenCount(match, length, ranks, validRanks, minRankIndex);
        if (keepEncodings) {
            for (int start = 0, end = 1; end < ranks.size() && out.size() < maxTokenCount; end++) {
                if (ranks.getInt(end) != DUMMY_RANK) {
                    var token = encode(match, start, end);
                    assert token != MAX_RANK : "Token should not be MAX_RANK";
                    out.add(token);
                    start = end;
                }
            }
        }
        return tokenCount;
    }

    int mergeBytesAndGetTokenCount(long piece, int length, IntArrayList ranks, int validRanks, int minRankIndex) {
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
                var newRank = encode(piece, previousIndex, nextNextIndex);
                int oldRank = ranks.set(previousIndex, newRank);
                if ((newRank == MAX_RANK) != (oldRank == MAX_RANK)) {
                    validRanks -= (newRank == MAX_RANK) ? 1 : -1;
                }
            }
            assert ranks.getInt(minRankIndex) != DUMMY_RANK;
            var newRank = encode(piece, minRankIndex, nextNextNextIndex);
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

    public int encode(long key) {
        var size = byteSize(key);
        if (size == 1) {
            return byteEncoders[(int) (key >> Byte.SIZE)];
        } else {
            return longEncoders[size].get(key);
        }
    }

    private int encode(long piece, int start, int end) {
        var fullByteSize = byteSize(piece);
        if (end > fullByteSize) {
            return MAX_RANK;
        } else if (end - start == fullByteSize) {
            assert start == 0;
            assert accepts(fullByteSize);
            return encode(piece);
        } else {
            var subToken = getSubToken(piece, start, end);
            assert CompactTokenEncoder.accepts(end - start);
            return encode(subToken);
        }
    }

    public int length() {
        return length;
    }
}