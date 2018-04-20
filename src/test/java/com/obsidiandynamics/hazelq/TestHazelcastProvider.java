package com.obsidiandynamics.hazelq;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.hazelcast.test.*;

public final class TestHazelcastProvider implements HazelcastProvider {
  private final TestHazelcastInstanceFactory factory = new TestHazelcastInstanceFactory();
  
  @Override
  public HazelcastInstance createInstance(Config config) {
    return factory.newHazelcastInstance(config);
  }

  @Override
  public void shutdownAll() {
    factory.shutdownAll();
  }
}
