package com.obsidiandynamics.hazelq;

import static org.junit.Assert.*;

import org.junit.*;

import com.obsidiandynamics.assertion.*;

public final class ElectionConfigTest {
  @Test
  public void testFields() {
    final ElectionConfig c = new ElectionConfig()
        .withLeaseDuration(100)
        .withScavengeInterval(200);
    
    assertEquals(100, c.getLeaseDuration());
    assertEquals(200, c.getScavengeInterval());
  }

  @Test
  public void testToString() {
    Assertions.assertToStringOverride(new ElectionConfig());
  }
}
