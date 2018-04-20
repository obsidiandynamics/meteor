package com.obsidiandynamics.hazelq;

import java.util.concurrent.atomic.*;
import java.util.function.*;

import com.hazelcast.core.*;
import com.obsidiandynamics.threads.*;

public final class InstancePool {
  private final Supplier<HazelcastInstance> instanceSupplier;
  
  private final AtomicReferenceArray<HazelcastInstance> instances;
  
  private final AtomicInteger position = new AtomicInteger();
  
  public InstancePool(int size, Supplier<HazelcastInstance> instanceSupplier) {
    this.instanceSupplier = instanceSupplier;
    instances = new AtomicReferenceArray<>(size);
  }
  
  public int size() {
    return instances.length();
  }
  
  public HazelcastInstance get() {
    return get(position.getAndIncrement() % size());
  }
  
  private HazelcastInstance get(int index) {
    return instances.updateAndGet(index, instance -> instance != null ? instance : instanceSupplier.get());
  }
  
  public void prestartAll() {
    prestart(size());
  }
  
  public void prestart(int numInstances) {
    Parallel.blocking(numInstances, i -> get(i % size())).run();
  }
}
