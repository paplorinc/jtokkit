package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.IntStream;

import static com.knuddels.jtokkit.GptBytePairEncoding.*;
import static com.knuddels.jtokkit.TokenEncoder.MAX_RANK;
import static java.nio.charset.StandardCharsets.UTF_8;

class CompactTokenEncoder {
    private int[] shortEncoders;
    private Long2IntMap longEncoders;
    private int length = 0;

    public CompactTokenEncoder(Map<byte[], Integer> encoder) {
        if (!encoder.isEmpty()) {
            shortEncoders = new int[1 << (Short.SIZE - 1)];
            Arrays.fill(shortEncoders, MAX_RANK);

            longEncoders = new Long2IntOpenHashMap(encoder.size());
            encoder.forEach((k, v) -> {
                assert v >= 0 : "Negative token: " + new String(k, UTF_8);

                if (accepts(k.length)) {
                    length++;

                    var key = from(k, 0, k.length);
                    var index = (short) key;
                    if (key == index) {
                        assert shortEncoders[index] == MAX_RANK : "Duplicate byte token: " + new String(k, UTF_8);
                        shortEncoders[index] = v;
                    } else {
                        longEncoders.put(key, (int) v);
                    }
                }
            });
        }
    }

    static boolean accepts(int length) {
        return length <= Long.BYTES;
    }

    static long from(byte[] bytes, int start, int end) {
        assert end - start > 0 : "Too small byte array: " + new String(bytes, UTF_8);
        assert accepts(end - start) : "Too big byte array: " + new String(bytes, start, end, UTF_8);
        if (end - start == 1) {
            return bytes[start] & 0xFFL;
        }

        int i = start + 2;
        long result1 = bytes[start] & 0xFFL;
        long result2 = bytes[start + 1] & 0xFFL;
        for (; i < end - 1; i += 2) {
            result1 = (result1 << Byte.SIZE * 2) | (bytes[i] & 0xFFL);
            result2 = (result2 << Byte.SIZE * 2) | (bytes[i + 1] & 0xFFL);
        }

        long combinedResult = (result1 << Byte.SIZE) | result2;
        if (i < end) {
            return (combinedResult << Byte.SIZE) | (bytes[i] & 0xFFL);
        } else {
            return combinedResult;
        }
    }

    public static int byteSize(long payload) {
        return Long.BYTES - Long.numberOfLeadingZeros(payload) / Byte.SIZE;
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

    int addTokensAndGetCount(int maxTokenCount, boolean keepEncodings, byte[] bytes, IntList out) {
        long match = from(bytes, 0, bytes.length);
        int token = encode(match);
        if (token != MAX_RANK) {
            if (keepEncodings) {
                out.add(token);
            }
            return 1;
        } else {
            var byteSize = byteSize(match);
            assert byteSize > 1 && byteSize <= Long.BYTES;
            long[] indexedRanks = getIndexedRanks(match, byteSize);
            int tokenCount = mergeBytesAndGetTokenCount(match, byteSize, indexedRanks);
            if (keepEncodings) {
                IntList tokensToAdd = encodeToList(match, tokenCount, indexedRanks);
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
        while (remaining > 2) {
            int minRankIndex = getMinRankIndex(indexedRanks, remaining - 1);
            if (minRankIndex < 0) {
                break;
            }

            var newMinRank = getRank(piece, indexedRanks, minRankIndex, minRankIndex + 3, remaining);
            indexedRanks[minRankIndex] = setRank(indexedRanks[minRankIndex], newMinRank);
            if (minRankIndex > 0) {
                var newPrevMinRank = getRank(piece, indexedRanks, minRankIndex - 1, minRankIndex + 2, remaining);
                indexedRanks[minRankIndex - 1] = setRank(indexedRanks[minRankIndex - 1], newPrevMinRank);
            }

            remaining--;
            assert IntStream.range(remaining, indexedRanks.length).allMatch(i -> rank(indexedRanks[i]) == MAX_RANK); // remaining ones will always be MAX_RANK values
            System.arraycopy(indexedRanks, minRankIndex + 2, indexedRanks, minRankIndex + 1, remaining - minRankIndex);
        }
        return remaining;
    }

    IntList encodeToList(long piece, int tokenCount, long[] indexedRanks) {
        IntList out = new IntArrayList(tokenCount);
        for (int i = 0; i < tokenCount; i++) {
            var start = index(indexedRanks[i]);
            int end = index(indexedRanks[i + 1]);
            var token = encode(piece, start, end);
            assert token != MAX_RANK;
            out.add(token);
        }
        return out;
    }

    public int encode(long key) {
        var shortKey = (short) key;
        if (key == shortKey) {
            return shortEncoders[shortKey];
        } else {
            return longEncoders.getOrDefault(key, MAX_RANK);
        }
    }

    private int encode(long piece, int start, int end) {
        int length = end - start;
        var byteSize = byteSize(piece);
        if (length == byteSize) {
            assert start == 0;
            assert accepts(byteSize);
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

    long getSubToken(long payload, int startIndex, int endIndex) {
        int byteSize = byteSize(payload);
        int newLength = endIndex - startIndex;
        if (byteSize == newLength) {
            return payload;
        } else {
            int shift = (byteSize - endIndex) * Byte.SIZE;
            long mask = -1L >>> -(newLength * Byte.SIZE);
            long result = (payload >>> shift) & mask;

            assert newLength == byteSize(result) : "Expected byte size: " + newLength + ", but got: " + byteSize(result) + " for result: " + result;

            return result;
        }
    }

    public int length() {
        return length;
    }
}