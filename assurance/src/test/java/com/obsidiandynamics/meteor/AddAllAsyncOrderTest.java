package com.obsidiandynamics.meteor;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import org.junit.*;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.hazelcast.ringbuffer.*;
import com.hazelcast.test.*;

public final class AddAllAsyncOrderTest {
  @Test
  public void test() throws InterruptedException, ExecutionException {
    assumeTrue(false);
    
    final int numInstances = 4;        // how many instances to utilise
    final int iterations = 10;         // number of test cycles
    final int itemsPerIteration = 100; // how many items will be added to the buffer
    
    for (int iteration = 0; iteration < iterations; iteration++) {
      System.out.println("iteration=" + iteration);
      // configure and prestart a bunch of instances
      final List<HazelcastInstance> instances = new ArrayList<>(numInstances);
      final Config config = new Config()
          .setProperty("hazelcast.logging.type", "none")
          .addRingBufferConfig(new RingbufferConfig().setName("default").setBackupCount(3).setCapacity(itemsPerIteration));
      final TestHazelcastInstanceFactory factory = new TestHazelcastInstanceFactory();
      IntStream.range(0, numInstances).parallel().forEach(i -> instances.add(factory.newHazelcastInstance(config)));
      
      // get a ringbuffer from one of the instances
      final HazelcastInstance instance = instances.get(iteration % numInstances);
      final Ringbuffer<Integer> ring = instance.getRingbuffer("buffer-" + iteration);
      
      // send all the items as a batch and await for sequence allocation
      final List<Integer> items = IntStream.range(0, itemsPerIteration).boxed().collect(Collectors.toList());
      final ICompletableFuture<Long> writeFuture = ring.addAllAsync(items, OverflowPolicy.FAIL);
      final long lastSequence = writeFuture.get();
      assertEquals(itemsPerIteration - 1, lastSequence);
      
      // assert correct order by reading from the buffer
      final ICompletableFuture<ReadResultSet<Integer>> readFuture = ring.readManyAsync(0, itemsPerIteration, 1000, null);
      final ReadResultSet<Integer> readResultSet = readFuture.get();
      assertEquals(itemsPerIteration, readResultSet.size());
      for (int itemIndex = 0; itemIndex < itemsPerIteration; itemIndex++) {
        final int readItem = readResultSet.get(itemIndex);
        assertEquals(itemIndex, readItem);
      }
      
      // clean up
      instances.forEach(inst -> inst.getLifecycleService().terminate());
    }
  }
}
