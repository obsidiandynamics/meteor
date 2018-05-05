package com.obsidiandynamics.meteor;

import static org.junit.Assert.*;

import org.junit.*;

import com.hazelcast.config.*;
import com.obsidiandynamics.assertion.*;

public final class StreamConfigTest {
  @Test
  public void testConfig() {
    final String name = "name";
    final int heapCapacity = 100;
    final int syncReplicas = 2;
    final int asyncReplicas = 3;
    
    final RingbufferStoreConfig ringbufferStoreConfig = new RingbufferStoreConfig()
        .setEnabled(true)
        .setFactoryClassName("TestClass");
    final StreamConfig config = new StreamConfig()
        .withName(name)
        .withHeapCapacity(heapCapacity)
        .withSyncReplicas(syncReplicas)
        .withAsyncReplicas(asyncReplicas)
        .withRingbufferStoreConfig(ringbufferStoreConfig);
    assertEquals(name, config.getName());
    assertEquals(heapCapacity, config.getHeapCapacity());
    assertEquals(syncReplicas, config.getSyncReplicas());
    assertEquals(asyncReplicas, config.getAsyncReplicas());
    assertEquals(ringbufferStoreConfig, config.getRingbufferStoreConfig());
    
    Assertions.assertToStringOverride(config);
  }
}
