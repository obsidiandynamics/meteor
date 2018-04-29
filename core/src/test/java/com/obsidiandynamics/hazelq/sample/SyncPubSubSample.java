package com.obsidiandynamics.hazelq.sample;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.obsidiandynamics.hazelq.*;
import com.obsidiandynamics.hazelq.log.*;
import com.obsidiandynamics.zerolog.*;

public final class SyncPubSubSample {
  public static void main(String[] args) throws InterruptedException {
    // set up a Zerolog logger
    final Zlg zlg = Zlg.forDeclaringClass().get();

    // configure Hazelcast
    System.setProperty("hazelcast.logging.class", ZlgFactory.class.getName());
    final HazelcastProvider provider = GridProvider.getInstance();
    final HazelcastInstance instance = provider.createInstance(new Config());

    // the stream config is shared between all publishers and subscribers
    final StreamConfig streamConfig = new StreamConfig().withName("test-stream");

    // create a publisher and send a message
    final Publisher publisher = Publisher.createDefault(instance,
                                                        new PublisherConfig()
                                                        .withStreamConfig(streamConfig));

    publisher.publishAsync(new Record("Hello world".getBytes()));

    // create a subscriber for a test group and poll for records
    final Subscriber subscriber = Subscriber.createDefault(instance, 
                                                           new SubscriberConfig()
                                                           .withStreamConfig(streamConfig)
                                                           .withGroup("test-group"));
    // 10 polls, at 100 ms each
    for (int i = 0; i < 10; i++) {
      zlg.i("Polling...");
      final RecordBatch records = subscriber.poll(100);
      
      if (! records.isEmpty()) {
        zlg.i("Got %d record(s)", z -> z.arg(records::size));
        records.forEach(r -> zlg.i(new String(r.getData())));
        subscriber.confirm();
      }
    }
    
    // clean up
    publisher.terminate().joinSilently();
    subscriber.terminate().joinSilently();
    instance.shutdown();
  }
}
