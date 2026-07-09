package com.loghog;

import java.util.Arrays;

public final class IntArrayBuilder {

    private int[] values = new int[1024];
    private int size;

    public void add(int value) {
        ensureCapacity(size + 1);
        values[size++] = value;
    }

    public void addAll(int[] incoming) {
        if (incoming == null || incoming.length == 0) {
            return;
        }
        ensureCapacity(size + incoming.length);
        System.arraycopy(incoming, 0, values, size, incoming.length);
        size += incoming.length;
    }

    public int size() {
        return size;
    }

    public int[] toArray() {
        return Arrays.copyOf(values, size);
    }

    private void ensureCapacity(int capacity) {
        if (capacity <= values.length) {
            return;
        }
        int nextSize = values.length;
        while (nextSize < capacity) {
            nextSize *= 2;
        }
        values = Arrays.copyOf(values, nextSize);
    }
}
