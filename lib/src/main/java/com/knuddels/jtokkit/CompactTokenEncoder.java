package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.IntStream;

import static com.knuddels.jtokkit.GptBytePairEncoding.*;
import static com.knuddels.jtokkit.TokenEncoder.MAX_RANK;
import static java.nio.charset.StandardCharsets.UTF_8;

public class CompactTokenEncoder {
    private int[] byteEncoders; // TODO small encoder with two arrays and binary search
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

        long finalResult = bytes[start] & 0xFFL;
        if (length > 1) {
            int i = start + 2;
            long result2 = bytes[start + 1] & 0xFFL;
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
        byte[] bytes = new byte[byteSize(value)];
        for (int i = bytes.length - 1; i >= 0; i--) {
            value >>>= Byte.SIZE;
            bytes[i] = (byte) (value & 0xFF);
        }
        return bytes;
    }

    public static int byteSize(long payload) {
        return (int) (payload & 0xFF);
    }

    public static int getMinRankIndex(long[] indexedRanks, int last) {
        int minRankIndex = -1;
        int minRank = MAX_RANK;

        int i = 0;
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

    static long getSubToken(long payload, int startIndex, int endIndex) {
        int byteSize = byteSize(payload);
        int newLength = endIndex - startIndex;
        if (byteSize == newLength) {
            return payload;
        } else {
            int shift = (1 + byteSize - endIndex) * Byte.SIZE;
            long mask = -1L >>> -(newLength * Byte.SIZE);
            long result = (payload >>> shift) & mask;
            result = (result << Byte.SIZE) | newLength;

            assert newLength == byteSize(result) : "Expected byte size: " + newLength + ", but got: " + byteSize(result) + " for result: " + result;

            return result;
        }
    }

    int addTokensAndGetCount(int maxTokenCount, boolean keepEncodings, ByteArrayList utf8Bytes, IntList out) {
        long match = from(utf8Bytes.elements(), 0, utf8Bytes.size());
        int encoded = encode(match);
        if (encoded != MAX_RANK) {
            if (keepEncodings) {
                out.add(encoded);
            }
            return 1;
        } else {
            var length = byteSize(match);
            assert length > 1 && length < Long.BYTES;
            long[] indexedRanks = getIndexedRanks(match, length);
            int tokenCount = mergeBytesAndGetTokenCount(match, length, indexedRanks);
            if (keepEncodings) {
                int start = 0;
                for (int i = 0; i < tokenCount && out.size() < maxTokenCount; i++) {
                    int end = index(indexedRanks[i + 1]);
                    var token = encode(match, start, end);
                    assert token != MAX_RANK;
                    out.add(token);

                    start = end;
                }
            }
            return tokenCount;
        }
    }

    long[] getIndexedRanks(long piece, int tokenCount) {
        assert tokenCount > 1 : "Already filtered out";
        long[] indexedRanks = new long[tokenCount + 1];
        for (int i = 0; i < tokenCount - 1; i++) {
            var encoded = encode(piece, i, i + 2);
            indexedRanks[i] = combine(i, encoded);
        }
        indexedRanks[tokenCount - 1] = combine(tokenCount - 1, MAX_RANK);
        indexedRanks[tokenCount] = combine(tokenCount, MAX_RANK);
        return indexedRanks;
    }

    int mergeBytesAndGetTokenCount(long piece, int remaining, long[] indexedRanks) {
        assert remaining > 1;
        while (true) {
            int minRankIndex = getMinRankIndex(indexedRanks, remaining - 1);
            if (minRankIndex < 0) {
                break;
            }
            var previousIndex = minRankIndex - 1;
            var nextIndex = minRankIndex + 1;
            var nextNextIndex = nextIndex + 1;
            var nextNextNextIndex = nextNextIndex + 1;

            var newMinRank = getRank(piece, indexedRanks, minRankIndex, nextNextNextIndex, remaining);
            indexedRanks[minRankIndex] = setRank(indexedRanks[minRankIndex], newMinRank);
            if (previousIndex >= 0) {
                var newPrevMinRank = getRank(piece, indexedRanks, previousIndex, nextNextIndex, remaining);
                indexedRanks[previousIndex] = setRank(indexedRanks[previousIndex], newPrevMinRank);
            }

            remaining--;
            assert IntStream.range(remaining, indexedRanks.length).allMatch(i -> rank(indexedRanks[i]) == MAX_RANK); // remaining ones will always be MAX_RANK values
            System.arraycopy(indexedRanks, nextNextIndex, indexedRanks, nextIndex, remaining - minRankIndex);
        }
        return remaining;
    }

    public int encode(long key) {
        if (byteSize(key) == 1) {
            return byteEncoders[(int) ((key >> Byte.SIZE) & 0xFF)];
        } else {
            return longEncoders.getOrDefault(key, MAX_RANK);
        }
    }

    private int encode(long piece, int start, int end) {
        int length = end - start;
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

    private int getRank(long piece, long[] parts, int startIndex, int endIndex, int size) {
        if (endIndex <= size) {
            int pieceStartIndex = index(parts[startIndex]);
            int pieceEndIndex = index(parts[endIndex]);
            return encode(piece, pieceStartIndex, pieceEndIndex);
        } else {
            return MAX_RANK;
        }
    }

    public int length() {
        return length;
    }
}