package com.obsidiandynamics.meteor;

import static com.obsidiandynamics.retry.Retry.*;

import java.util.*;
import java.util.concurrent.*;

import com.hazelcast.core.*;
import com.hazelcast.ringbuffer.*;
import com.obsidiandynamics.meteor.util.*;
import com.obsidiandynamics.nodequeue.*;
import com.obsidiandynamics.retry.*;
import com.obsidiandynamics.worker.*;
import com.obsidiandynamics.zerolog.util.*;

final class DefaultPublisher implements Publisher, Joinable {
  private static final int PUBLISH_MAX_YIELDS = 100;
  private static final int PUBLISH_BACKOFF_MILLIS = 1;
  private static final int MAX_BATCH_SIZE = 1_000;
  
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
        .withExceptionMatcher(isA(HazelcastException.class))
        .withAttempts(Integer.MAX_VALUE)
        .withBackoff(100)
        .withFaultHandler(config.getZlg()::w)
        .withErrorHandler(config.getZlg()::e);
    buffer = new RetryableRingbuffer<>(retry, StreamHelper.getRingbuffer(instance, streamConfig));
    
    publishThread = WorkerThread.builder()
        .withOptions(new WorkerOptions().daemon().withName(Publisher.class, streamConfig.getName(), "publisher"))
        .onCycle(this::publisherCycle)
        .onUncaughtException(new ZlgWorkerExceptionHandler(config.getZlg()))
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
    List<AsyncRecord> recs = null;
    
    for (;;) {
      final AsyncRecord rec = queueConsumer.poll();
      if (rec != null) {
        if (recs == null) {
          recs = new ArrayList<>();
          yields = 0;
        }
        recs.add(rec);
        if (recs.size() == MAX_BATCH_SIZE) {
          sendNow(recs);
          return;
        }
      } else {
        if (recs != null) {
          sendNow(recs);
        } else if (yields++ < PUBLISH_MAX_YIELDS) {
          Thread.yield();
        } else {
          //noinspection BusyWait
          Thread.sleep(PUBLISH_BACKOFF_MILLIS);
        }
        return;
      }
    }
  }
  
  private void sendNow(List<AsyncRecord> recs) throws InterruptedException {
    final List<byte[]> items = new ArrayList<>(recs.size());
    final int size = recs.size();
    for (int i = 0; i < size; i++) {
      items.add(recs.get(i).record.getData());
    }
    
    final ICompletableFuture<Long> f = buffer.addAllAsync(items, OverflowPolicy.OVERWRITE);
    try {
      final long lastSequence = f.get();
      final long firstSequence = lastSequence - size + 1;

      for (int i = 0; i < size; i++) {
        final AsyncRecord rec = recs.get(i);
        final long offset = firstSequence + i;
        rec.record.setOffset(offset);
        rec.callback.onComplete(offset, null);
      }
    } catch (ExecutionException e) {
      for (AsyncRecord rec : recs) {
        rec.callback.onComplete(Record.UNASSIGNED_OFFSET, e.getCause());
      }
    }
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
