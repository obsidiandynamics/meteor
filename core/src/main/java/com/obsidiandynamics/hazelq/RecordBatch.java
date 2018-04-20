package com.obsidiandynamics.hazelq;

import java.util.*;

public interface RecordBatch extends Iterable<Record> {
  final RecordBatch empty = new RecordBatch() {
    @Override
    public int size() {
      return 0;
    }

    @Override
    public Iterator<Record> iterator() {
      return Collections.emptyIterator();
    }
  };
  
  public static RecordBatch empty() { return empty; }

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
}
