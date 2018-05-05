package com.obsidiandynamics.meteor;

import java.io.*;
import java.util.*;

import com.hazelcast.core.*;
import com.obsidiandynamics.func.*;

public final class HeapRingbufferStore implements RingbufferStore<Object> {
  public static final class Factory implements RingbufferStoreFactory<Object>, Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    public HeapRingbufferStore newRingbufferStore(String name, Properties properties) {
      return new HeapRingbufferStore();
    }
  }
  
  private final List<byte[]> stored = new ArrayList<>();
  
  private HeapRingbufferStore() {}

  @Override
  public void store(long sequence, Object data) {
    stored.add(Classes.cast(data));
  }

  @Override
  public void storeAll(long firstItemSequence, Object[] items) {
    long sequence = firstItemSequence;
    for (Object item : items) {
      store(sequence++, item);
    }
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
