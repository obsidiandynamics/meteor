package com.obsidiandynamics.hazelq;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.function.*;

import org.junit.*;

import com.hazelcast.core.*;
import com.obsidiandynamics.func.*;

public final class InstancePoolTest {
  @Test
  public void testSize() {
    final int size = 4;
    final InstancePool pool = new InstancePool(size, () -> null);
    assertEquals(size, pool.size());
  }
  
  @Test
  public void testGet() {
    final int size = 4;
    final Supplier<HazelcastInstance> supplier = Classes.cast(mock(Supplier.class));
    when(supplier.get()).thenAnswer(invocation -> mock(HazelcastInstance.class));
    final InstancePool pool = new InstancePool(size, supplier);
    
    // get more instances than what's in the pool
    final List<HazelcastInstance> instances = new ArrayList<>(size * 2);
    for (int i = 0; i < size * 2; i++) {
      final HazelcastInstance instance = pool.get();
      assertNotNull(instance);
      instances.add(instance);
    }
    
    verify(supplier, times(4)).get();
    
    // verify the number of unique instances
    final Set<HazelcastInstance> set = new HashSet<>(instances);
    assertEquals(size, set.size());
  }
  
  @Test
  public void testPrestart() {
    final int size = 4;
    final Supplier<HazelcastInstance> supplier = Classes.cast(mock(Supplier.class));
    when(supplier.get()).thenAnswer(invocation -> mock(HazelcastInstance.class));
    final InstancePool pool = new InstancePool(size, supplier);
    
    pool.prestartAll();
    verify(supplier, times(4)).get();
  }
}
