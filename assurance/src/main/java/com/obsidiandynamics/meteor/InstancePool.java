package com.obsidiandynamics.meteor;

import java.util.concurrent.atomic.*;
import java.util.function.*;

import com.hazelcast.core.*;
import com.obsidiandynamics.threads.*;

/**
 *  Pools Hazelcast instances to avoid repeated instantiation.<p>
 *  
 *  Instances are returned from the pool in a round-robin fashion. If more invocations
 *  to {@link InstancePool#get()} are made than there are instances in the pool, some
 *  instances will be shared across callers.<p>
 *  
 *  This class is thread-safe.
 */
public final class InstancePool {
  private final Supplier<HazelcastInstance> instanceSupplier;
  
  private final HazelcastInstance[] instances;
  
  private final Object instancesLock = new Object();
  
  private final AtomicInteger position = new AtomicInteger();
  
  public InstancePool(int size, Supplier<HazelcastInstance> instanceSupplier) {
    this.instanceSupplier = instanceSupplier;
    instances = new HazelcastInstance[size];
  }
  
  public int size() {
    return instances.length;
  }
  
  public HazelcastInstance get() {
    return get(position.getAndIncrement() % size());
  }
  
  private HazelcastInstance get(int index) {
    synchronized (instancesLock) {
      if (instances[index] == null) {
        return instances[index] = instanceSupplier.get();
      } else {
        return instances[index];
      }
    }
  }
  
  public void prestartAll() {
    prestart(size());
  }
  
  public void prestart(int numInstances) {
    Parallel.blocking(numInstances, i -> get(i % size())).run();
  }
}
