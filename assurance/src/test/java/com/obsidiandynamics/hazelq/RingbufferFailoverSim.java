package com.obsidiandynamics.hazelq;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.hazelcast.ringbuffer.*;
import com.obsidiandynamics.worker.*;
import com.obsidiandynamics.zerolog.*;

/**
 *  Uses a {@link NopRingbufferStore} to simulate a ringbuffer failover with loss of data.
 *  Specifically, data is migrated from one member to another, but because 
 *  {@link RingbufferStore#getLargestSequence()} returns {@code -1}, the read from the ringbuffer
 *  fails with a {@link IllegalArgumentException}. (The data is technically there, but the head and
 *  tail sequences have been lost.)<p>
 *  
 *  Depending on how the ringbuffer gets mapped to a partition owner, this simulation may take a
 *  few cycles before data loss is observed.
 */
public class RingbufferFailoverSim {
  private static final Zlg zlg = Zlg.forDeclaringClass().get();
  
  private static final String BUFFER_NAME = "buffer0";
  
  private final int messages;
  
  private RingbufferFailoverSim(int messages) {
    this.messages = messages;
  }
  
  private class TestPublisher implements Joinable {
    private final int pubIntervalMillis;
    private final byte[] bytes;
    private final Ringbuffer<byte[]> buffer;
    private final WorkerThread thread;
    private int published;
    
    TestPublisher(Supplier<HazelcastInstance> instanceMaker, int pubIntervalMillis, int bytes) {
      this.pubIntervalMillis = pubIntervalMillis;
      this.bytes = new byte[bytes];
      final HazelcastInstance instance = instanceMaker.get();
      buffer = instance.getRingbuffer(BUFFER_NAME);
      zlg.i("serviceName=%s, partitionKey=%s", z -> z.arg(buffer::getServiceName).arg(buffer::getPartitionKey));
      final Partition partition = instance.getPartitionService().getPartition(BUFFER_NAME);
      zlg.i("partitionId=%s, owner=%s", z -> z.arg(partition::getPartitionId).arg(partition::getOwner));
      instance.getPartitionService().addMigrationListener(new MigrationListener() {
        @Override
        public void migrationStarted(MigrationEvent migrationEvent) {
          zlg.i("Migration started %s", z -> z.arg(migrationEvent));
        }

        @Override
        public void migrationCompleted(MigrationEvent migrationEvent) {
          zlg.i("Migration compeleted %s", z -> z.arg(migrationEvent));
        }

        @Override
        public void migrationFailed(MigrationEvent migrationEvent) {
          zlg.i("Migration failed %s", z -> z.arg(migrationEvent));
        }
      });
      
      thread = WorkerThread.builder()
      .withOptions(new WorkerOptions().daemon().withName(TestPublisher.class))
      .onCycle(this::publishCycle)
      .buildAndStart();
    }
    
    private void publishCycle(WorkerThread t) throws InterruptedException {
      buffer.addAsync(bytes, OverflowPolicy.OVERWRITE);
      published++;
      zlg.i("Published %s", z -> z.arg(published));
      
      if (published == messages) {
        zlg.i("Publisher: terminating");
        t.terminate();
      } else {
        Thread.sleep(pubIntervalMillis);
      }
    }

    @Override
    public boolean join(long timeoutMillis) throws InterruptedException {
      return thread.join(timeoutMillis);
    }
  }
  
  private class TestSubscriber implements Joinable {
    private final int pollTimeoutMillis;
    private final Ringbuffer<byte[]> buffer;
    private final boolean terminateOnComplete;
    private final WorkerThread thread;
    private int received;
    private long nextSequence;
    
    TestSubscriber(Supplier<HazelcastInstance> instanceMaker, int pollTimeoutMillis, boolean terminateOnComplete) {
      this.pollTimeoutMillis = pollTimeoutMillis;
      this.terminateOnComplete = terminateOnComplete;
      buffer = instanceMaker.get().getRingbuffer(BUFFER_NAME);
      
      thread = WorkerThread.builder()
      .withOptions(new WorkerOptions().daemon().withName(TestSubscriber.class))
      .onCycle(this::receiveCycle)
      .buildAndStart();
    }
    
    private void receiveCycle(WorkerThread t) throws InterruptedException {
      final ICompletableFuture<ReadResultSet<byte[]>> f = buffer.readManyAsync(nextSequence, 1, 1000, bytes -> bytes != null);
      try {
        final ReadResultSet<byte[]> results = f.get(pollTimeoutMillis, TimeUnit.MILLISECONDS);
        nextSequence = results.getSequence(results.size() - 1) + 1;
        received += results.size();
        zlg.i("Received %,d records (total %,d) next is %,d", z -> z.arg(results::size).arg(received).arg(nextSequence));
      } catch (ExecutionException e) {
        e.printStackTrace();
      } catch (TimeoutException e) {
        zlg.w("Timed out");
      }
      
      if (received % messages == 0 && received > 0) {
        zlg.i("Subscriber: received one complete set");
        if (terminateOnComplete) {
          t.terminate();
        }
      }
    }

    @Override
    public boolean join(long timeoutMillis) throws InterruptedException {
      return thread.join(timeoutMillis);
    }
  }
  
  public static void main(String[] args) {
    final int messages = 10;
    final int pubIntervalMillis = 10;
    final int bytes = 10;
    final int pollTimeoutMillis = 100;
    final int cycles = 100;
    
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
                             .setName(BUFFER_NAME)
                             .setBackupCount(1)
                             .setAsyncBackupCount(0)
                             .setRingbufferStoreConfig(new RingbufferStoreConfig()
                                                       .setEnabled(true)
                                                       .setFactoryClassName(NopRingbufferStore.Factory.class.getName())));

    final Supplier<HazelcastInstance> instanceSupplier = () -> GridProvider.getInstance().createInstance(config);

    zlg.i("Creating publisher instance...");
    final AtomicReference<HazelcastInstance> instance = new AtomicReference<>(instanceSupplier.get());
    instance.get().getRingbuffer(BUFFER_NAME);
    
    final InstancePool instancePool = new InstancePool(2, instanceSupplier);
    zlg.i("Prestarting subscriber instances...");
    instancePool.prestartAll();
    zlg.i("Instances prestarted");
    
    new RingbufferFailoverSim(messages) {{
      new TestSubscriber(instancePool::get, pollTimeoutMillis, false);
      new TestSubscriber(instancePool::get, pollTimeoutMillis, false);
      
      for (int i = 0; i < cycles; i++) {
        if (instance.get() == null) {
          zlg.i("Creating publisher instance...");
          instance.set(instanceSupplier.get());
        }
        
        zlg.i("Publisher instance created");
        final TestPublisher pub = new TestPublisher(instance::get, pubIntervalMillis, bytes);
        final TestSubscriber sub = new TestSubscriber(instance::get, pollTimeoutMillis, true);
        Joiner.of(pub, sub).joinSilently();
        instance.getAndSet(null).shutdown();
      }
    }};
  }
}
