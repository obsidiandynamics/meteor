package com.obsidiandynamics.hazelq;

import static org.junit.Assert.*;

import org.junit.*;

import com.hazelcast.config.*;

public final class TestHazelcastProviderTest {
  @Test
  public void test() {
    final Config config = new Config()
        .setProperty("hazelcast.shutdownhook.enabled", "false")
        .setProperty("hazelcast.logging.type", "none");
    
    final TestHazelcastProvider testProvider = new TestHazelcastProvider();
    assertNotNull(testProvider.createInstance(config));
    testProvider.shutdownAll();
  }
}
