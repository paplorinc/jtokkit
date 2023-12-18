package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.Map;

final class TokenEncoder {
    public static final int DUMMY_RANK = Integer.MAX_VALUE;
    public static final int MAX_RANK = Integer.MAX_VALUE - 1;
    public static final int VERY_LARGE_TOKENIZER_BYTE_THRESHOLD = 1_000;
    private final Object2IntMap<?>[] encoders;
    private int length = 0;

    public TokenEncoder(Map<byte[], Integer> encoder) {
        if (!encoder.isEmpty()) {
            var tempEncoders = new Int2ObjectAVLTreeMap<Object2IntMap<ImmutableByteArray>>();
            encoder.forEach((k, v) -> {
                if (accepts(k.length)) {
                    length++;
                    var key = new ImmutableByteArray(k, 0, k.length);
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

    private static void removeNode(Int2ObjectAVLTreeMap<Int2ObjectAVLTreeMap<RankNode>> rankMap, RankNode nextNode) {
        var nodeMap = rankMap.get(nextNode.rank);
        if (nodeMap.size() == 1) {
            assert nodeMap.containsKey(nextNode.index);
            rankMap.remove(nextNode.rank);
        } else {
            nodeMap.remove(nextNode.index);
        }
    }

    int encode(ImmutableByteArray payload) {
        if (payload.length() < encoders.length) {
            var encoder = encoders[payload.length()];
            return encoder == null ? MAX_RANK : encoder.getInt(payload);
        } else {
            return MAX_RANK;
        }
    }

    public int addTokensAndGetCount(CompactTokenEncoder compactTokenEncoder, int maxTokenCount, boolean keepEncodings, byte[] utf8Bytes, int start, int end, IntList out, IntArrayList ranks) {
        assert accepts(end - start);
        var match = new ImmutableByteArray(utf8Bytes, start, end);
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

    private int addTokensAndGetCountLarge(CompactTokenEncoder compactTokenEncoder, int maxTokenCount, boolean keepEncodings, IntList out, ImmutableByteArray match, int length) {
        assert length > 1 : "Already filtered out";

        var rankMap = new Int2ObjectAVLTreeMap<Int2ObjectAVLTreeMap<RankNode>>();

        RankNode head = null;
        RankNode prevNode = null;
        for (var i = 0; i < length + 1; i++) {
            var rank = i < length - 1 ? encode(compactTokenEncoder, match, i, i + 2) : MAX_RANK;
            var node = new RankNode(rank, i);
            if (head == null) {
                head = node;
            } else {
                prevNode.next = node;
                node.prev = prevNode;
            }
            prevNode = node;

            rankMap.computeIfAbsent(rank, k -> new Int2ObjectAVLTreeMap<>()).put(i, node);
        }

        assert accepts(length);
        while (true) {
            var minKey = rankMap.get(rankMap.firstIntKey());
            var minNode = minKey.get(minKey.firstIntKey());
            if (minNode.rank == MAX_RANK) {
                break;
            }

            var previousNode = minNode.prev;
            var nextNode = minNode.next;
            var nextNextNode = nextNode != null ? nextNode.next : null;
            var nextNextNextNode = nextNextNode != null ? nextNextNode.next : null;

            if (previousNode != null) {
                var newPrevMinRank = nextNextNode != null ? encode(compactTokenEncoder, match, previousNode.index, nextNextNode.index) : MAX_RANK;
                removeNode(rankMap, previousNode);
                previousNode.rank = newPrevMinRank;
                rankMap.computeIfAbsent(newPrevMinRank, k -> new Int2ObjectAVLTreeMap<>()).put(previousNode.index, previousNode);
            }

            var newMinRank = nextNextNextNode != null ? encode(compactTokenEncoder, match, minNode.index, nextNextNextNode.index) : MAX_RANK;
            removeNode(rankMap, minNode);
            minNode.rank = newMinRank;
            rankMap.computeIfAbsent(newMinRank, k -> new Int2ObjectAVLTreeMap<>()).put(minNode.index, minNode);

            minNode.next = nextNextNode;
            if (nextNode != null) {
                if (nextNextNode != null) {
                    nextNextNode.prev = minNode;
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

    private int addTokensAndGetCountSmall(CompactTokenEncoder compactTokenEncoder, int maxTokenCount, boolean keepEncodings, IntList out, IntArrayList ranks, ImmutableByteArray match, int length) {
        initRanks(compactTokenEncoder, match, length, ranks);
        var tokenCount = mergeBytesAndGetTokenCount(compactTokenEncoder, match, length, ranks);
        if (keepEncodings) {
            var start = 0;
            while (start < length && ranks.getInt(start) == DUMMY_RANK) {
                start++;
            }
            for (var end = 1; end < ranks.size() && out.size() < maxTokenCount; end++) {
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

    void initRanks(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int tokenCount, IntArrayList ranks) {
        assert tokenCount > 1 : "Already filtered out";
        ranks.clear();
        ranks.ensureCapacity(tokenCount + 1);
        for (var i = 0; i < tokenCount - 1; i++) {
            ranks.add(encode(compactTokenEncoder, piece, i, i + 2));
        }
        ranks.add(MAX_RANK);
        ranks.add(MAX_RANK);
    }

    int mergeBytesAndGetTokenCount(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int remaining, IntArrayList ranks) {
        assert accepts(remaining);
        while (true) {
            var minRankIndex = getMinRankIndex(ranks);
            if (minRankIndex < 0) {
                break;
            }
            var previousIndex = getPreviousIndex(ranks, minRankIndex - 1);
            var nextIndex = getNextIndex(ranks, minRankIndex + 1);
            var nextNextIndex = getNextIndex(ranks, nextIndex + 1);
            var nextNextNextIndex = getNextIndex(ranks, nextNextIndex + 1);

            if (previousIndex >= 0) {
                var newPrevMinRank = nextNextIndex < ranks.size() ? encode(compactTokenEncoder, piece, previousIndex, nextNextIndex) : MAX_RANK;
                ranks.set(previousIndex, newPrevMinRank);
            }
            var newMinRank = nextNextNextIndex < ranks.size() ? encode(compactTokenEncoder, piece, minRankIndex, nextNextNextIndex) : MAX_RANK;
            ranks.set(minRankIndex, newMinRank);

            ranks.set(nextIndex, DUMMY_RANK);

            remaining--;
        }
        return remaining;
    }

    private int encode(CompactTokenEncoder compactTokenEncoder, ImmutableByteArray piece, int start, int end) {
        var length = end - start;
        if (length == piece.length()) {
            assert start == piece.getStart() && end == piece.getEnd();
            assert accepts(piece.length());
            return encode(piece);
        } else {
            start += piece.getStart();
            end += piece.getStart();
            if (CompactTokenEncoder.accepts(length)) {
                return compactTokenEncoder.encode(CompactTokenEncoder.from(piece.array, start, end));
            } else {
                return encode(new ImmutableByteArray(piece.array, start, end));
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