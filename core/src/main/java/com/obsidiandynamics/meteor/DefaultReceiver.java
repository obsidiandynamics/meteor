package com.obsidiandynamics.meteor;

import com.obsidiandynamics.worker.*;
import com.obsidiandynamics.zerolog.util.*;

public final class DefaultReceiver implements Receiver {
  private final Subscriber subscriber;
  
  private final RecordHandler recordHandler;
  
  private final int pollTimeoutMillis;
  
  private final WorkerThread pollerThread;
  
  DefaultReceiver(Subscriber subscriber, RecordHandler recordHandler, int pollTimeoutMillis) {
    this.subscriber = subscriber;
    this.recordHandler = recordHandler;
    this.pollTimeoutMillis = pollTimeoutMillis;
    pollerThread = WorkerThread.builder()
        .withOptions(new WorkerOptions()
                     .daemon()
                     .withName(Receiver.class, subscriber.getConfig().getStreamConfig().getName(), "poller"))
        .onCycle(this::pollerCycle)
        .onUncaughtException(new ZlgWorkerExceptionHandler(subscriber.getConfig().getZlg()))
        .buildAndStart();
  }
  
  private void pollerCycle(WorkerThread thread) throws InterruptedException {
    final RecordBatch batch = subscriber.poll(pollTimeoutMillis);
    for (Record record : batch) {
      recordHandler.onRecord(record);
    }
  }
  
  @Override
  public Joinable terminate() {
    pollerThread.terminate();
    return this;
  }

  @Override
  public boolean join(long timeoutMillis) throws InterruptedException {
    return pollerThread.join(timeoutMillis);
  }
  
  WorkerState getThreadState() {
    return pollerThread.getState();
  }
}
