package com.obsidiandynamics.hazelq;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.hazelcast.test.*;

public final class TestHazelcastProvider implements HazelcastProvider {
  private final TestHazelcastInstanceFactory factory;
  
  public TestHazelcastProvider() {
    this(new TestHazelcastInstanceFactory());
  }
  
  public TestHazelcastProvider(TestHazelcastInstanceFactory factory) {
    this.factory = factory;
  }
  
  @Override
  public HazelcastInstance createInstance(Config config) {
    return factory.newHazelcastInstance(config);
  }

  @Override
  public void shutdownAll() {
    factory.shutdownAll();
  }
}
