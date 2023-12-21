package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.Map;

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
            encoders = new Object2IntMap[tempEncoders.lastIntKey() + 1];
            tempEncoders.forEach((k, v) -> {
                encoders[k] = new Object2IntOpenHashMap<>(v, .2f);
                encoders[k].defaultReturnValue(MAX_RANK);
            });
        } else {
            encoders = new Object2IntMap[0]; // for testing
        }
    }

    static boolean accepts(int length) {
        return !CompactTokenEncoder.accepts(length);
    }

    public static int getMinRankIndex(IntArrayList ranks) {
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

    public static int getNextIndex(IntArrayList ranks, int nextIndex) {
        while (nextIndex < ranks.size() && ranks.getInt(nextIndex) == DUMMY_RANK) {
            nextIndex++;
        }
        return nextIndex;
    }

    public static int getPreviousIndex(IntArrayList ranks, int previousIndex) {
        while (previousIndex >= 0 && ranks.getInt(previousIndex) == DUMMY_RANK) {
            previousIndex--;
        }
        return previousIndex;
    }

    private static void removeNode(Int2ObjectSortedMap<Int2ObjectSortedMap<RankNode>> rankMap, RankNode nextNode) {
        var nodeMap = rankMap.get(nextNode.rank);
        if (nodeMap.size() == 1) {
            assert nodeMap.containsKey(nextNode.index);
            rankMap.remove(nextNode.rank);
        } else {
            nodeMap.remove(nextNode.index);
        }
    }

    public static int getValidRankChange(int oldRank, int newRank) {
        var isCurrentMaxRank = newRank == MAX_RANK;
        var isPreviousMaxRank = oldRank == MAX_RANK;
        if (isCurrentMaxRank && !isPreviousMaxRank) {
            return -1;
        } else if (!isCurrentMaxRank && isPreviousMaxRank) {
            return 1;
        } else {
            return 0;
        }
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
                return addTokensAndGetCountSmall(compactTokenEncoder, maxTokenCount, keepEncodings, out, ranks, match, length);
            } else {
                return addTokensAndGetCountLarge(compactTokenEncoder, maxTokenCount, keepEncodings, out, match, length);
            }
        }
    }

    private int addTokensAndGetCountLarge(CompactTokenEncoder compactTokenEncoder, int maxTokenCount, boolean keepEncodings, IntList out, ByteArray match, int length) {
        assert length > 1 : "Already filtered out";

        var rankMap = new Int2ObjectAVLTreeMap<Int2ObjectSortedMap<RankNode>>();

        RankNode head = null;
        RankNode prevNode = null;
        var validRanks = 0;
        for (var i = 0; i < length + 1; i++) {
            var encoded = encode(compactTokenEncoder, match, i, i + 2);
            if (encoded != MAX_RANK) {
                validRanks++;
            }
            var node = new RankNode(encoded, i);
            if (head == null) {
                head = node;
            } else {
                prevNode.next = node;
                node.prev = prevNode;
            }
            prevNode = node;

            rankMap.computeIfAbsent(encoded, k -> new Int2ObjectAVLTreeMap<>()).put(i, node);
        }

        assert accepts(length);
        while (true) {
            if (validRanks == 0) {
                assert rankMap.get(rankMap.firstIntKey()).get(rankMap.get(rankMap.firstIntKey()).firstIntKey()).rank == MAX_RANK;
                break;
            }
            var minKey = rankMap.get(rankMap.firstIntKey());
            var minNode = minKey.get(minKey.firstIntKey());
            assert minNode.rank != MAX_RANK;

            var previousNode = minNode.prev;
            var nextNode = minNode.next;
            var nextNextNode = nextNode != null ? nextNode.next : null;
            var nextNextNextNode = nextNextNode != null ? nextNextNode.next : null;

            if (previousNode != null) {
                var newRank = encode(compactTokenEncoder, match, previousNode.index, nextNextNode != null ? nextNextNode.index : Integer.MAX_VALUE);
                validRanks += getValidRankChange(previousNode.rank, newRank);
                removeNode(rankMap, previousNode);
                previousNode.rank = newRank;
                rankMap.computeIfAbsent(newRank, k -> new Int2ObjectAVLTreeMap<>()).put(previousNode.index, previousNode);
            }

            var newRank = encode(compactTokenEncoder, match, minNode.index, nextNextNextNode != null ? nextNextNextNode.index : Integer.MAX_VALUE);
            validRanks += getValidRankChange(minNode.rank, newRank);
            removeNode(rankMap, minNode);
            minNode.rank = newRank;
            rankMap.computeIfAbsent(newRank, k -> new Int2ObjectAVLTreeMap<>()).put(minNode.index, minNode);

            minNode.next = nextNextNode;
            if (nextNode != null) {
                if (nextNextNode != null) {
                    nextNextNode.prev = minNode;
                }
                if (nextNode.rank != MAX_RANK) {
                    validRanks--;
                }
                removeNode(rankMap, nextNode);
            }

            length--;
        }

        if (keepEncodings) {
            while (head.next != null && out.size() < maxTokenCount) {
                var token = encode(compactTokenEncoder, match, head.index, head.next.index);
                assert token != MAX_RANK : "Token should not be MAX_RANK";
                out.add(token);
                head = head.next;
            }
        }

        return length;
    }

    private int addTokensAndGetCountSmall(CompactTokenEncoder compactTokenEncoder, int maxTokenCount, boolean keepEncodings, IntList out, IntArrayList ranks, ByteArray match, int length) {
        var validRanks = initRanks(compactTokenEncoder, match, length, ranks);
        var tokenCount = mergeBytesAndGetTokenCount(compactTokenEncoder, match, length, ranks, validRanks);
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

    int initRanks(CompactTokenEncoder compactTokenEncoder, ByteArray piece, int tokenCount, IntArrayList ranks) {
        assert tokenCount > 1 : "Already filtered out";
        ranks.clear();
        ranks.ensureCapacity(tokenCount + 1);
        var validRanks = 0;
        for (var i = 0; i < tokenCount + 1; i++) {
            var encoded = encode(compactTokenEncoder, piece, i, i + 2);
            if (encoded != MAX_RANK) {
                validRanks++;
            }
            ranks.add(encoded);
        }
        return validRanks;
    }

    int mergeBytesAndGetTokenCount(CompactTokenEncoder compactTokenEncoder, ByteArray piece, int length, IntArrayList ranks, int validRanks) {
        assert accepts(length);
        while (true) {
            if (validRanks == 0) {
                assert getMinRankIndex(ranks) < 0;
                break;
            }
            var minRankIndex = getMinRankIndex(ranks);
            assert minRankIndex >= 0;

            var previousIndex = getPreviousIndex(ranks, minRankIndex - 1);
            var nextIndex = getNextIndex(ranks, minRankIndex + 1);
            var nextNextIndex = getNextIndex(ranks, nextIndex + 1);
            var nextNextNextIndex = getNextIndex(ranks, nextNextIndex + 1);

            if (previousIndex >= 0) {
                assert ranks.getInt(previousIndex) != DUMMY_RANK;
                var newRank = encode(compactTokenEncoder, piece, previousIndex, nextNextIndex);
                int oldRank = ranks.set(previousIndex, newRank);
                validRanks += getValidRankChange(oldRank, newRank);
            }
            assert ranks.getInt(minRankIndex) != DUMMY_RANK;
            var newRank = encode(compactTokenEncoder, piece, minRankIndex, nextNextNextIndex);
            var oldRank = ranks.set(minRankIndex, newRank);
            validRanks += getValidRankChange(oldRank, newRank);

            var oldDeletedRank = ranks.set(nextIndex, DUMMY_RANK);
            if (oldDeletedRank != MAX_RANK) {
                validRanks--;
            }

            length--;
        }
        return length;
    }

    int encode(ByteArray payload) {
        if (payload.length() < encoders.length) {
            var encoder = encoders[payload.length()];
            return encoder == null ? MAX_RANK : encoder.getInt(payload);
        } else {
            return MAX_RANK;
        }
    }

    private int encode(CompactTokenEncoder compactTokenEncoder, ByteArray piece, int start, int end) {
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

    private static class RankNode {
        int rank;
        int index;
        RankNode prev, next;

        RankNode(int rank, int index) {
            this.rank = rank;
            this.index = index;
        }
    }
}