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

            for (int i = 2; i < longEncoders.length; i++) {
                longEncoders[i] = new Long2IntOpenHashMap(longEncoders[i], .2f);
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
        if (byteSize == newLength) {
            return payload;
        } else {
            var shift = (1 + byteSize - endIndex) * Byte.SIZE;
            var mask = -1L >>> -(newLength * Byte.SIZE);
            var result = (payload >>> shift) & mask;
            result = (result << Byte.SIZE) | newLength;

            assert newLength == byteSize(result) : "Expected byte size: " + newLength + ", but got: " + byteSize(result) + " for result: " + result;

            return result;
        }
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
            assert length > 1 && length < Long.BYTES;
            initRanks(match, length, ranks);
            var tokenCount = mergeBytesAndGetTokenCount(match, length, ranks);
            if (keepEncodings) {
                var i = 0;
                while (i < length && ranks.getInt(i) == DUMMY_RANK) {
                    i++;
                }
                for (var j = 1; j < ranks.size() && out.size() < maxTokenCount; j++) {
                    if (ranks.getInt(j) != DUMMY_RANK) {
                        var token = encode(match, i, j);
                        assert token != MAX_RANK : "Token should not be MAX_RANK";
                        out.add(token);
                        i = j;
                    }
                }
            }
            return tokenCount;
        }
    }

    void initRanks(long piece, int tokenCount, IntArrayList ranks) {
        assert tokenCount > 1 : "Already filtered out";
        ranks.clear();
        ranks.ensureCapacity(tokenCount + 1);
        for (var i = 0; i < tokenCount - 1; i++) {
            ranks.add(encode(piece, i, i + 2));
        }
        ranks.add(MAX_RANK);
        ranks.add(MAX_RANK);
    }

    int mergeBytesAndGetTokenCount(long piece, int length, IntArrayList ranks) {
        assert length > 1;
        int minRankIndex;
        while (true) {
            if (length <= 2) {
                assert getMinRankIndex(ranks) < 0;
                break;
            }
            minRankIndex = getMinRankIndex(ranks);
            if (minRankIndex < 0) {
                break;
            }
            var previousIndex = getPreviousIndex(ranks, minRankIndex - 1);
            var nextIndex = getNextIndex(ranks, minRankIndex + 1);
            var nextNextIndex = getNextIndex(ranks, nextIndex + 1);
            var nextNextNextIndex = getNextIndex(ranks, nextNextIndex + 1);

            if (previousIndex >= 0) {
                var newPrevMinRank = nextNextIndex < ranks.size() ? encode(piece, previousIndex, nextNextIndex) : MAX_RANK;
                ranks.set(previousIndex, newPrevMinRank);
            }
            var newMinRank = nextNextNextIndex < ranks.size() ? encode(piece, minRankIndex, nextNextNextIndex) : MAX_RANK;
            ranks.set(minRankIndex, newMinRank);

            ranks.set(nextIndex, DUMMY_RANK);

            length--;
        }
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
        var length = end - start;
        var fullByteSize = byteSize(piece);
        if (length == fullByteSize) {
            assert start == 0;
            assert accepts(fullByteSize);
            return encode(piece);
        } else {
            var subToken = getSubToken(piece, start, end);
            assert CompactTokenEncoder.accepts(length);
            return encode(subToken);
        }
    }

    public int length() {
        return length;
    }
}