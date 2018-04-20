package com.obsidiandynamics.hazelq;

import static java.util.concurrent.TimeUnit.*;
import static org.junit.Assert.*;

import java.util.concurrent.atomic.*;

import org.HdrHistogram.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

import com.obsidiandynamics.func.*;
import com.obsidiandynamics.testmark.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class PubSubRoundTripTest extends AbstractPubSubTest {
  private final int SCALE = Testmark.getOptions(Scale.class, Scale.unity()).magnitude();
  
  @Test
  public void testRoundTripAsync() {
    testRoundTrip(100 * SCALE, false, new InstancePool(2, this::newInstance), new RoundTripOptions());
  }
  
  @Test
  public void testRoundTripDirect() {
    testRoundTrip(100 * SCALE, true, new InstancePool(2, this::newInstance), new RoundTripOptions());
  }
  
  @Test
  public void testRoundTripAsyncBenchmark() {
    Testmark.ifEnabled("round trip async over grid", () -> {
      final RoundTripOptions options = new RoundTripOptions() {{
        verbose = true;
      }};
      testRoundTrip(100_000 * SCALE, false, new InstancePool(2, this::newGridInstance), options);
    });
  }
  
  @Test
  public void testRoundTripDirectBenchmark() {
    Testmark.ifEnabled("round trip direct over grid", () -> {
      final RoundTripOptions options = new RoundTripOptions() {{
        verbose = true;
      }};
      testRoundTrip(100_000 * SCALE, true, new InstancePool(2, this::newGridInstance), options);
    });
  }
  
  private static class RoundTripOptions {
    boolean verbose;
  }
  
  @FunctionalInterface
  private interface PublishStrategy {
    void go(Publisher publisher, Record record);
  }
  
  private final void testRoundTrip(int numMessages, boolean direct, InstancePool instancePool, RoundTripOptions options) {
    final String streamRequest = "request";
    final String streamReply = "reply";
    final int capacity = numMessages;
    final int pollTimeoutMillis = 100;
    final int backlogTarget = 0;
    final PublishStrategy publishMechanic = (publisher, record) -> {
      if (direct) publisher.publishDirect(record);
      else publisher.publishAsync(record, PublishCallback.nop());
    };
    
    // common configuration for the request and response streams
    final StreamConfig requestStreamConfig = new StreamConfig()
        .withName(streamRequest)
        .withHeapCapacity(capacity);
    final StreamConfig replyStreamConfig = new StreamConfig()
        .withName(streamReply)
        .withHeapCapacity(capacity);
    
    if (options.verbose) System.out.format("Prestarting instances... ");
    final int prestartInstances = Math.min(4, instancePool.size());
    instancePool.prestart(prestartInstances);
    if (options.verbose) System.out.format("ready (x%d). Starting run...\n", prestartInstances);
    
    // create publishers
    final PublisherConfig requestPubConfig = new PublisherConfig()
        .withStreamConfig(requestStreamConfig);
    final PublisherConfig replyPubConfig = new PublisherConfig()
        .withStreamConfig(replyStreamConfig);
    final DefaultPublisher requestPub = configurePublisher(instancePool.get(), requestPubConfig);
    final DefaultPublisher replyPub = configurePublisher(instancePool.get(), replyPubConfig);

    // create subscribers with receivers
    final ExceptionHandler eh = mockExceptionHandler();
    final SubscriberConfig requestSubConfig = new SubscriberConfig()
        .withExceptionHandler(eh)
        .withElectionConfig(new ElectionConfig().withScavengeInterval(1))
        .withStreamConfig(requestStreamConfig);
    final SubscriberConfig replySubConfig = new SubscriberConfig()
        .withExceptionHandler(eh)
        .withElectionConfig(new ElectionConfig().withScavengeInterval(1))
        .withStreamConfig(replyStreamConfig);
    
    createReceiver(configureSubscriber(instancePool.get(), requestSubConfig), record -> {
      publishMechanic.go(replyPub, record);
    }, pollTimeoutMillis);
    
    final AtomicInteger received = new AtomicInteger();
    final Histogram hist = new Histogram(NANOSECONDS.toNanos(10), SECONDS.toNanos(10), 5);
    createReceiver(configureSubscriber(instancePool.get(), replySubConfig), record -> {
      final SimpleLongMessage m = SimpleLongMessage.unpack(record.getData());
      final long latency = System.nanoTime() - m.value;
      hist.recordValue(latency);
      received.incrementAndGet();
    }, pollTimeoutMillis);
    
    // send the messages
    for (int i = 0; i < numMessages; i++) {
      publishMechanic.go(requestPub, new Record(new SimpleLongMessage(System.nanoTime()).pack()));
      while (i - received.get() >= backlogTarget) {
        Thread.yield();
      }
    }
    
    wait.until(() -> assertEquals(numMessages, received.get()));
    
    if (options.verbose) {
      final long min = hist.getMinValue();
      final double mean = hist.getMean();
      final long p50 = hist.getValueAtPercentile(50.0);
      final long p95 = hist.getValueAtPercentile(95.0);
      final long p99 = hist.getValueAtPercentile(99.0);
      final long max = hist.getMaxValue();
      System.out.format("min: %,d, mean: %,.0f, 50%%: %,d, 95%%: %,d, 99%%: %,d, max: %,d (ns)\n", 
                        min, mean, p50, p95, p99, max);
    }
    verifyNoError(eh);
    
    afterBase();
    beforeBase();
  }
  
  public static void main(String[] args) {
    Testmark.enable();
    JUnitCore.runClasses(PubSubRoundTripTest.class);
  }
}
