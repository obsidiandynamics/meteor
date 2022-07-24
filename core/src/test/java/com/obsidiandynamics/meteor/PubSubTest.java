package com.obsidiandynamics.meteor;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import org.junit.*;
import org.junit.runners.*;

import com.hazelcast.core.*;
import com.obsidiandynamics.func.*;
import com.obsidiandynamics.meteor.Receiver.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class PubSubTest extends AbstractPubSubTest {
  private static class TestHandler implements RecordHandler {
    private final List<SimpleLongMessage> received = new CopyOnWriteArrayList<>();
    
    private volatile long lastId = -1;
    private volatile AssertionError error;

    @Override
    public void onRecord(Record record) {
      final SimpleLongMessage message = SimpleLongMessage.unpack(record.getData());
      final long id = message.value;
      if (lastId == -1) {
        lastId = id;
      } else {
        final long expectedBallotId = lastId + 1;
        if (id != expectedBallotId) {
          error = new AssertionError("Expected ID " + expectedBallotId + ", got " + id);
          throw error;
        } else {
          lastId = id;
        }
      }
      received.add(message);
    }
  }
  
  @Test
  public void testPubSubUngrouped() {
    testPubSub(3, 5, new InstancePool(2, this::newInstance), null);
  }
  
  @Test
  public void testPubSubGrouped() {
    testPubSub(3, 5, new InstancePool(2, this::newInstance), randomGroup());
  }
  
  private void testPubSub(int numReceivers, int numMessages, InstancePool instancePool, String group) {
    final String stream = "s";
    
    // common configuration
    final StreamConfig streamConfig = new StreamConfig()
        .withName(stream)
        .withHeapCapacity(numMessages);
    
    // prestart the instance pool
    final int prestartInstances = Math.min(1 + numReceivers, instancePool.size());
    instancePool.prestart(prestartInstances);

    // create subscribers with receivers
    final ExceptionHandler eh = mockExceptionHandler();
    final SubscriberConfig subConfig = new SubscriberConfig()
        .withGroup(group)
        .withExceptionHandler(eh)
        .withElectionConfig(new ElectionConfig().withScavengeInterval(1))
        .withStreamConfig(streamConfig);
        
    final List<TestHandler> handlers = new ArrayList<>(numReceivers);
    for (int i = 0; i < numReceivers; i++) {
      final HazelcastInstance instance = instancePool.get();
      final Subscriber s = configureSubscriber(instance, subConfig);
      s.attachReceiver(register(new TestHandler(), handlers), 10);
    }
    
    // create a publisher and publish the messages
    final PublisherConfig pubConfig = new PublisherConfig()
        .withStreamConfig(streamConfig);
    final HazelcastInstance instance = instancePool.get();
    final Publisher p = configurePublisher(instance, pubConfig);
    
    final List<FuturePublishCallback> futures = new ArrayList<>(numMessages);
    for (int i = 0; i < numMessages; i++) {
      register(p.publishAsync(new Record(new SimpleLongMessage(i).pack())), futures);
    }
    
    // wait until all publish confirmations have been processed
    wait.until(() -> {
      final int completedFutures = (int) futures.stream().filter(CompletableFuture::isDone).count();
      assertEquals(numMessages, completedFutures);
    });
    
    final int errorredFutures = (int) futures.stream().filter(CompletableFuture::isCompletedExceptionally).count();
    assertEquals(0, errorredFutures);
    
    // verify received messages; if a failure is detected, deep dive into the contents for debugging
    boolean success = false;
    try {
      wait.until(() -> {
        // list of handlers that have received at least one message
        final List<TestHandler> receivedHandlers = handlers.stream()
            .filter(h -> h.received.size() != 0).collect(Collectors.toList());
        
        // the number of expected receivers depends on whether a group has been set
        if (group != null) {
          assertEquals(1, receivedHandlers.size());
        } else {
          assertEquals(numReceivers, receivedHandlers.size());
        }
        
        for (TestHandler handler : receivedHandlers) {
          assertNull(handler.error);
          assertEquals(numMessages, handler.received.size());
          long index = 0;
          for (SimpleLongMessage m  : handler.received) {
            assertEquals(index, m.value);
            index++;
          }
        }
      });
      success = true;
    } finally {
      if (! success) {
        System.out.format("numReceivers=%d, numMessages=%d, instances.size=%d, group=%s\n",
                          numReceivers, numMessages, instancePool.size(), group);
        for (TestHandler handler : handlers) {
          System.out.println("---");
          for (SimpleLongMessage m : handler.received) {
            System.out.println("- " + m);
          }
        }
      }
    }
    
    verifyNoError(eh);
  }
}
