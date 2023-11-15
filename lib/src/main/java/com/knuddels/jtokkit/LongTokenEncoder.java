package com.knuddels.jtokkit;

import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.factory.primitive.LongIntMaps;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableLongIntMap;
import org.eclipse.collections.api.map.primitive.MutableLongIntMap;

import java.util.Arrays;
import java.util.Map;

import static com.knuddels.jtokkit.GptBytePairEncoding.*;
import static com.knuddels.jtokkit.TokenEncoder.MAX_RANK;

final class LongTokenEncoder {
    private final ImmutableLongIntMap longEncoders;

    public LongTokenEncoder(Map<byte[], Integer> encoder) {
        MutableLongIntMap tempLongEncoders = LongIntMaps.mutable.ofInitialCapacity(encoder.size());
        encoder.forEach((k, v) -> {
            if (accepts(k)) {
                tempLongEncoders.put(from(k), v);
            }
        });
        this.longEncoders = tempLongEncoders.toImmutable();
    }

    public static boolean accepts(byte[] bytes) {
        return bytes.length <= Long.BYTES;
    }

    static long from(byte[] bytes) { // TODO ByteBuffer ?
        assert bytes.length > 0 : "Empty byte array";
        assert accepts(bytes) : "Too big byte array: " + Arrays.toString(bytes);

        long result = bytes[0] & 0xFFL;
        for (int i = 1; i < bytes.length; i++) {
            result = (result << Byte.SIZE) | (bytes[i] & 0xFFL);
        }
        return result;
    }

    public static int byteSize(long payload) {
        return Long.BYTES - Long.numberOfLeadingZeros(payload) / Byte.SIZE;
    }

    public static long getSubToken(long payload, int startIndex, int endIndex) {
        int length = byteSize(payload);
        int newLength = endIndex - startIndex;
        if (length == newLength) {
            return payload;
        } else {
            assert newLength <= Long.BYTES;
            int shift = (length - endIndex) * Byte.SIZE;
            long mask = -1L >>> -(newLength * Byte.SIZE);
            long result = (payload >>> shift) & mask;

            assert byteSize(result) == newLength : "Expected byte size: " + newLength + ", but got: " + byteSize(result) + " for result: " + result;

            return result;
        }
    }

    public int encode(long payload) {
        return longEncoders.getIfAbsent(payload, MAX_RANK);
    }

    public int length() {
        return longEncoders.size();
    }

    int addTokensAndGetCount(TokenEncoder tokenEncoder, int maxTokenCount, boolean keepEncodings, byte[] bytes, MutableIntList out) {
        long match = from(bytes);
        int encoded = encode(match);
//        assert !TokenEncoder.accepts(bytes) || encoded == tokenEncoder.encode(TokenEncoder.from(bytes)) : "Expected: " + tokenEncoder.encode(TokenEncoder.from(bytes)) + ", but got: " + encoded;
        if (encoded != MAX_RANK) {
            if (keepEncodings) {
                out.add(encoded);
            }
            return 1;
        } else {
            // TODO specialize for 2-3 tokens
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

    long[] getIndexedRanks(long piece, int tokenCount) {
        long[] indexedRanks = new long[tokenCount];
        assert tokenCount > 1 : "Already filtered out";
        if (tokenCount == 3) {
            indexedRanks[0] = combine(0, encode(piece));
        } else {
            for (int i = 0; i < tokenCount - 2; i++) {
                long subToken = getSubToken(piece, i, i + 2);
                indexedRanks[i] = combine(i, encode(subToken));
            }
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

    private int getRank(long piece, long[] parts, int startIndex, int size) {
        int endIndex = startIndex + 3;
        if (endIndex >= size) {
            return MAX_RANK;
        } else {
            int pieceStartIndex = index(parts[startIndex]);
            int pieceEndIndex = index(parts[endIndex]);
            long encoderIndex = getSubToken(piece, pieceStartIndex, pieceEndIndex);
            return encode(encoderIndex);
        }
    }

    IntList encodeToList(long piece, int tokenCount, long[] indexedRanks) {
        MutableIntList out = IntLists.mutable.withInitialCapacity(tokenCount);
        for (int i = 0; i < tokenCount; i++) {
            var start = index(indexedRanks[i]);
            int end = index(indexedRanks[i + 1]);
            long bytesBetween = getSubToken(piece, start, end);
            out.add(encode(bytesBetween));
        }
        return out;
    }
}