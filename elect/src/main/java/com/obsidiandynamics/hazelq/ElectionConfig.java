package com.obsidiandynamics.hazelq;

import com.obsidiandynamics.yconf.*;

@Y
public final class ElectionConfig {
  @YInject
  private int scavengeIntervalMillis = 100;
  
  @YInject
  private int leaseDurationMillis = 60_000;
  
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
