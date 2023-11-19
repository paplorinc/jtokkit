package com.knuddels.jtokkit;

import org.eclipse.collections.api.factory.primitive.IntIntMaps;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntIntMap;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;

import java.util.Map;

import static com.knuddels.jtokkit.GptBytePairEncoding.*;
import static com.knuddels.jtokkit.TokenEncoder.MAX_RANK;
import static java.nio.charset.StandardCharsets.UTF_8;

final class IntTokenEncoder {
    private final ImmutableIntIntMap encoders;

    public IntTokenEncoder(Map<byte[], Integer> encoder) {
        MutableIntIntMap tempLongEncoders = IntIntMaps.mutable.ofInitialCapacity(encoder.size());
        encoder.forEach((k, v) -> {
            if (accepts(k.length)) {
                tempLongEncoders.put(from(k, 0, k.length), v);
            }
        });
        this.encoders = tempLongEncoders.toImmutable();
    }

    static boolean accepts(int length) {
        return length <= Integer.BYTES;
    }

    static int from(byte[] bytes, int start, int end) {
        assert accepts(end - start) : "Too big byte array: " + new String(bytes, start, end, UTF_8);

        int result = 0; // Faster without extracting the first byte
        for (int i = start; i < end; i++) {
            result = (result << Byte.SIZE) | (bytes[i] & 0xFF);
        }
        return result;
    }

    public static int byteSize(int payload) {
        return Integer.BYTES - Integer.numberOfLeadingZeros(payload) / Byte.SIZE;
    }

    public int encode(int payload) {
        return encoders.getIfAbsent(payload, MAX_RANK);
    }

    int addTokensAndGetCount(int maxTokenCount, boolean keepEncodings, byte[] bytes, MutableIntList out) {
        int match = from(bytes, 0, bytes.length);
        int encoded = encode(match);
        if (encoded != MAX_RANK) {
            if (keepEncodings) {
                out.add(encoded);
            }
            return 1;
        } else {
            int size = byteSize(match) + 1;
            long[] indexedRanks = getIndexedRanks(match, size);
            int tokenCount = mergeBytesAndGetTokenCount(match, size, indexedRanks);
            if (keepEncodings) {
                IntList tokensToAdd = encodeToList(match, tokenCount, indexedRanks);
                var remaining = maxTokenCount - out.size();
                if (remaining < tokensToAdd.size()) {
                    for (int i = 0; i < remaining; i++) {
                        out.add(tokensToAdd.get(i));
                    }
                } else {
                    out.addAll(tokensToAdd);
                }
            }
            return tokenCount;
        }
    }

    long[] getIndexedRanks(int piece, int tokenCount) {
        long[] indexedRanks = new long[tokenCount];
        assert tokenCount > 1 : "Already filtered out";
        if (tokenCount == 3) {
            indexedRanks[0] = combine(0, encode(piece));
        } else {
            for (int i = 0; i < tokenCount - 2; i++) {
                int subToken = getSubToken(piece, i, i + 2);
                indexedRanks[i] = combine(i, encode(subToken));
            }
        }
        indexedRanks[tokenCount - 2] = combine(tokenCount - 2, MAX_RANK);
        indexedRanks[tokenCount - 1] = combine(tokenCount - 1, MAX_RANK);
        return indexedRanks;
    }

    int mergeBytesAndGetTokenCount(int piece, int tokenCount, long[] indexedRanks) {
        assert tokenCount > 1;
        while (tokenCount > 1) {
            int minRankIndex = TokenEncoder.getMinRankIndex(indexedRanks, tokenCount);
            int minRank = rank(indexedRanks[minRankIndex]);
            if (minRank == MAX_RANK) {
                break;
            }

            if (minRankIndex > 0) {
                indexedRanks[minRankIndex - 1] = setRank(indexedRanks[minRankIndex - 1], getRank(piece, indexedRanks, minRankIndex - 1, tokenCount));
            }
            indexedRanks[minRankIndex] = setRank(indexedRanks[minRankIndex], getRank(piece, indexedRanks, minRankIndex, tokenCount));
            System.arraycopy(indexedRanks, minRankIndex + 2, indexedRanks, minRankIndex + 1, tokenCount - minRankIndex - 2); // remaining ones will always be MAX_RANK values
            tokenCount--;
        }
        return tokenCount - 1;
    }

    IntList encodeToList(int piece, int tokenCount, long[] indexedRanks) {
        MutableIntList out = IntLists.mutable.withInitialCapacity(tokenCount);
        for (int i = 0; i < tokenCount; i++) {
            var start = index(indexedRanks[i]);
            int end = index(indexedRanks[i + 1]);
            int bytesBetween = getSubToken(piece, start, end);
            out.add(encode(bytesBetween));
        }
        return out;
    }

    private int getRank(int piece, long[] parts, int startIndex, int size) {
        int endIndex = startIndex + 3;
        if (endIndex >= size) {
            return MAX_RANK;
        } else {
            int pieceStartIndex = index(parts[startIndex]);
            int pieceEndIndex = index(parts[endIndex]);
            int encoderIndex = getSubToken(piece, pieceStartIndex, pieceEndIndex);
            return encode(encoderIndex);
        }
    }

    int getSubToken(int payload, int startIndex, int endIndex) {
        int length = byteSize(payload);
        int newLength = endIndex - startIndex;
        if (length == newLength) {
            return payload;
        } else {
            assert accepts(newLength);
            int shift = (length - endIndex) * Byte.SIZE;
            int mask = -1 >>> -(newLength * Byte.SIZE);
            int result = (payload >>> shift) & mask;

            assert byteSize(result) == newLength : "Expected byte size: " + newLength + ", but got: " + byteSize(result) + " for result: " + result;

            return result;
        }
    }

    public int length() {
        return encoders.size();
    }
}