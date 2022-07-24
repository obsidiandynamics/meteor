package com.obsidiandynamics.meteor;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.hazelcast.ringbuffer.*;
import com.obsidiandynamics.worker.*;
import com.obsidiandynamics.zerolog.*;

public class RingbufferBandwidthSim {
  private static final Zlg zlg = Zlg.forDeclaringClass().get();
  
  private final int messages;
  
  private RingbufferBandwidthSim(int messages) {
    this.messages = messages;
  }
  
  private class TestPublisher {
    private final int pubIntervalMillis;
    private final byte[] bytes;
    private final Ringbuffer<byte[]> buffer;
    private int published;
    
    TestPublisher(Supplier<HazelcastInstance> instanceMaker, int pubIntervalMillis, int bytes) {
      this.pubIntervalMillis = pubIntervalMillis;
      this.bytes = new byte[bytes];
      buffer = instanceMaker.get().getRingbuffer("buffer");
      
      WorkerThread.builder()
      .withOptions(new WorkerOptions().daemon().withName(TestPublisher.class))
      .onCycle(this::publishCycle)
      .buildAndStart();
    }
    
    private void publishCycle(WorkerThread t) throws InterruptedException {
      buffer.addAsync(bytes, OverflowPolicy.OVERWRITE);
      published++;
      zlg.i("Published %,d", z -> z.arg(published));
      
      if (published == messages) {
        zlg.i("Publisher: terminating");
        t.terminate();
      } else {
        Thread.sleep(pubIntervalMillis);
      }
    }
  }
  
  private class TestSubscriber {
    private final int pollTimeoutMillis;
    private final Ringbuffer<byte[]> buffer;
    private int received;
    private long nextSequence;
    
    TestSubscriber(Supplier<HazelcastInstance> instanceMaker, int pollTimeoutMillis) {
      this.pollTimeoutMillis = pollTimeoutMillis;
      buffer = instanceMaker.get().getRingbuffer("buffer");
      
      WorkerThread.builder()
      .withOptions(new WorkerOptions().daemon().withName(TestSubscriber.class))
      .onCycle(this::receiveCycle)
      .buildAndStart();
    }
    
    private void receiveCycle(WorkerThread t) throws InterruptedException {
      final ICompletableFuture<ReadResultSet<byte[]>> f = buffer.readManyAsync(nextSequence, 1, 1000, Objects::nonNull);
      try {
        final ReadResultSet<byte[]> results = f.get(pollTimeoutMillis, TimeUnit.MILLISECONDS);
        nextSequence = results.getSequence(results.size() - 1) + 1;
        received += results.size();
        zlg.i("Received %,d records (total %,d)", z -> z.arg(results::size).arg(received));
      } catch (ExecutionException e) {
        e.printStackTrace();
      } catch (TimeoutException e) {
        zlg.w("Timed out");
      }
      
      if (received == messages) {
        zlg.i("Subscriber: terminating");
        t.terminate();
      }
    }
  }
  
  public static void main(String[] args) {
    final int messages = 1_000;
    final int pubIntervalMillis = 100;
    final int bytes = 10;
    final int pollTimeoutMillis = 1_000;
    
    final Config config = new Config()
        .setProperty("hazelcast.logging.type", "none")
        .setProperty("hazelcast.shutdownhook.enabled", "false")
        .setProperty("hazelcast.graceful.shutdown.max.wait", String.valueOf(5))
        .setProperty("hazelcast.wait.seconds.before.join", String.valueOf(0))
        .setProperty("hazelcast.max.wait.seconds.before.join", String.valueOf(0))
        .setNetworkConfig(new NetworkConfig()
                          .setJoin(new JoinConfig()
                                   .setMulticastConfig(new MulticastConfig()
                                                       .setEnabled(true)
                                                       .setMulticastTimeoutSeconds(1))
                                   .setTcpIpConfig(new TcpIpConfig()
                                                   .setEnabled(false))))
        .addRingBufferConfig(new RingbufferConfig()
                             .setName("default")
                             .setBackupCount(0)
                             .setAsyncBackupCount(0));

    final InstancePool instancePool = new InstancePool(3, () -> GridProvider.getInstance().createInstance(config));
    zlg.i("Prestarting instances...");
    instancePool.prestartAll();
    zlg.i("Instances prestarted");
    
    new RingbufferBandwidthSim(messages) {{
      new TestPublisher(instancePool::get, pubIntervalMillis, bytes);
      new TestSubscriber(instancePool::get, pollTimeoutMillis);
      new TestSubscriber(instancePool::get, pollTimeoutMillis);
    }};
  }
}
