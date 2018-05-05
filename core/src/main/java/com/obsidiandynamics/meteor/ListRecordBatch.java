package com.obsidiandynamics.meteor;

import java.util.*;

final class ListRecordBatch implements RecordBatch {
  private final List<Record> records;
  
  ListRecordBatch(List<Record> records) {
    this.records = records;
  }
  
  @Override
  public int size() {
    return records.size();
  }
  
  @Override
  public Iterator<Record> iterator() {
    return records.iterator();
  }
}
