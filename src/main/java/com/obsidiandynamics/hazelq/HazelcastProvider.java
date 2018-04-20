package com.obsidiandynamics.hazelq;

import com.hazelcast.config.*;
import com.hazelcast.core.*;

public interface HazelcastProvider {
  HazelcastInstance createInstance(Config config);
  
  void shutdownAll();
}
