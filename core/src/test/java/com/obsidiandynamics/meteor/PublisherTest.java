package com.obsidiandynamics.meteor;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import org.junit.*;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.hazelcast.ringbuffer.*;
import com.hazelcast.util.executor.*;

public final class PublisherTest extends AbstractPubSubTest {
  private static class TestCallback implements PublishCallback {
    volatile long offset = Record.UNASSIGNED_OFFSET;
    volatile Throwable error;

    @Override
    public void onComplete(long offset, Throwable error) {
      this.offset = offset;
      this.error = error;
    }

    boolean isComplete() {
      return offset != Record.UNASSIGNED_OFFSET || error != null;
    }
  }

  /**
   *  Publishes to a bounded buffer, where the backing store is a NOP.
   *
   */
  @Test
  public void testPublishToBoundedBuffer() throws InterruptedException, ExecutionException {
    final String stream = "s";
    final int capacity = 10;

    final DefaultPublisher p =
        configurePublisher(new PublisherConfig().withStreamConfig(new StreamConfig()
                                                                  .withName(stream)
                                                                  .withHeapCapacity(capacity)));
    final Ringbuffer<byte[]> buffer = p.getInstance().getRingbuffer(Namespace.METEOR_STREAM.qualify(stream));
    final List<Record> records = new ArrayList<>();
    final List<TestCallback> callbacks = new ArrayList<>();
    
    assertNotNull(p.getConfig());

    final int initialMessages = 5;
    publish(initialMessages, p, records, callbacks);

    assertEquals(initialMessages, records.size());
    assertEquals(initialMessages, callbacks.size());
    wait.until(() -> assertEquals(initialMessages, completed(callbacks).size()));
    assertNoError(callbacks);
    for (int i = 0; i < initialMessages; i++) {
      assertEquals(i, records.get(i).getOffset());
    }
    assertEquals(initialMessages, buffer.size());
    final List<byte[]> initialItems = readRemaining(buffer, 0);
    assertEquals(initialMessages, initialItems.size());

    final int furtherMessages = 20;
    publish(furtherMessages, p, records, callbacks);

    wait.until(() -> assertEquals(initialMessages + furtherMessages, completed(callbacks).size()));
    assertNoError(callbacks);
    assertEquals(capacity, buffer.size());
    final List<byte[]> allItems = readRemaining(buffer, 0);
    assertEquals(capacity, allItems.size());
  }
  
  /**
   *  Publishes to a buffer that uses a simple {@link HeapRingbufferStore} as its backing store.
   *
   */
  @Test
  public void testPublishToStoredBuffer() throws InterruptedException, ExecutionException {
    final String stream = "s";
    final int capacity = 10;

    final DefaultPublisher p =
        configurePublisher(new PublisherConfig()
                           .withStreamConfig(new StreamConfig()
                                             .withName(stream)
                                             .withHeapCapacity(capacity)
                                             .withRingbufferStoreConfig(new RingbufferStoreConfig()
                                                                        .setFactoryClassName(HeapRingbufferStore.Factory.class.getName()))));
    final Ringbuffer<byte[]> buffer = p.getInstance().getRingbuffer(Namespace.METEOR_STREAM.qualify(stream));
    final List<Record> records = new ArrayList<>();
    final List<TestCallback> callbacks = new ArrayList<>();

    final int initialMessages = 5;
    publish(initialMessages, p, records, callbacks);

    wait.until(() -> assertEquals(initialMessages, completed(callbacks).size()));
    assertNoError(callbacks);
    assertEquals(initialMessages, buffer.size());
    final List<byte[]> initialItems = readRemaining(buffer, 0);
    assertEquals(initialMessages, initialItems.size());

    final int furtherMessages = 20;
    publish(furtherMessages, p, records, callbacks);

    wait.until(() -> assertEquals(initialMessages + furtherMessages, completed(callbacks).size()));
    assertNoError(callbacks);
    assertEquals(capacity, buffer.size());
    final List<byte[]> allItems = readRemaining(buffer, 0);
    assertEquals(initialMessages + furtherMessages, allItems.size());
  }
  
