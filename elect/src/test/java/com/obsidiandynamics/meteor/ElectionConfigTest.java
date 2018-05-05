package com.obsidiandynamics.meteor;

import static org.junit.Assert.*;

import org.junit.*;

import com.obsidiandynamics.assertion.*;
import com.obsidiandynamics.zerolog.*;

public final class ElectionConfigTest {
  @Test
  public void testFields() {
    final Zlg zlg = Zlg.forDeclaringClass().get();
    final ElectionConfig c = new ElectionConfig()
        .withZlg(zlg)
        .withLeaseDuration(100)
        .withScavengeInterval(200);
    
    assertEquals(zlg, c.getZlg());
    assertEquals(100, c.getLeaseDuration());
    assertEquals(200, c.getScavengeInterval());
  }

  @Test
  public void testToString() {
    Assertions.assertToStringOverride(new ElectionConfig());
  }
}
