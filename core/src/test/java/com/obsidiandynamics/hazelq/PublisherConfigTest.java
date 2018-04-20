package com.obsidiandynamics.hazelq;

import static org.junit.Assert.*;

import org.junit.*;
import org.slf4j.*;

import com.obsidiandynamics.assertion.*;

public final class PublisherConfigTest {
  @Test
  public void testConfig() {
    final Logger log = LoggerFactory.getLogger(SubscriberConfigTest.class);
    final StreamConfig streamConfig = new StreamConfig();
    
    final PublisherConfig config = new PublisherConfig()
        .withLog(log)
        .withStreamConfig(streamConfig);
    assertEquals(log, config.getLog());
    assertEquals(streamConfig, config.getStreamConfig());
    
    Assertions.assertToStringOverride(config);
  }
}
