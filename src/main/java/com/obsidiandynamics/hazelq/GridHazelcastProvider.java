package com.obsidiandynamics.hazelq;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.obsidiandynamics.yconf.*;

@Y(GridHazelcastProvider.Mapper.class)
public final class GridHazelcastProvider implements HazelcastProvider {
  public static final class Mapper implements TypeMapper {
    @Override public Object map(YObject y, Class<?> type) {
      return instance;
    }
  }
  
  private static final GridHazelcastProvider instance = new GridHazelcastProvider();
  
  public static GridHazelcastProvider getInstance() {
    return instance;
  }
  
  private GridHazelcastProvider() {}
  
  @Override
  public HazelcastInstance createInstance(Config config) {
    return Hazelcast.newHazelcastInstance(config);
  }

  @Override
  public void shutdownAll() {
    Hazelcast.shutdownAll();
  }
}
