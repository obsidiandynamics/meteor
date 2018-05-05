package com.obsidiandynamics.meteor;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.obsidiandynamics.yconf.*;

@Y(GridProvider.Mapper.class)
public final class GridProvider implements HazelcastProvider {
  public static final class Mapper implements TypeMapper {
    @Override public Object map(YObject y, Class<?> type) {
      return instance;
    }
  }
  
  private static final GridProvider instance = new GridProvider();
  
  public static GridProvider getInstance() {
    return instance;
  }
  
  private GridProvider() {}
  
  @Override
  public HazelcastInstance createInstance(Config config) {
    return Hazelcast.newHazelcastInstance(config);
  }

  @Override
  public void shutdownAll() {
    Hazelcast.shutdownAll();
  }
}
