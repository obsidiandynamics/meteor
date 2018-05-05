package com.obsidiandynamics.meteor;

import com.obsidiandynamics.yconf.*;
import com.obsidiandynamics.zerolog.*;

@Y
public final class ElectionConfig {
  @YInject
  private Zlg zlg = Zlg.forDeclaringClass().get();
  
  @YInject
  private int scavengeIntervalMillis = 100;
  
  @YInject
  private int leaseDurationMillis = 60_000;

  Zlg getZlg() {
    return zlg;
  }

  public ElectionConfig withZlg(Zlg zlg) {
    this.zlg = zlg;
    return this;
  }
  
  int getScavengeInterval() {
    return scavengeIntervalMillis;
  }

  public ElectionConfig withScavengeInterval(int scavengeIntervalMillis) {
    this.scavengeIntervalMillis = scavengeIntervalMillis;
    return this;
  }

  int getLeaseDuration() {
    return leaseDurationMillis;
  }

  public ElectionConfig withLeaseDuration(int leaseDurationMillis) {
    this.leaseDurationMillis = leaseDurationMillis;
    return this;
  }

  @Override
  public String toString() {
    return ElectionConfig.class.getSimpleName() + " [scavengeIntervalMillis=" + scavengeIntervalMillis + 
        ", leaseDurationMillis=" + leaseDurationMillis + "]";
  }
}
