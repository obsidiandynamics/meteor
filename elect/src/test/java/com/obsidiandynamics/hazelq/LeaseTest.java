package com.obsidiandynamics.hazelq;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

import com.obsidiandynamics.assertion.*;

public final class LeaseTest {
  @Test
  public void testToString() {
    Assertions.assertToStringOverride(new Lease(UUID.randomUUID(), 0));
  }
  
  @Test
  public void testEqualsHashCode() {
    final UUID u0 = UUID.randomUUID();
    final UUID u1 = UUID.randomUUID();
    final Lease l1 = new Lease(u0, 0);
    final Lease l2 = new Lease(u1, 1);
    final Lease l3 = new Lease(u0, 0);
    final Lease l4 = l1;

    assertNotEquals(l1, l2);
    assertEquals(l1, l3);
    assertEquals(l1, l4);
    assertNotEquals(l1, new Object());

    assertNotEquals(l1.hashCode(), l2.hashCode());
    assertEquals(l1.hashCode(), l3.hashCode());
  }

  @Test
  public void testPackUnpack() {
    final Lease original = new Lease(UUID.randomUUID(), System.currentTimeMillis());
    final byte[] packed = original.pack();
    final Lease unpacked = Lease.unpack(packed);
    assertEquals(original, unpacked);
  }
  
  @Test
  public void testFields() {
    final UUID c = UUID.randomUUID();
    final Lease current = new Lease(c, Long.MAX_VALUE);
    assertEquals(c, current.getTenant());
    assertEquals(Long.MAX_VALUE, current.getExpiry());
  }
  
  @Test
  public void testHeldByAndCurrent() {
    final UUID c = UUID.randomUUID();
    final Lease current = new Lease(c, Long.MAX_VALUE);
    assertFalse(current.isVacant());
    assertTrue(current.isHeldBy(c));
    assertTrue(current.isCurrent());
    assertTrue(current.isHeldByAndCurrent(c));
    assertFalse(current.isHeldByAndCurrent(UUID.randomUUID()));
    
    final Lease vacant = Lease.vacant();
    assertTrue(vacant.isVacant());
    assertFalse(vacant.isHeldBy(c));
    assertFalse(vacant.isCurrent());
    assertFalse(vacant.isHeldByAndCurrent(c));
    
    final Lease expired = new Lease(c, 1);
    assertTrue(expired.isHeldBy(c));
    assertFalse(expired.isHeldBy(UUID.randomUUID()));
    assertFalse(expired.isCurrent());
    assertFalse(expired.isHeldByAndCurrent(c));
    assertFalse(expired.isHeldByAndCurrent(UUID.randomUUID()));
  }
  
  @Test
  public void testForever() {
    assertEquals(Long.MAX_VALUE, Lease.forever(UUID.randomUUID()).getExpiry());
  }
  
  @Test
  public void testExpired() {
    assertEquals(0, Lease.expired(UUID.randomUUID()).getExpiry());
  }
}
