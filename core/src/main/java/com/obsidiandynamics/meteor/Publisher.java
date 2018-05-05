package com.obsidiandynamics.meteor;

import com.hazelcast.core.*;
import com.obsidiandynamics.worker.*;

public interface Publisher extends Terminable {
  PublisherConfig getConfig();

  long publishDirect(Record record);
  
  void publishAsync(Record record, PublishCallback callback);
  
  default FuturePublishCallback publishAsync(Record record) {
    final FuturePublishCallback futureCallback = new FuturePublishCallback();
    publishAsync(record, futureCallback);
    return futureCallback;
  }
  
  static Publisher createDefault(HazelcastInstance instance, PublisherConfig config) {
    return new DefaultPublisher(instance, config);
  }
}
