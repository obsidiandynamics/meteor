package com.obsidiandynamics.meteor;

import com.obsidiandynamics.yconf.*;
import com.obsidiandynamics.zerolog.*;

@Y
public final class PublisherConfig {
  @YInject
  private Zlg zlg = Zlg.forDeclaringClass().get();
  
  @YInject
  private StreamConfig streamConfig = new StreamConfig();
  
  Zlg getZlg() {
    return zlg;
  }
  
  public PublisherConfig withZlg(Zlg zlg) {
    this.zlg = zlg;
    return this;
  }

  StreamConfig getStreamConfig() {
    return streamConfig;
  }

  public PublisherConfig withStreamConfig(StreamConfig streamConfig) {
    this.streamConfig = streamConfig;
    return this;
  }

  @Override
  public String toString() {
    return PublisherConfig.class.getSimpleName() + " [streamConfig=" + streamConfig + "]";
  }
}
