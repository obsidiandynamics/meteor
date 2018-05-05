package com.obsidiandynamics.meteor;

import static org.junit.Assert.*;

import org.junit.*;

import com.hazelcast.config.*;
import com.obsidiandynamics.assertion.*;
import com.obsidiandynamics.func.*;
import com.obsidiandynamics.zerolog.*;

public final class SubscriberConfigTest {
  @Test
  public void testConfig() {
    final ElectionConfig electionConfig = new ElectionConfig();
    final ExceptionHandler exceptionHandler = ExceptionHandler.nop();
    final String group = "group";
    final InitialOffsetScheme initialOffsetScheme = InitialOffsetScheme.EARLIEST;
    final Zlg zlg = Zlg.forDeclaringClass().get();
    final StreamConfig streamConfig = new StreamConfig();
    final double staleReadSafetyMargin = 0.5;
    final int minLeaseExtendIntervalMillis = 500;
    final MapStoreConfig mapStoreConfig = new MapStoreConfig()
        .setEnabled(true)
        .setClassName("TestClass");
    
    final SubscriberConfig config = new SubscriberConfig()
        .withElectionConfig(electionConfig)
        .withExceptionHandler(exceptionHandler)
        .withGroup(group)
        .withInitialOffsetScheme(initialOffsetScheme)
        .withZlg(zlg)
        .withStreamConfig(streamConfig)
        .withStaleReadSafetyMargin(staleReadSafetyMargin)
        .withMinLeaseExtendInterval(minLeaseExtendIntervalMillis)
        .withMapStoreConfig(mapStoreConfig);
    assertEquals(electionConfig, config.getElectionConfig());
    assertEquals(exceptionHandler, config.getExceptionHandler());
    assertEquals(group, config.getGroup());
    assertEquals(initialOffsetScheme, config.getInitialOffsetScheme());
    assertEquals(zlg, config.getZlg());
    assertEquals(streamConfig, config.getStreamConfig());
    assertEquals(staleReadSafetyMargin, config.getStaleReadSafetyMargin(), Double.MIN_VALUE);
    assertEquals(minLeaseExtendIntervalMillis, config.getMinLeaseExtendInterval());
    assertEquals(mapStoreConfig, config.getMapStoreConfig());
    
    Assertions.assertToStringOverride(config);
  }
}
