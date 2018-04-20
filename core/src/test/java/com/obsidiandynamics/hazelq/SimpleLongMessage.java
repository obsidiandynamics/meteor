package com.obsidiandynamics.hazelq;

import java.nio.*;

final class SimpleLongMessage {
  final long value;

  SimpleLongMessage(long value) { this.value = value; }
  
  byte[] pack() {
    final ByteBuffer buf = ByteBuffer.allocate(8);
    buf.putLong(value);
    return buf.array();
  }
  
  static SimpleLongMessage unpack(byte[] bytes) {
    return new SimpleLongMessage(ByteBuffer.wrap(bytes).getLong());
  }

  @Override
  public String toString() {
    return SimpleLongMessage.class.getSimpleName() + " [id=" + value + "]";
  }
}