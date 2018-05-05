package com.obsidiandynamics.meteor;

import static org.junit.Assert.*;

import org.junit.*;

import com.obsidiandynamics.assertion.*;

public class RecordTest {
  @Test
  public void testCreateWithData() {
    final byte[] data = { (byte) 0x00, (byte) 0x01 };
    final Record record = new Record(data);
    assertArrayEquals(data, record.getData());
    assertEquals(Record.UNASSIGNED_OFFSET, record.getOffset());
    
    record.setOffset(100);
    assertEquals(100, record.getOffset());
  }
  
  @Test
  public void testCreateWithDataAndOffset() {
    final byte[] data = { (byte) 0x00, (byte) 0x01 };
    final Record record = new Record(data, 100);
    assertArrayEquals(data, record.getData());
    assertEquals(100, record.getOffset());
  }
  
  @Test
  public void testToString() {
    Assertions.assertToStringOverride(new Record(new byte[0]));
  }
}
