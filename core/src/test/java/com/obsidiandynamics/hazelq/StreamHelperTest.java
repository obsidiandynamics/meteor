package com.obsidiandynamics.hazelq;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.*;

import com.hazelcast.config.*;
import com.hazelcast.core.*;

public final class StreamHelperTest {
  @Test
  public void testIsNotNull() {
    assertTrue(StreamHelper.isNotNull(new byte[0]));
    assertFalse(StreamHelper.isNotNull(null));
  }

  @Test
  public void testGetRingbuffer() {
    final HazelcastInstance instance = mock(HazelcastInstance.class);
    final Config config = new Config();
    when(instance.getConfig()).thenReturn(config);
    
    final RingbufferStoreConfig ringbufferStoreConfig = new RingbufferStoreConfig()
        .setEnabled(true)
        .setClassName("TestClass");
    final StreamConfig streamConfig = new StreamConfig()
        .withName("stream")
        .withAsyncReplicas(3)
        .withSyncReplicas(2)
        .withHeapCapacity(100)
        .withRingbufferStoreConfig(ringbufferStoreConfig);
    
    StreamHelper.getRingbuffer(instance, streamConfig);
    verify(instance).getConfig();
    
    final RingbufferConfig r = config.getRingbufferConfig(QNamespace.HAZELQ_STREAM.qualify(streamConfig.getName()));
    assertEquals(streamConfig.getAsyncReplicas(), r.getAsyncBackupCount());
    assertEquals(streamConfig.getSyncReplicas(), r.getBackupCount());
    assertEquals(streamConfig.getHeapCapacity(), r.getCapacity());
    assertEquals(streamConfig.getRingbufferStoreConfig(), r.getRingbufferStoreConfig());
  }
  
  @Test
  public void testGetMap() {
    final HazelcastInstance instance = mock(HazelcastInstance.class);
    final Config config = new Config();
    when(instance.getConfig()).thenReturn(config);
    
    final MapStoreConfig mapStoreConfig = new MapStoreConfig()
        .setEnabled(true)
        .setClassName("TestClass");
    final StreamConfig streamConfig = new StreamConfig()
        .withName("stream")
        .withAsyncReplicas(3)
        .withSyncReplicas(2)
        .withHeapCapacity(100);
    
    StreamHelper.getMap(instance, "map", streamConfig, mapStoreConfig);
    final MapConfig m = config.getMapConfig("map");
    assertEquals(streamConfig.getAsyncReplicas(), m.getAsyncBackupCount());
    assertEquals(streamConfig.getSyncReplicas(), m.getBackupCount());
    assertEquals(mapStoreConfig, m.getMapStoreConfig());
  }
}
