package com.obsidiandynamics.hazelq;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import org.junit.*;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.hazelcast.ringbuffer.*;
import com.hazelcast.test.*;

public final class AddAsyncOrderTest {
  @Test
  public void test() throws InterruptedException, ExecutionException {
    assumeTrue(false);
    
    final int numInstances = 4;        // only fails with multiple instances
    final int iterations = 10;         // sometimes takes a few iterations to fail
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
      
      // send all the items and await all callbacks with a countdown latch
      final CountDownLatch latch = new CountDownLatch(itemsPerIteration);
      final AtomicReference<AssertionError> error = new AtomicReference<>();
      final int _iteration = iteration;
      for (int item = 0; item < itemsPerIteration; item++) {
        // send each item one by one and compare its queued number with the allocated sequence number
        final ICompletableFuture<Long> writeFuture = ring.addAsync(item, OverflowPolicy.FAIL);
        final int _item = item;
        writeFuture.andThen(new ExecutionCallback<Long>() {
          @Override 
          public void onResponse(Long sequence) {
            // the callback may be called out of order, which is perfectly fine; but the sequence numbers
            // must match the order in which the items were enqueued
            try {
              assertEquals(_item, (long) sequence);
            } catch (AssertionError e) {
              // if we detect a problem, save the AssertionError as the unit test is running in a different thread
              System.err.println("SEQUENCE OUT OF ORDER: item=" + _item + ", sequence=" + sequence + ", iteration=" + _iteration);
              error.set(e);
            } finally {
              latch.countDown();
            }
          }
  
          @Override 
          public void onFailure(Throwable t) {
            t.printStackTrace();
          }
        });
      }
      
      // wait for all callbacks
      latch.await();
      
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
      
      // check if any assertion errors were observed during the run
      if (error.get() != null) {
        throw error.get();
      }
    }
  }
}
