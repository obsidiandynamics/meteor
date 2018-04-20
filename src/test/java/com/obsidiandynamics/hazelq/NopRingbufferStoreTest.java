package com.obsidiandynamics.hazelq;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

public final class NopRingbufferStoreTest {
  @Test
  public void testMethods() {
    final NopRingbufferStore store = NopRingbufferStore.Factory.getInstance()
        .newRingbufferStore("store", new Properties());
    store.store(0, null);
    store.storeAll(0, null);
    assertNull(store.load(0));
    assertEquals(-1, store.getLargestSequence());
  }
}
