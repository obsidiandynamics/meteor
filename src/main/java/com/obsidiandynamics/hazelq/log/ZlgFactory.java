package com.obsidiandynamics.hazelq.log;

import com.hazelcast.logging.*;
import com.obsidiandynamics.zerolog.*;

public final class ZlgFactory extends LoggerFactorySupport {
  @Override
  public ILogger createLogger(String name) {
    return new ZlgLogger(Zlg.forName(name).get());
  }
}
