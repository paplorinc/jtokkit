package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Arrays;
import java.util.Map;

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
                if (accepts(k.length)) {
                    length++;

                    var key = from(k, 0, k.length);
                    var shortKey = (short) key;
                    if (key == shortKey) {
                        assert shortKey >= 0 : "Negative short key: " + new String(k, UTF_8);
                        var index = (int) shortKey;
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
        assert accepts(end - start) : "Too big byte array: " + new String(bytes, start, end, UTF_8);

        long result = 0; // Faster without extracting the first byte
        for (int i = start; i < end; i++) {
            result = (result << Byte.SIZE) | (bytes[i] & 0xFFL);
        }
        return result;
    }

    public static int byteSize(long payload) {
        return Long.BYTES - Long.numberOfLeadingZeros(payload) / Byte.SIZE;
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
            long[] indexedRanks = getIndexedRanks(match, byteSize + 1);
            int tokenCount = mergeBytesAndGetTokenCount(match, byteSize + 1, indexedRanks);
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
        long[] indexedRanks = new long[tokenCount];
        assert tokenCount > 1 : "Already filtered out";
        for (int i = 0; i < tokenCount - 2; i++) {
            var encoded = encode(piece, i, i + 2);
            indexedRanks[i] = combine(i, encoded);
        }
        indexedRanks[tokenCount - 2] = combine(tokenCount - 2, MAX_RANK);
        indexedRanks[tokenCount - 1] = combine(tokenCount - 1, MAX_RANK);
        return indexedRanks;
    }

    int mergeBytesAndGetTokenCount(long piece, int tokenCount, long[] indexedRanks) {
        assert tokenCount > 1;
        while (tokenCount > 1) {
            int minRankIndex = TokenEncoder.getMinRankIndex(indexedRanks, tokenCount);
            int minRank = rank(indexedRanks[minRankIndex]);
            if (minRank == MAX_RANK) {
                break;
            }

            indexedRanks[minRankIndex] = setRank(indexedRanks[minRankIndex], getRank(piece, indexedRanks, minRankIndex, tokenCount));
            if (minRankIndex > 0) {
                indexedRanks[minRankIndex - 1] = setRank(indexedRanks[minRankIndex - 1], getRank(piece, indexedRanks, minRankIndex - 1, tokenCount));
            }
            System.arraycopy(indexedRanks, minRankIndex + 2, indexedRanks, minRankIndex + 1, tokenCount - minRankIndex - 2); // remaining ones will always be MAX_RANK values
            tokenCount--;
        }
        return tokenCount - 1;
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

    private int getRank(long piece, long[] parts, int startIndex, int size) {
        int endIndex = startIndex + 3;
        if (endIndex >= size) {
            return MAX_RANK;
        } else {
            int pieceStartIndex = index(parts[startIndex]);
            int pieceEndIndex = index(parts[endIndex]);
            return encode(piece, pieceStartIndex, pieceEndIndex);
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