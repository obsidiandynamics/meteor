package com.obsidiandynamics.meteor;

import java.util.*;

public interface RecordBatch extends Iterable<Record> {
  RecordBatch empty = new RecordBatch() {
    @Override
    public int size() {
      return 0;
    }

    @Override
    public Iterator<Record> iterator() {
      return Collections.emptyIterator();
    }
  };
  
  static RecordBatch empty() { return empty; }

  int size();

  @Override
  Iterator<Record> iterator();
  
  default boolean isEmpty() {
    return size() == 0;
  }
  
  default List<Record> toList() {
    final List<Record> list = new ArrayList<>(size());
    readInto(list);
    return list;
  }
  
  default void readInto(Collection<? super Record> sink) {
    iterator().forEachRemaining(sink::add);
  }
  
  default Record first() {
    final List<Record> records = toList();
    if (records.isEmpty()) throw new NoSuchElementException();
    return records.get(0);
  }
  
  default Record last() {
    final List<Record> records = toList();
    if (records.isEmpty()) throw new NoSuchElementException();
    return records.get(records.size() - 1);
  }
}
