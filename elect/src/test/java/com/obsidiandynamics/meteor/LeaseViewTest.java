package com.obsidiandynamics.meteor;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

import com.obsidiandynamics.assertion.*;

public final class LeaseViewTest {
  @Test
  public void testToString() {
    Assertions.assertToStringOverride(new LeaseViewImpl(10));
  }
  
  @Test
  public void testViewVersion() {
    assertEquals(10, new LeaseViewImpl(10).getVersion());
  }
  
  @Test
  public void testCopy() {
    final LeaseViewImpl orig = new LeaseViewImpl(0);
    final Lease lease0 = Lease.forever(UUID.randomUUID());
    orig.put("key0", lease0);
    
    final LeaseViewImpl copy = new LeaseViewImpl(orig, 1);
    assertEquals(1, copy.getVersion());
    assertEquals(Collections.singletonMap("key0", lease0), copy.asMap());
    
    // mutate the copy and ensure that the original remains unchanged
    final Lease lease1 = Lease.forever(UUID.randomUUID());
    copy.put("key1", lease1);
    assertEquals(Collections.singletonMap("key0", lease0), orig.asMap());
    
    // mutate the original and ensure that the copy remains unchanged
    final Lease lease2 = Lease.forever(UUID.randomUUID());
    orig.put("key2", lease2);
    assertEquals(2, copy.asMap().size());
    assertEquals(lease0, copy.asMap().get("key0"));
    assertEquals(lease1, copy.asMap().get("key1"));
  }
  
  @Test
  public void testGetTenant() {
    final LeaseViewImpl v = new LeaseViewImpl(0);
    final UUID c = UUID.randomUUID();
    v.put("resource", new Lease(c, Long.MAX_VALUE));
    assertEquals(c, v.getLease("resource").getTenant());
  }
  
  @Test
  public void testIsCurrentTenant() {
    final LeaseViewImpl v = new LeaseViewImpl(0);
    final UUID c = UUID.randomUUID();
    v.put("resource", new Lease(c, Long.MAX_VALUE));
    assertTrue(v.isCurrentTenant("resource", c));
  }
}
