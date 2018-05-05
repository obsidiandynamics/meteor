package com.obsidiandynamics.meteor;

import com.hazelcast.config.*;
import com.obsidiandynamics.yconf.*;

@Y
public final class StreamConfig {
  @YInject
  private String name;
  
  @YInject
  private int heapCapacity = RingbufferConfig.DEFAULT_CAPACITY;
  
  @YInject
  private int syncReplicas = RingbufferConfig.DEFAULT_SYNC_BACKUP_COUNT;
  
  @YInject
  private int asyncReplicas = RingbufferConfig.DEFAULT_ASYNC_BACKUP_COUNT;

  @YInject
  private RingbufferStoreConfig ringbufferStoreConfig = new RingbufferStoreConfig().setEnabled(false);

  String getName() {
    return name;
  }

  public StreamConfig withName(String name) {
    this.name = name;
    return this;
  }

  int getHeapCapacity() {
    return heapCapacity;
  }

  public StreamConfig withHeapCapacity(int heapCapacity) {
    this.heapCapacity = heapCapacity;
    return this;
  }

  int getSyncReplicas() {
    return syncReplicas;
  }

  public StreamConfig withSyncReplicas(int syncReplicas) {
    this.syncReplicas = syncReplicas;
    return this;
  }

  int getAsyncReplicas() {
    return asyncReplicas;
  }

  public StreamConfig withAsyncReplicas(int asyncReplicas) {
    this.asyncReplicas = asyncReplicas;
    return this;
  }
  
  RingbufferStoreConfig getRingbufferStoreConfig() {
    return ringbufferStoreConfig;
  }
  
  public StreamConfig withRingbufferStoreConfig(RingbufferStoreConfig ringbufferStoreConfig) {
    this.ringbufferStoreConfig = ringbufferStoreConfig;
    return this;
  }

  @Override
  public String toString() {
    return StreamConfig.class.getSimpleName() + " [name=" + name + ", heapCapacity=" + heapCapacity
           + ", syncReplicas=" + syncReplicas + ", asyncReplicas=" + asyncReplicas 
           + ", ringbufferStoreConfig=" + ringbufferStoreConfig + "]";
  }
}
