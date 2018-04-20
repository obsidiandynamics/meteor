package com.obsidiandynamics.hazelq;

import com.obsidiandynamics.worker.*;

public interface Receiver extends Terminable, Joinable {
  @FunctionalInterface
  interface RecordHandler {
    void onRecord(Record record) throws InterruptedException;
  }
}
