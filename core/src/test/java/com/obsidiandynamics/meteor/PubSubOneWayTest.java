package com.obsidiandynamics.meteor;

import static org.junit.Assert.*;

import java.lang.invoke.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.*;

import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

import com.hazelcast.core.*;
import com.obsidiandynamics.func.*;
import com.obsidiandynamics.meteor.util.*;
import com.obsidiandynamics.testmark.*;
import com.obsidiandynamics.threads.*;
import com.obsidiandynamics.zerolog.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class PubSubOneWayTest extends AbstractPubSubTest {
  private static final Zlg zlg = Zlg.forClass(MethodHandles.lookup().lookupClass()).get();
  
  private final int scale = Testmark.getOptions(Scale.class, Scale.unity()).magnitude();
  
  @Test
  public void testOneWay() {
    testOneWay(2, 4, 10_000 * scale, 10, true, new InstancePool(2, this::newInstance), new OneWayOptions());
  }
  
  @Test
  public void testOneWayBenchmark() {
    Testmark.ifEnabled("one-way over grid", () -> {
      final OneWayOptions options = new OneWayOptions() {{
        verbose = true;
        printBacklog = false;
      }};
      final Supplier<InstancePool> poolSupplier = () -> new InstancePool(4, this::newGridInstance);
      final int messageSize = 100;
      final boolean randomBytes = false; // without compression this really makes no difference
      
      testOneWay(1, 1, 2_000_000 * scale, messageSize, randomBytes, poolSupplier.get(), options);
      testOneWay(1, 2, 2_000_000 * scale, messageSize, randomBytes, poolSupplier.get(), options);
      testOneWay(1, 4, 2_000_000 * scale, messageSize, randomBytes, poolSupplier.get(), options);
      testOneWay(2, 4, 1_000_000 * scale, messageSize, randomBytes, poolSupplier.get(), options);
      testOneWay(2, 8, 1_000_000 * scale, messageSize, randomBytes, poolSupplier.get(), options);
      testOneWay(4, 8, 500_000 * scale, messageSize, randomBytes, poolSupplier.get(), options);
      testOneWay(4, 16, 500_000 * scale, messageSize, randomBytes, poolSupplier.get(), options);
    });
  }
  
  private static class OneWayOptions {
    boolean verbose;
    boolean printBacklog;
  }
  
  private void testOneWay(int publishers, int subscribers, int messagesPerPublisher, int messageSize, 
                          boolean randomBytes, InstancePool instancePool, OneWayOptions options) {
    final int backlogTarget = 10_000;
    final int checkInterval = backlogTarget;
    final String stream = "s";
    final byte[] fixedMessage = randomBytes ? null : new byte[messageSize];
    final int capacity = backlogTarget * publishers * 2;
    final int pollTimeoutMillis = 100;
    
    // common configuration
    final StreamConfig streamConfig = new StreamConfig()
        .withName(stream)
        .withHeapCapacity(capacity);
    
    if (options.verbose) System.out.format("Prestarting instances for %d/%d pub/sub... ", publishers, subscribers);
    final int prestartInstances = Math.min(publishers + subscribers, instancePool.size());
    instancePool.prestart(prestartInstances);
    if (options.verbose) System.out.format("ready (x%d). Starting run...\n", prestartInstances);

    // create subscribers with receivers
    final ExceptionHandler eh = mockExceptionHandler();
    final SubscriberConfig subConfig = new SubscriberConfig()
        .withExceptionHandler(eh)
        .withElectionConfig(new ElectionConfig().withScavengeInterval(1))
        .withStreamConfig(streamConfig);
    
    final AtomicLong[] receivedArray = new AtomicLong[subscribers];
    for (int i = 0; i < subscribers; i++) {
      final AtomicLong received = new AtomicLong();
      receivedArray[i] = received;
      
      final HazelcastInstance instance = instancePool.get();
      final Subscriber s = configureSubscriber(instance, subConfig);
      s.attachReceiver(record -> received.incrementAndGet(), pollTimeoutMillis);
    }
    
    final LongSupplier totalReceived = () -> {
      long total = 0;
      for (AtomicLong received : receivedArray) {
        total += received.get();
      }
      return total;
    };
    
    final LongSupplier smallestReceived = () -> {
      long smallest = Long.MAX_VALUE;
      for (AtomicLong received : receivedArray) {
        final long r = received.get();
        if (r < smallest) {
          smallest = r;
        }
      }
      return smallest;
    };
    
    // create the publishers and send across several threads
    final PublisherConfig pubConfig = new PublisherConfig()
        .withStreamConfig(streamConfig);
    final List<Publisher> publishersList = IntStream.range(0, publishers).boxed()
        .map(i -> configurePublisher(instancePool.get(), pubConfig)).collect(Collectors.toList());
    
    final AtomicLong totalSent = new AtomicLong();
    final long tookMillis = Threads.tookMillis(() -> {
      Parallel.blocking(publishers, threadNo -> {
        final Publisher p = publishersList.get(threadNo);
        
        final Random random = new Random();
        for (int i = 0; i < messagesPerPublisher; i++) {
          final byte[] bytes;
          if (randomBytes) {
            bytes = new byte[messageSize];
            random.nextBytes(bytes);
          } else {
            bytes = fixedMessage;
          }
          p.publishAsync(new Record(bytes), PublishCallback.nop());
          
          if (i != 0 && i % checkInterval == 0) {
            long lastLogTime = 0;
            final long sent = totalSent.addAndGet(checkInterval);
            for (;;) {
              final int backlog = (int) (sent - smallestReceived.getAsLong());
              if (backlog >= backlogTarget) {
                Threads.sleep(1);
                if (options.printBacklog && System.currentTimeMillis() - lastLogTime > 5_000) {
                  zlg.i("throttling... backlog @ %,d (%,d messages)", z -> z.arg(backlog).arg(sent));
                  lastLogTime = System.currentTimeMillis();
                }
              } else {
                break;
              }
            }
          }
        }
      }).run();

      wait.until(() -> {
        assertEquals(publishers * messagesPerPublisher * (long) subscribers, totalReceived.getAsLong());
      });
    });
                                     
    final long totalMessages = (long) publishers * messagesPerPublisher * subscribers;
    final double rate = (double) totalMessages / tookMillis * 1000;
    final long bps = (long) (rate * messageSize * 8 * 2);
    
    if (options.verbose) {
      System.out.format("%,d msgs took %,d ms, %,.0f msg/s, %s\n", totalMessages, tookMillis, rate, Bandwidth.translate(bps));
    }
    verifyNoError(eh);
    
    afterBase();
    beforeBase();
  }
  
  public static void main(String[] args) {
    Testmark.enable().withOptions(Scale.by(8));
    JUnitCore.runClasses(PubSubOneWayTest.class);
  }
}
