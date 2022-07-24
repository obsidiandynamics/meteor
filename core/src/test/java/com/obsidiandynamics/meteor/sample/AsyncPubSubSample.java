package com.obsidiandynamics.meteor.sample;

import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.obsidiandynamics.meteor.*;
import com.obsidiandynamics.meteor.Record;
import com.obsidiandynamics.threads.*;
import com.obsidiandynamics.zerolog.*;

public final class AsyncPubSubSample {
  public static void main(String[] args) {
    // set up a Zerolog logger and bridge from Hazelcast's internal logger
    final Zlg zlg = Zlg.forDeclaringClass().get();
    HazelcastZlgBridge.install();

    // configure Hazelcast
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
    
    // receive records asynchronously; polls every 100 ms
    subscriber.attachReceiver(record -> {
      zlg.i("Got %s", z -> z.arg(new String(record.getData())));
      subscriber.confirm();
    }, 100);
    
    // give it some time...
    Threads.sleep(5_000);
    
    // clean up
    publisher.terminate().joinSilently();
    subscriber.terminate().joinSilently();
    instance.shutdown();
  }
}
