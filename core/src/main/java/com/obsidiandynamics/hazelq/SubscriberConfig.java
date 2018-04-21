package com.obsidiandynamics.hazelq;

import com.hazelcast.config.*;
import com.obsidiandynamics.func.*;
import com.obsidiandynamics.yconf.*;
import com.obsidiandynamics.zerolog.*;

@Y
public final class SubscriberConfig {
  @YInject
  private Zlg zlg = Zlg.forDeclaringClass().get();

  @YInject
  private ExceptionHandler exceptionHandler = new LogAwareExceptionHandler(this::getZlg);

  @YInject
  private StreamConfig streamConfig = new StreamConfig();

  @YInject
  private String group = null;

  @YInject
  private InitialOffsetScheme initialOffsetScheme = InitialOffsetScheme.AUTO;

  @YInject
  private ElectionConfig electionConfig = new ElectionConfig();

  @YInject
  private double staleReadSafetyMargin = 0.1;

  @YInject
  private int minLeaseExtendIntervalMillis = 1_000;

  @YInject
  private MapStoreConfig mapStoreConfig = new MapStoreConfig().setEnabled(false);

  Zlg getZlg() {
    return zlg;
  }

  public SubscriberConfig withZlg(Zlg zlg) {
    this.zlg = zlg;
    return this;
  }

  ExceptionHandler getExceptionHandler() {
    return exceptionHandler;
  }

  public SubscriberConfig withExceptionHandler(ExceptionHandler exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
    return this;
  }

  StreamConfig getStreamConfig() {
    return streamConfig;
  }

  public SubscriberConfig withStreamConfig(StreamConfig streamConfig) {
    this.streamConfig = streamConfig;
    return this;
  }

  boolean hasGroup() {
    return group != null;
  }

  String getGroup() {
    return group;
  }

  public SubscriberConfig withGroup(String group) {
    this.group = group;
    return this;
  }

  InitialOffsetScheme getInitialOffsetScheme() {
    return initialOffsetScheme;
  }

  public SubscriberConfig withInitialOffsetScheme(InitialOffsetScheme initialOffsetScheme) {
    this.initialOffsetScheme = initialOffsetScheme;
    return this;
  }

  ElectionConfig getElectionConfig() {
    return electionConfig;
  }

  public SubscriberConfig withElectionConfig(ElectionConfig electionConfig) {
    this.electionConfig = electionConfig;
    return this;
  }

  double getStaleReadSafetyMargin() {
    return staleReadSafetyMargin;
  }

  public SubscriberConfig withStaleReadSafetyMargin(double staleReadSafetyMargin) {
    this.staleReadSafetyMargin = staleReadSafetyMargin;
    return this;
  }

  int getMinLeaseExtendInterval() {
    return minLeaseExtendIntervalMillis;
  }

  public SubscriberConfig withMinLeaseExtendInterval(int minLeaseExtendIntervalMillis) {
    this.minLeaseExtendIntervalMillis = minLeaseExtendIntervalMillis;
    return this;
  }

  MapStoreConfig getMapStoreConfig() {
    return mapStoreConfig;
  }

  public SubscriberConfig withMapStoreConfig(MapStoreConfig mapStoreConfig) {
    this.mapStoreConfig = mapStoreConfig;
    return this;
  }

  @Override
  public String toString() {
    return SubscriberConfig.class.getSimpleName() + " [exceptionHandler=" + exceptionHandler 
        + ", streamConfig=" + streamConfig
        + ", group=" + group + ", initialOffsetScheme=" + initialOffsetScheme 
        + ", electionConfig=" + electionConfig + ", staleReadSafetyMargin=" + staleReadSafetyMargin
        + ", minLeaseExtendInterval=" + minLeaseExtendIntervalMillis 
        + ", mapStoreConfig=" + mapStoreConfig + "]";
  }
}
