package com.obsidiandynamics.hazelq;

import java.util.*;
import java.util.Map.*;
import java.util.function.*;
import java.util.stream.*;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.obsidiandynamics.worker.*;
import com.obsidiandynamics.zerolog.*;

public class MapBandwidthSim {
  private static final Zlg zlg = Zlg.forDeclaringClass().get();
  
  private MapBandwidthSim() {}
  
  private class TestWriter {
    private final int writeIntervalMillis;
    private final byte[] bytes;
    private final IMap<Integer, byte[]> map;
    private final int writes;
    private final int keys;
    private int written;
    
    TestWriter(Supplier<HazelcastInstance> instanceMaker, int writeIntervalMillis, int writes, int keys, int bytes) {
      this.writeIntervalMillis = writeIntervalMillis;
      this.writes = writes;
      this.keys = keys;
      this.bytes = new byte[bytes];
      map = instanceMaker.get().getMap("map");
      IntStream.range(0, keys).forEach(i -> map.put(i, this.bytes));
      
      WorkerThread.builder()
      .withOptions(new WorkerOptions().daemon().withName(TestWriter.class))
      .onCycle(this::writeCycle)
      .buildAndStart();
    }
    
    private void writeCycle(WorkerThread t) throws InterruptedException {
      final Integer key = written % keys;
      map.replace(key, bytes, bytes);
      written++;
      zlg.i("Written %,d", z -> z.arg(written));
      
      if (written == writes) {
        zlg.i("Writer: terminating");
        t.terminate();
      } else {
        Thread.sleep(writeIntervalMillis);
      }
    }
  }
  
  private class TestReader {
    private final int readIntervalMillis;
    private final IMap<Integer, byte[]> map;
    
    TestReader(Supplier<HazelcastInstance> instanceMaker, int readIntervalMillis) {
      this.readIntervalMillis = readIntervalMillis;
      map = instanceMaker.get().getMap("map");
      
      WorkerThread.builder()
      .withOptions(new WorkerOptions().daemon().withName(TestReader.class))
      .onCycle(this::readCycle)
      .buildAndStart();
    }
    
    private void readCycle(WorkerThread t) throws InterruptedException {
      final Set<Entry<Integer, byte[]>> entrySet = map.entrySet();
      zlg.i("Read %,d entries", z -> z.arg(entrySet::size));
      Thread.sleep(readIntervalMillis);
    }
  }
  
  public static void main(String[] args) {
    final int writeIntervalMillis = 100;
    final int writes = 1_000;
    final int keys = 3;
    final int bytes = 10;
    final int readIntervalMillis = 100;
    
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
        .addMapConfig(new MapConfig()
                      .setName("default")
                      .setBackupCount(1)
                      .setAsyncBackupCount(0));

    final InstancePool instancePool = new InstancePool(4, () -> GridHazelcastProvider.getInstance().createInstance(config));
    zlg.i("Prestarting instances...");
    instancePool.prestartAll();
    zlg.i("Instances prestarted");
    
    new MapBandwidthSim() {{
      new TestWriter(instancePool::get, writeIntervalMillis, writes, keys, bytes);
      new TestReader(instancePool::get, readIntervalMillis);
      new TestReader(instancePool::get, readIntervalMillis);
      new TestReader(instancePool::get, readIntervalMillis);
    }};
  }
}
