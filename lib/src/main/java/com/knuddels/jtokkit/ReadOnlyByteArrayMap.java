package com.knuddels.jtokkit;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

class ReadOnlyByteArrayMap extends AbstractMap<ImmutableByteArray, Integer> {

    private final int[] backingArray;

    public ReadOnlyByteArrayMap(int[] backingArray) {
        if (backingArray.length != 256) {
            throw new IllegalArgumentException("Array must have 256 elements");
        }
        this.backingArray = backingArray;
    }

    @Override
    public Integer get(Object key) {
        int byteKey = ((ImmutableByteArray) key).getFirstByte() - Byte.MIN_VALUE;
        return backingArray[byteKey];
    }

    @Override
    public boolean containsKey(Object key) {
        return key instanceof ImmutableByteArray;
    }

    @Override
    public Set<Entry<ImmutableByteArray, Integer>> entrySet() {
        throw new UnsupportedOperationException("Read-only map");
    }

    @Override
    public Integer put(ImmutableByteArray key, Integer value) {
        throw new UnsupportedOperationException("Read-only map");
    }

    @Override
    public Integer remove(Object key) {
        throw new UnsupportedOperationException("Read-only map");
    }

    @Override
    public void putAll(Map<? extends ImmutableByteArray, ? extends Integer> m) {
        throw new UnsupportedOperationException("Read-only map");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("Read-only map");
    }
}
