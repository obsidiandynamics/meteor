package com.obsidiandynamics.hazelq;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.hazelcast.test.*;

public final class TestProvider implements HazelcastProvider {
  private final TestHazelcastInstanceFactory factory;
  
  public TestProvider() {
    this(new TestHazelcastInstanceFactory());
  }
  
  public TestProvider(TestHazelcastInstanceFactory factory) {
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
