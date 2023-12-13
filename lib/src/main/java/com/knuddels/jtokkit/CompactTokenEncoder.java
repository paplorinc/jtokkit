package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Arrays;
import java.util.Map;

import static com.knuddels.jtokkit.TokenEncoder.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class CompactTokenEncoder {
    private int[] byteEncoders;
    private Long2IntMap longEncoders;
    private int length = 0;

    public CompactTokenEncoder(Map<byte[], Integer> encoder) {
        if (!encoder.isEmpty()) {
            byteEncoders = new int[1 << Byte.SIZE];
            Arrays.fill(byteEncoders, MAX_RANK);

            longEncoders = new Long2IntOpenHashMap(encoder.size());
            encoder.forEach((k, v) -> {
                assert v >= 0 : "Negative token: " + new String(k, UTF_8);
                if (accepts(k.length)) {
                    length++;

                    var key = from(k, 0, k.length);
                    if (k.length == 1) {
                        var index = (int) key >> Byte.SIZE; // drop length
                        assert byteEncoders[index] == MAX_RANK : "Duplicate byte token: " + new String(k, UTF_8);
                        byteEncoders[index] = v;
                    } else {
                        longEncoders.put(key, (int) v);
                    }
                    assert encode(key) == v;
                }
            });
        }
    }

    static boolean accepts(int length) {
        return length < Long.BYTES; // first byte is size
    }

    static long from(byte[] bytes, int start, int end) {
        var length = end - start;
        assert length > 0 : "Too small byte array: " + new String(bytes, UTF_8);
        assert accepts(length) : "Too big byte array: " + new String(bytes, start, end, UTF_8);

        var finalResult = bytes[start] & 0xFFL;
        if (length > 1) {
            var i = start + 2;
            var result2 = bytes[start + 1] & 0xFFL;
            for (; i < end - 1; i += 2) {
                finalResult = (finalResult << (Byte.SIZE * 2)) | (bytes[i] & 0xFFL);
                result2 = (result2 << (Byte.SIZE * 2)) | (bytes[i + 1] & 0xFFL);
            }

            finalResult = (finalResult << Byte.SIZE) | result2;
            if (i < end) {
                finalResult = (finalResult << Byte.SIZE) | (bytes[i] & 0xFFL);
            }
        }
        finalResult = (finalResult << Byte.SIZE) | length;

        assert Arrays.equals(Arrays.copyOfRange(bytes, start, end), toByteArray(finalResult))
                : "Expected: " + Arrays.toString(Arrays.copyOfRange(bytes, start, end)) + ", but got: " + Arrays.toString(toByteArray(finalResult));
        return finalResult;
    }

    static byte[] toByteArray(long value) {
        var bytes = new byte[byteSize(value)];
        for (var i = bytes.length - 1; i >= 0; i--) {
            value >>>= Byte.SIZE;
            bytes[i] = (byte) (value & 0xFF);
        }
        return bytes;
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

    int addTokensAndGetCount(int maxTokenCount, boolean keepEncodings, ByteArrayList utf8Bytes, IntList out) {
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
            var ranks = getRanks(match, length);
            var tokenCount = mergeBytesAndGetTokenCount(match, length, ranks);
            if (keepEncodings) {
                var start = 0;
                while (start < length && ranks[start] == DUMMY_RANK) {
                    start++;
                }
                for (var end = 1; end < ranks.length && out.size() < maxTokenCount; end++) {
                    if (ranks[end] != DUMMY_RANK) {
                        var token = encode(match, start, end);
                        assert token != MAX_RANK;
                        out.add(token);
                        start = end;
                    }
                }
            }
            return tokenCount;
        }
    }

    int[] getRanks(long piece, int tokenCount) {
        assert tokenCount > 1 : "Already filtered out";
        var ranks = new int[tokenCount + 1];
        for (var i = 0; i < tokenCount - 1; i++) {
            ranks[i] = encode(piece, i, i + 2);
        }
        ranks[tokenCount - 1] = MAX_RANK;
        ranks[tokenCount] = MAX_RANK;
        return ranks;
    }

    int mergeBytesAndGetTokenCount(long piece, int remaining, int[] ranks) {
        assert remaining > 1;
        int minRankIndex;
        while (true) {
            minRankIndex = getMinRankIndex(ranks, ranks.length - 3);
            if (minRankIndex < 0) {
                break;
            }
            var previousIndex = getPreviousIndex(ranks, minRankIndex - 1);
            var nextIndex = getNextIndex(ranks, minRankIndex + 1);
            var nextNextIndex = getNextIndex(ranks, nextIndex + 1);
            var nextNextNextIndex = getNextIndex(ranks, nextNextIndex + 1);

            var newMinRank = nextNextNextIndex < ranks.length ? encode(piece, minRankIndex, nextNextNextIndex) : MAX_RANK;
            ranks[minRankIndex] = newMinRank;
            if (previousIndex >= 0) {
                var newPrevMinRank = nextNextIndex < ranks.length ? encode(piece, previousIndex, nextNextIndex) : MAX_RANK;
                ranks[previousIndex] = newPrevMinRank;
            }

            remaining--;
            ranks[nextIndex] = DUMMY_RANK;
        }
        return remaining;
    }

    public int encode(long key) {
        if (byteSize(key) == 1) {
            return byteEncoders[(int) (key >> Byte.SIZE)];
        } else {
            return longEncoders.getOrDefault(key, MAX_RANK);
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