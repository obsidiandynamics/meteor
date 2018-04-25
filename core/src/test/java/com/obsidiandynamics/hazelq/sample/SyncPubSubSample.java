package com.obsidiandynamics.hazelq.sample;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.obsidiandynamics.hazelq.*;
import com.obsidiandynamics.zerolog.*;

public final class SyncPubSubSample {
  public static void main(String[] args) throws InterruptedException {
    // set up a Zerolog logger
    final Zlg zlg = Zlg.forDeclaringClass().get();

    // configure Hazelcast
    final Config config = new Config().setProperty("hazelcast.logging.type", "slf4j");
    final HazelcastInstance instance = GridProvider.getInstance().createInstance(config);

    // the stream config is shared between all publishers and subscribers
    final StreamConfig streamConfig = new StreamConfig().withName("TestStream");

    // create a publisher and send a message
    final Publisher publisher = Publisher.createDefault(instance,
                                                        new PublisherConfig()
                                                        .withStreamConfig(streamConfig));

    publisher.publishAsync(new Record("Hello world".getBytes()));

    // create a subscriber for a test group and poll for records
    final Subscriber subscriber = Subscriber.createDefault(instance, 
                                                           new SubscriberConfig()
                                                           .withStreamConfig(streamConfig)
                                                           .withGroup("TestGroup"));
    for (;;) {
      final RecordBatch records = subscriber.poll(100);
      zlg.i("Got %d record(s)", z -> z.arg(records::size));
    }
  }
}
