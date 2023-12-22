package com.knuddels.jtokkit;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.IntList;

import static com.knuddels.jtokkit.TokenEncoder.MAX_RANK;

final class TokenEncoderLarge {
    static int addTokensAndGetCountLarge(TokenEncoder tokenEncoder, CompactTokenEncoder compactTokenEncoder, int maxTokenCount, boolean keepEncodings, IntList out, ByteArray match, int length) {
        assert length > 1 : "Already filtered out";

        var rankMap = new Int2ObjectAVLTreeMap<Int2ObjectSortedMap<RankNode>>();

        RankNode head = null;
        RankNode prevNode = null;
        var validRanks = 0;
        for (var i = 0; i < length + 1; i++) {
            var encoded = tokenEncoder.encode(compactTokenEncoder, match, i, i + 2);
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

        assert TokenEncoder.accepts(length);
        while (validRanks > 0) {
            var minKey = rankMap.get(rankMap.firstIntKey());
            var minNode = minKey.get(minKey.firstIntKey());
            assert minNode.rank != MAX_RANK;

            var previousNode = minNode.prev;
            var nextNode = minNode.next;
            var nextNextNode = nextNode != null ? nextNode.next : null;
            var nextNextNextNode = nextNextNode != null ? nextNextNode.next : null;

            if (previousNode != null) {
                var newRank = tokenEncoder.encode(compactTokenEncoder, match, previousNode.index, nextNextNode != null ? nextNextNode.index : Integer.MAX_VALUE);
                if ((newRank == MAX_RANK) != (previousNode.rank == MAX_RANK)) {
                    validRanks -= (newRank == MAX_RANK) ? 1 : -1;
                }
                removeNode(rankMap, previousNode);
                previousNode.rank = newRank;
                rankMap.computeIfAbsent(newRank, k -> new Int2ObjectAVLTreeMap<>()).put(previousNode.index, previousNode);
            }

            var newRank = tokenEncoder.encode(compactTokenEncoder, match, minNode.index, nextNextNextNode != null ? nextNextNextNode.index : Integer.MAX_VALUE);
            if ((newRank == MAX_RANK) != (minNode.rank == MAX_RANK)) {
                validRanks--;
            }
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
        assert rankMap.get(rankMap.firstIntKey()).get(rankMap.get(rankMap.firstIntKey()).firstIntKey()).rank == MAX_RANK;


        if (keepEncodings) {
            while (head.next != null && out.size() < maxTokenCount) {
                var token = tokenEncoder.encode(compactTokenEncoder, match, head.index, head.next.index);
                assert token != MAX_RANK : "Token should not be MAX_RANK";
                out.add(token);
                head = head.next;
            }
        }

        return length;
    }

    static void removeNode(Int2ObjectSortedMap<Int2ObjectSortedMap<RankNode>> rankMap, RankNode nextNode) {
        var nodeMap = rankMap.get(nextNode.rank);
        if (nodeMap.size() == 1) {
            assert nodeMap.containsKey(nextNode.index);
            rankMap.remove(nextNode.rank);
        } else {
            nodeMap.remove(nextNode.index);
        }
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