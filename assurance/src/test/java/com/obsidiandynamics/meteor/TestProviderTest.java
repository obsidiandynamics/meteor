package com.obsidiandynamics.meteor;

import static org.junit.Assert.*;

import org.junit.*;

import com.hazelcast.config.*;

public final class TestProviderTest {
  @Test
  public void test() {
    final Config config = new Config()
        .setProperty("hazelcast.shutdownhook.enabled", "false")
        .setProperty("hazelcast.logging.type", "none");
    
    final TestProvider testProvider = new TestProvider();
    assertNotNull(testProvider.createInstance(config));
    testProvider.shutdownAll();
  }
}
