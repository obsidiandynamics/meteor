package com.obsidiandynamics.meteor;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

public final class RecordBatchTest {
  @Test
  public void testDefaultMethods() {
    final Record r0 = new Record(new byte[0]);
    final Record r1 = new Record(new byte[0]);
    final RecordBatch b0 = new ListRecordBatch(Arrays.asList(r0, r1));
    assertFalse(b0.isEmpty());
    assertEquals(Arrays.asList(r0, r1), b0.toList());
    assertEquals(r0, b0.first());
    assertEquals(r1, b0.last());
    
    final RecordBatch b1 = new ListRecordBatch(Collections.emptyList());
    assertTrue(b1.isEmpty());
    assertEquals(Collections.emptyList(), b1.toList());
  }
  
  @Test
  public void testEmpty() {
    assertEquals(0, RecordBatch.empty().size());
    assertFalse(RecordBatch.empty().iterator().hasNext());
  }
  
  @Test(expected=NoSuchElementException.class)
  public void testEmptyFirst() {
    RecordBatch.empty().first(); 
  }
  
  @Test(expected=NoSuchElementException.class)
  public void testEmptyLast() {
    RecordBatch.empty().last(); 
  }
}