  /**
   *  Tests direct publishing.
   *
   */
  @Test
  public void testPublishDirect() throws InterruptedException, ExecutionException {
    final String stream = "s";
    final int capacity = 10;

    final DefaultPublisher p =
        configurePublisher(new PublisherConfig()
                           .withStreamConfig(new StreamConfig()
                                             .withName(stream)
                                             .withHeapCapacity(capacity)
                                             .withRingbufferStoreConfig(new RingbufferStoreConfig()
                                                                        .setFactoryClassName(HeapRingbufferStore.Factory.class.getName()))));
    final Ringbuffer<byte[]> buffer = p.getInstance().getRingbuffer(Namespace.METEOR_STREAM.qualify(stream));

    final long offset0 = p.publishDirect(new Record("h0".getBytes()));
    assertEquals(0, offset0);
    final long offset1 = p.publishDirect(new Record("h1".getBytes()));
    assertEquals(1, offset1);

    final List<byte[]> items = readRemaining(buffer, 0);
    assertEquals(2, items.size());
    assertEquals("h0", new String(items.get(0)));
    assertEquals("h1", new String(items.get(1)));
  }

  /**
   *  Tests publish failure by rigging a mock {@link Ringbuffer} to return a {@link CompletedFuture} with
   *  an error.
   */
  @Test
  public void testPublishFailure() {
    final String stream = "s";
    final int capacity = 10;

    final HazelcastInstance realInstance = newInstance();
    final HazelcastInstance mockInstance = mock(HazelcastInstance.class);
    @SuppressWarnings("unchecked")
    final Ringbuffer<byte[]> mockBuffer = mock(Ringbuffer.class);
    when(mockInstance.<byte[]>getRingbuffer(any())).thenReturn(mockBuffer);
    when(mockInstance.getConfig()).thenReturn(realInstance.getConfig());
    final RuntimeException cause = new RuntimeException("error");
    when(mockBuffer.addAllAsync(any(), any())).then(invocation -> {
      return new CompletedFuture<>(null, cause, Runnable::run);
    });

    final DefaultPublisher p =
        configurePublisher(mockInstance,
                           new PublisherConfig()
                           .withStreamConfig(new StreamConfig()
                                             .withName(stream)
                                             .withHeapCapacity(capacity)
                                             .withRingbufferStoreConfig(new RingbufferStoreConfig()
                                                                        .setFactoryClassName(HeapRingbufferStore.Factory.class.getName()))));
    final List<Record> records = new ArrayList<>();
    final List<TestCallback> callbacks = new ArrayList<>();

    publish(1, p, records, callbacks);
    wait.until(() -> assertEquals(1, completed(callbacks).size()));
    assertEquals(Record.UNASSIGNED_OFFSET, records.get(0).getOffset());
    assertEquals(Record.UNASSIGNED_OFFSET, callbacks.get(0).offset);
    assertEquals(cause, callbacks.get(0).error);
  }

  private static void publish(int numMessages, Publisher publisher, List<Record> records, List<TestCallback> callbacks) {
    for (int i = 0; i < numMessages; i++) {
      final TestCallback callback = new TestCallback();
      callbacks.add(callback);
      final Record record = new Record("hello".getBytes());
      records.add(record);
      publisher.publishAsync(record, callback);
    }
  }

  
  /**
   *  Reads the remaining contents of the ringbuffer from a given starting point, automatically fast-forwarding 
   *  the starting point if a {@link StaleSequenceException} is caught.
   *
   */
  private static List<byte[]> readRemaining(Ringbuffer<byte[]> buffer, long startSequence) throws InterruptedException, ExecutionException {
    long adjStartSequence = startSequence;
    final List<byte[]> items = new ArrayList<>();
    for (;;) {
      final ReadResultSet<byte[]> results;
      try {
        final int toRead = (int) Math.min(1_000, buffer.capacity());
        results = buffer.readManyAsync(adjStartSequence, 0, toRead, null).get();
      } catch (ExecutionException e) {
        if (e.getCause() instanceof StaleSequenceException) {
          System.out.format("SSE: fast-forwarding start sequence to %d\n", buffer.headSequence());
          adjStartSequence = buffer.headSequence();
          continue;
        } else {
          throw e;
        }
      }
      
      if (results.size() > 0) {
        results.forEach(items::add);
        adjStartSequence += results.size();
      } else {
        return items;
      }
    }
  }

  private static List<TestCallback> completed(List<TestCallback> callbacks) {
    return callbacks.stream().filter(TestCallback::isComplete).collect(Collectors.toList());
  }

  private static void assertNoError(List<TestCallback> callbacks) {
    for (TestCallback callback : callbacks) {
      if (callback.error != null) {
        throw new AssertionError(callback.error);
      }
    }
  }
}