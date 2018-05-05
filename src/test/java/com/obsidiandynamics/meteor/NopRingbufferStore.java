package com.obsidiandynamics.meteor;

import java.io.*;
import java.util.*;

import com.hazelcast.core.*;

public final class NopRingbufferStore implements RingbufferStore<Object> {
  public static final class Factory implements RingbufferStoreFactory<Object>, Serializable {
    private static final long serialVersionUID = 1L;
    
    private static final Factory instance = new Factory();
    
    public static Factory getInstance() { return instance; };
    
    @Override
    public NopRingbufferStore newRingbufferStore(String name, Properties properties) {
      return NopRingbufferStore.instance;
    }
  }
  
  private static final NopRingbufferStore instance = new NopRingbufferStore();
  
  private NopRingbufferStore() {}

  @Override
  public void store(long sequence, Object data) {}

  @Override
  public void storeAll(long firstItemSequence, Object[] items) {}

  @Override
  public byte[] load(long sequence) {
    return null;
  }

  @Override
  public long getLargestSequence() {
    return -1;
  }
}
