package com.obsidiandynamics.meteor;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

import com.obsidiandynamics.assertion.*;

public final class RegistryTest {
  @Test
  public void testToString() {
    Assertions.assertToStringOverride(new Registry());
  }
  
  @Test
  public void testEnrolAll() {
    final UUID c = UUID.randomUUID();
    final Registry source = new Registry().withCandidate("resource", c);
    final Registry r = new Registry();
    r.enrolAll(source);
    assertEquals(Collections.singleton(c), r.getCandidatesView().get("resource"));
    assertEquals(1, r.getCandidatesView().size());
    assertEquals(Collections.singleton("resource"), r.getResourcesView());
  }
  
  @Test 
  public void testEnrolUnenrol() {
    final Registry r = new Registry();
    assertEquals(Collections.emptyMap(), r.getCandidatesView());    
    assertNull(r.getRandomCandidate("key"));
    
    final UUID c0 = UUID.randomUUID();
    r.enrol("key", c0);
    assertEquals(1, r.getCandidatesView().size());
    assertEquals(setOf(c0), r.getCandidatesView().get("key"));
    assertNotNull(r.getRandomCandidate("key"));
    
    final UUID c1 = UUID.randomUUID();
    r.enrol("key", c1);
    assertEquals(1, r.getCandidatesView().size());
    assertEquals(setOf(c0, c1), r.getCandidatesView().get("key"));
    assertNotNull(r.getRandomCandidate("key"));
    
    r.unenrol("key", c0);
    assertEquals(1, r.getCandidatesView().size());
    assertEquals(setOf(c1), r.getCandidatesView().get("key"));
    assertNotNull(r.getRandomCandidate("key"));
    
    r.unenrol("key", c1);
    assertEquals(Collections.emptyMap(), r.getCandidatesView());
    assertNull(r.getRandomCandidate("key"));
  }
  
  @SafeVarargs
  private static <T> Set<T> setOf(T... items) {
    return new HashSet<>(Arrays.asList(items));
  }
}
