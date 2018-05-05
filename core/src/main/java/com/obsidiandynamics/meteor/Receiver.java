package com.obsidiandynamics.meteor;

import com.obsidiandynamics.worker.*;

public interface Receiver extends Terminable, Joinable {
  @FunctionalInterface
  interface RecordHandler {
    void onRecord(Record record) throws InterruptedException;
  }
}
