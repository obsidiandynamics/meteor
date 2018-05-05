package com.obsidiandynamics.meteor;

import com.hazelcast.core.*;
import com.obsidiandynamics.meteor.Receiver.*;
import com.obsidiandynamics.worker.*;

public interface Subscriber extends Terminable {
  RecordBatch poll(long timeoutMillis) throws InterruptedException;
  
  SubscriberConfig getConfig();
  
  void confirm(long offset);
  
  void confirm();
  
  void seek(long offset);
  
  boolean isAssigned();
  
  void deactivate();
  
  void reactivate();
  
  Receiver attachReceiver(RecordHandler recordHandler, int pollTimeoutMillis);
  
  static Subscriber createDefault(HazelcastInstance instance, SubscriberConfig config) {
    return new DefaultSubscriber(instance, config);
  }
}
