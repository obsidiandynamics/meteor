package com.obsidiandynamics.hazelq;

import java.util.*;

import com.hazelcast.config.*;
import com.hazelcast.core.*;

public final class HazelcastSample {
  public static void main(String[] args) {
    final Config config = new Config()
        .setProperty("hazelcast.logging.type", "slf4j")
        .setProperty("hazelcast.max.no.heartbeat.seconds", String.valueOf(10));
    
    final HazelcastProvider provider = GridProvider.getInstance();
    final HazelcastInstance h0 = provider.createInstance(config);
    final HazelcastInstance h1 = provider.createInstance(config);
    useInstance(h0);
    useInstance(h1);
    h0.shutdown();
    h1.shutdown();
  }
  
  private static void useInstance(HazelcastInstance instance) {
    final Map<Integer, String> mapCustomers = instance.getMap("customers");
    mapCustomers.put(1, "Alpha");
    mapCustomers.put(2, "Bravo");
    mapCustomers.put(3, "Charlie");

    System.out.println("Customer with key 1: " + mapCustomers.get(1));
    System.out.println("Map size: " + mapCustomers.size());

    final Queue<String> queueCustomers = instance.getQueue("customers");
    queueCustomers.offer("Tom");
    queueCustomers.offer("Mary");
    queueCustomers.offer("Jane");
    System.out.println("First customer: " + queueCustomers.poll());
    System.out.println("Second customer: " + queueCustomers.peek());
    System.out.println("Queue size: " + queueCustomers.size());
  }
}
