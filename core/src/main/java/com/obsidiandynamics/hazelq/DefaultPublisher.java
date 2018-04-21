package com.obsidiandynamics.hazelq;

import com.hazelcast.core.*;
import com.hazelcast.ringbuffer.*;
import com.obsidiandynamics.hazelq.util.*;
import com.obsidiandynamics.nodequeue.*;
import com.obsidiandynamics.retry.*;
import com.obsidiandynamics.worker.*;

final class DefaultPublisher implements Publisher, Joinable {
  private static final int PUBLISH_MAX_YIELDS = 100;
  private static final int PUBLISH_BACKOFF_MILLIS = 1;
  
  private static class AsyncRecord {
    final Record record;
    final PublishCallback callback;
    
    AsyncRecord(Record record, PublishCallback callback) {
      this.record = record;
      this.callback = callback;
    }
  }
  
  private final HazelcastInstance instance;
  
  private final PublisherConfig config;
  
  private final WorkerThread publishThread;
  
  private final NodeQueue<AsyncRecord> queue = new NodeQueue<>();
  
  private final QueueConsumer<AsyncRecord> queueConsumer = queue.consumer();
  
  private final RetryableRingbuffer<byte[]> buffer;
  
  private int yields;

  DefaultPublisher(HazelcastInstance instance, PublisherConfig config) {
    this.instance = instance;
    this.config = config;
    final StreamConfig streamConfig = config.getStreamConfig();

    final Retry retry = new Retry()
        .withExceptionClass(HazelcastException.class)
        .withAttempts(Integer.MAX_VALUE)
        .withBackoff(100)
        .withFaultHandler(config.getZlg()::w)
        .withErrorHandler(config.getZlg()::e);
    buffer = new RetryableRingbuffer<>(retry, StreamHelper.getRingbuffer(instance, streamConfig));
    
    publishThread = WorkerThread.builder()
        .withOptions(new WorkerOptions().daemon().withName(Publisher.class, streamConfig.getName(), "publisher"))
        .onCycle(this::publisherCycle)
        .buildAndStart();
  }
  
  @Override
  public PublisherConfig getConfig() {
    return config;
  }
  
  HazelcastInstance getInstance() {
    return instance;
  }
  
  private void publisherCycle(WorkerThread t) throws InterruptedException {
    final AsyncRecord rec = queueConsumer.poll();
    if (rec != null) {
      sendNow(rec.record, rec.callback);
      yields = 0;
    } else if (yields++ < PUBLISH_MAX_YIELDS) {
      Thread.yield();
    } else {
      Thread.sleep(PUBLISH_BACKOFF_MILLIS);
    }
  }
  
  private void sendNow(Record record, PublishCallback callback) {
    final ICompletableFuture<Long> f = buffer.addAsync(record.getData(), OverflowPolicy.OVERWRITE);
    f.andThen(new ExecutionCallback<Long>() {
      @Override public void onResponse(Long offset) {
        final long offsetPrimitive = offset;
        record.setOffset(offsetPrimitive);
        callback.onComplete(offsetPrimitive, null);
      }

      @Override public void onFailure(Throwable error) {
        callback.onComplete(Record.UNASSIGNED_OFFSET, error);
      }
    });
  }
  
  @Override
  public long publishDirect(Record record) {
    final long sequence = buffer.add(record.getData());
    record.setOffset(sequence);
    return sequence;
  }

  @Override
  public void publishAsync(Record record, PublishCallback callback) {
    queue.add(new AsyncRecord(record, callback));
  }

  @Override
  public Joinable terminate() {
    publishThread.terminate();
    return this;
  }

  @Override
  public boolean join(long timeoutMillis) throws InterruptedException {
    return publishThread.join(timeoutMillis);
  }
}
