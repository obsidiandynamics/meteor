package com.obsidiandynamics.meteor;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.hazelcast.ringbuffer.*;

final class StreamHelper {
  static final long SMALLEST_OFFSET = 0;

  static boolean isNotNull(byte[] bytes) {
    return bytes != null;
  }

  private StreamHelper() {}

  static Ringbuffer<byte[]> getRingbuffer(HazelcastInstance instance, StreamConfig streamConfig) {
    final String streamFQName = Namespace.HAZELQ_STREAM.qualify(streamConfig.getName());
    final RingbufferConfig ringbufferConfig = new RingbufferConfig(streamFQName)
        .setBackupCount(streamConfig.getSyncReplicas())
        .setAsyncBackupCount(streamConfig.getAsyncReplicas())
        .setCapacity(streamConfig.getHeapCapacity())
        .setRingbufferStoreConfig(streamConfig.getRingbufferStoreConfig());
    instance.getConfig().addRingBufferConfig(ringbufferConfig);
    return instance.getRingbuffer(streamFQName);
  }

  static IMap<String, byte[]> getLeaseMap(HazelcastInstance instance, StreamConfig streamConfig, 
                                          MapStoreConfig mapStoreConfig) {
    return getMap(instance, Namespace.HAZELQ_META.qualify("lease." + streamConfig.getName()),
                  streamConfig, mapStoreConfig);
  }

  static IMap<String, Long> getOffsetsMap(HazelcastInstance instance, StreamConfig streamConfig, 
                                          MapStoreConfig mapStoreConfig) {
    return getMap(instance, Namespace.HAZELQ_META.qualify("offsets." + streamConfig.getName()),
                  streamConfig, mapStoreConfig);
  }

  static <K, V> IMap<K, V> getMap(HazelcastInstance instance, String mapFQName, 
                                  StreamConfig streamConfig, MapStoreConfig mapStoreConfig) {
    final MapConfig mapConfig = new MapConfig(mapFQName)
        .setBackupCount(streamConfig.getSyncReplicas())
        .setAsyncBackupCount(streamConfig.getAsyncReplicas())
        .setMapStoreConfig(mapStoreConfig);
    instance.getConfig().addMapConfig(mapConfig);
    return instance.getMap(mapFQName);
  }
}
