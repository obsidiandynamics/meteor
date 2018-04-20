package com.obsidiandynamics.hazelq;

import java.io.*;
import java.util.*;

import com.hazelcast.core.*;

public final class HeapRingbufferStore implements RingbufferStore<byte[]> {
  public static final class Factory implements RingbufferStoreFactory<byte[]>, Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    public HeapRingbufferStore newRingbufferStore(String name, Properties properties) {
      return new HeapRingbufferStore();
    }
  }
  
  private final List<byte[]> stored = new ArrayList<>();
  
  private HeapRingbufferStore() {}

  @Override
  public void store(long sequence, byte[] data) {
    stored.add(data);
  }

  @Override
  public void storeAll(long firstItemSequence, byte[][] items) {
    for (byte[] item : items) stored.add(item);
  }

  @Override
  public byte[] load(long sequence) {
    return stored.get((int) sequence);
  }

  @Override
  public long getLargestSequence() {
    return stored.size() - 1;
  }
}
