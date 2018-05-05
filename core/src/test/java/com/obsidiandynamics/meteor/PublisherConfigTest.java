package com.obsidiandynamics.meteor;

import static org.junit.Assert.*;

import org.junit.*;

import com.obsidiandynamics.assertion.*;
import com.obsidiandynamics.zerolog.*;

public final class PublisherConfigTest {
  @Test
  public void testConfig() {
    final Zlg zlg = Zlg.forDeclaringClass().get();
    final StreamConfig streamConfig = new StreamConfig();
    
    final PublisherConfig config = new PublisherConfig()
        .withZlg(zlg)
        .withStreamConfig(streamConfig);
    assertEquals(zlg, config.getZlg());
    assertEquals(streamConfig, config.getStreamConfig());
    
    Assertions.assertToStringOverride(config);
  }
}
