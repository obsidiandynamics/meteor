<img src="https://raw.githubusercontent.com/wiki/obsidiandynamics/meteor/images/meteor-logo.png" width="90px" alt="logo"/> Meteor
===
Real-time message streaming over a Hazelcast in-memory data grid.

[![Download](https://api.bintray.com/packages/obsidiandynamics/meteor/meteor-core/images/download.svg) ](https://bintray.com/obsidiandynamics/meteor/meteor-core/_latestVersion)
[![Build](https://travis-ci.org/obsidiandynamics/meteor.svg?branch=master) ](https://travis-ci.org/obsidiandynamics/meteor#)
[![codecov](https://codecov.io/gh/obsidiandynamics/meteor/branch/master/graph/badge.svg)](https://codecov.io/gh/obsidiandynamics/meteor)

# What is Meteor
**TL;DR** — Meteor is a broker-less, lightweight embeddable alternative to Kafka/Kinesis that runs in an in-memory data grid.

## History
Meteor started out as a part of Blackstrom — a research project into ultra-fast transactional mesh fabric technology for distributed micro-service and event-driven architectures. Blackstrom originally relied on Kafka, but we longed for a more lightweight distributed ledger for testing, simulation and small-scale deployments. It had to have a **zero deployment footprint** (no brokers or other middleware), be reasonably performant, reliable and highly available. We wanted Kafka, but without the brokers.

Meteor has shown lots of potential early in its journey, surpassing all expectations in terms of performance and scalability. A benchmark conducted on an [i7-4770 Haswell](https://ark.intel.com/products/75122/Intel-Core-i7-4770-Processor-8M-Cache-up-to-3_90-GHz) CPU with 4 publishers and 16 subscribers has shown aggregate throughput in excess of 4.3 million messages/sec over a single stream, with 100-byte messages. This is about 30% faster than an equivalent scenario running on Kafka. Bear in mind that Meteor is largely unoptimised, being in its infancy, whereas Kafka had benefited from years of performance tuning.

That said, Meteor wasn't designed to be a 'better Kafka', but rather an alternative streaming platform that has its own merits and may be a better fit in certain contexts. Eventually Meteor got broken off into a separate project, and so here we are.

## Fundamentals
### Real-time message streaming
The purpose of a real-time message streaming platform is to enable very high-volume, low-latency distribution of message-oriented content among distributed processes that are completely decoupled from each other. These process fall into one of two categories: publishers (emitters of message content) and subscribers (consumers of messages).

Message streaming platforms reside in the broader class of Message-oriented Middleware (MoM) and are similar to traditional message queues and topics, but generally offer stronger temporal guarantees and typically order-of-magnitude performance gains due to log-structured immutability. Whereas messages in an MQ tend to be arbitrarily ordered and generally independent of one another, messages in a stream tend to be strongly ordered, often chronologically or causally. Also, a stream persists its messages, whereas an MQ will discard a message once it's been read. For this reason, message streaming tends to be a better fit for implementing Event-Driven Architectures, encompassing event sourcing, eventual consistency and CQRS concepts.

Message streaming platforms are a comparatively recent paradigm within the broader MoM domain. There are only a couple of mainstream implementations available (excluding this one), compared to hundreds of MQ-style brokers, some going back to the 1980s (e.g. Tuxedo). Compared to established standards such as AMQP, MQTT, XMPP and JMS, there are no equivalent standards in the streaming space. Streaming platforms are an active area of continuous research and experimentation. In spite of this, streaming platforms aren't just a niche concept or an academic idea with a few esoteric use cases; they can be applied effectively to a broad range of messaging scenarios, routinely displacing their traditional analogues.

### Streams, records and offsets
A stream is a **totally ordered** sequence of records, and is fundamental to Meteor. A record has an ID (64-bit integer offset) and a payload, which is an array of bytes. 'Totally ordered' means that, for any given publisher, records will be written in the order they were emitted. If record _P_ was published before _Q_, then _P_ will precede _Q_ in the stream. Furthermore, they will be read in the same order by all subscribers; _P_ will always be read before _Q_. (Depending on the context, we may sometimes use the term _message_ to refer to a record, as messaging is the dominant use case for Meteor.)

There is no recognised ordering _across_ publishers; if two (or more) publishers emit records simultaneously, those records may materialise in arbitrary order. However, this ordering will be observed uniformly across all subscribers.

The record offset uniquely identifies a record in the stream, and is used for O(1) lookups. The offset is a strictly monotonically increasing integer in a sparse address space, meaning that each successive offset is always higher than its predecessor and there may be varying gaps between successively assigned offsets. Your application shouldn't try to interpret the offset or guess what the next offset might be; it may, however, infer the relative order of any record pair based on their offsets, sort the records, and so forth.

```
     start of stream
+--------+-----------------+
|0..00000|First message    |
+--------+-----------------+
|0..00001|Second message   |
+--------+-----------------+
|0..00002|Third message    |
+--------+-----------------+
|0..00003|Fourth message   |
+--------+-----------------+
|0..00007|Fifth message    |
+--------+-----------------+
|0..00008|Sixth message    |
+--------+-----------------+
|0..00010|Seventh message  |
+--------+-----------------+
            ...
+--------+-----------------+
|0..56789|Last message     |
+--------+-----------------+
       end of stream
```

### Publishers
Publishers place records into the stream, for consumption by any number of subscribers. The pub-sub topology adheres to a broader multipoint-to-multipoint model, meaning that there may be any number of publishers and subscribers simultaneously interacting with a stream. Depending on the actual solution context, stream topologies may also be point-to-multipoint, multipoint-to-point and point-to-point.

### Subscribers
A subscriber is a stateful entity that reads a message from a stream, one at a time. **The act of reading a message does not consume it.** In fact, subscribers have absolutely no impact on the stream, and, as such, a stream is said to be immutable. This is a yet another point of distinction between a message stream and a traditional message queue (MQ).

A subscriber internally maintains an offset that points to the next message in the stream, advancing the offset for every successive read. When a subscriber first attaches to a stream, it may elect to start at the head-end or the tail-end of the stream, or seek to a particular offset.

Subscribers retain their offset state locally. Since subscribers do not interfere, there may be any number of them reading the same stream, each with their own position in the stream. Subscribers run at their own pace; a slow/backlogged subscriber has no impact on its peers.

```
+--------+
|0..00000|
+--------+
|0..00001| <= subscriber 2
+--------+
|0..00002|
+--------+
|0..00003| <= subscriber 1
+--------+
   ...
+--------+
|0..00008| <= subscriber 3
+--------+
|0..00042|
+--------+
   ...
```

### Subscriber groups
Subscribers may be optionally associated with a group, which provides exclusivity of stream reading, as well as offset tracking. These are called **grouped subscribers**. Conversely, subscribers that aren't bounded by groups are called **ungrouped subscribers**.

For a set of subscribers sharing a common group, _at most one_ subscriber is allowed to read from the stream. The assignment of a subscriber to a stream is random, biased towards first-come-first-serve. Only the assigned subscriber may read from the stream; other subscribers will hold off from reading the stream.

By routinely reading a record from a stream, the subscriber implicitly indicates to its peers that it is 'healthy', thereby extending its lease over the stream. However, should the subscriber fail to read again within the allowable deadline, it will be deemed as faulty and the stream will be reassigned to an alternate subscriber in the same group, so long as an alternate subscriber exists.

A grouped subscriber also tracks its current read offset locally. It also may (and typically will) routinely write its last read offset back to the grid, recording it in a dedicated metadata area. This is called _confirming_ an offset. A confirmed offset implies that the record at that offset **and all prior records** have been dealt with by the subscriber. A word of caution: an offset should only be confirmed when your application is done with the record in question and all records before it. By 'done with' we mean the record has been processed to the point that any actions that would have resulted from the record have been carried out. This may include calling other APIs, updating a database, persisting the record's payload, or publishing more records.

```
+--------+
|0..00000|
+--------+
|0..00001| <= confirmed offset (stable; stored as stream metadata on the grid)
+--------+
|0..00002|
+--------+
|0..00003| <= read offset (volatile; stored locally in the subscriber)
+--------+
   ...
+--------+
|0..00008|
+--------+
|0..00042|
+--------+
   ...
```

Typically a subscriber will confirm its read offset linearly, in tandem with the processing of the records. (Read a record, confirm it, read the next, confirm, and so on.) However, the subscriber is free to adopt any confirmation tactic, as long as it doesn't confirm records that haven't yet been completely processed. A subscriber may, for example, elect to process a batch of records concurrently, and only confirm the last record when the entire batch is done.

**Note:** Contrary to its grouped counterpart, if an ungrouped subscriber is closed and reopened, it will lose its offset. It is up to the application to appropriately reset those subscribers.

The relationship between publishers, streams and subscribers is depicted below.

```
+-----------+         +-----------+
|PUBLISHER 1|         |PUBLISHER 2|
+-----+-----+         +-----+-----+
      |                     |
      |                     |
      |                     |
      |                     |
      |                     |
 +----v---------------------v-----------------------------------------+
 |                            >>> STREAM >>>                          |
 | +----------------------------------------------------------------+ |
 | |record 0..00|record 0..01|record 0..02|record 0..03|record 0..04| |
 | +----------------------------------------------------------------+ |
 |                            >>> STREAM >>>                          |
 +------------+--------------------+-------------------+--------------+
              |                    |                   |
              |                    |                   X  (no feed to subscriber 3)
              |                    |
              |            +-------|----------------------------+
              |            |       |                            |
       +------v-----+      | +-----v------+      +------------+ |
       |SUBSCRIBER 1|      | |SUBSCRIBER 2|      |SUBSCRIBER 3| |
       +------------+      | +------------+      +------------+ |
                           |           SUBSCRIBER GROUP         |
                           +------------------------------------+
```

The subscriber group is a somewhat understated concept that's pivotal to the versatility of a message streaming platform. By simply varying the affinity of subscribers with their groups, one can arrive at vastly different distribution topologies — from a topic-like, pub-sub behaviour to an MQ-style, point-to-point model. And crucially, because messages are never truly consumed (the advancing offset only creates the illusion of consumption), one can superimpose disparate distribution topologies over a single message stream.

### At-least-once delivery and exactly-once processing
Meteor takes a prudent approach to data integrity — providing at-least-once message delivery guarantees. If a subscriber within a group is unable to completely process a message (for example, if it crashes midstream), the last message along with any other unconfirmed messages will be redelivered to another subscriber within the same group. This is why it's so important for a subscriber to only confirm an offset when it has completely dealt with the message, not before.

At-least-once semantics do carry a caveat: as the name suggests, under some circumstances it may be possible for your application to read the same message repeatedly, even if it had already dealt with the message. For example, you may have dealt with the message but crashed just before confirming it. When a new subscriber takes over the stream, it will replay messages from the last confirmed checkpoint. Alternatively, a message may have been confirmed and another subscriber in the group took over the stream, but due to the asynchronous nature of confirmations the new subscriber will replay from the point of the last received confirmation.

**Subscribers in message streaming applications must be idempotent.** In other words, processing the same record repeatedly should have no net effect on the subscriber ecosystem. If a record has no _additive_ effects, the subscriber is inherently idempotent. (For example, if the subscriber simply overwrites an existing database entry with a new one, then the update is idempotent.) Otherwise, the subscriber must check whether a record has already been processed, and to what extent, prior to processing a record. **The combination of at-least-once delivery and subscriber idempotence leads to exactly-once semantics.**

### Stream capacity
Meteor stores the records in a distributed ring buffer within the IMDG — each stream corresponds to one Hazelcast ring buffer. The ring buffer has a configurable, albeit a finite capacity; when the capacity is exhausted, the oldest records will be overwritten. The subscriber API doesn't expose the ring topology; you just see an ordered list of records that gets truncated from the tail end when the buffer is at capacity. The default stream capacity is 10,000 records; this can be overridden via the `StreamConfig` class.

Meteor lets you transparently access historical records beyond the buffer's capacity by providing a custom `RingbufferStore` implementation via the `StreamConfig`. Doing so has the effect of seamlessly splicing a data store with an in-memory ring buffer, emulating the feel of a single append-only log. Obviously, accessing historical records will be significantly slower than reading from the near the head of the log.

# Getting Started
## Dependencies
Gradle builds are hosted on JCenter. Add the following snippet to your build file, replacing `x.y.z` with the version shown on the Download badge at the top of this README.

```groovy
compile "com.obsidiandynamics.meteor:meteor-core:x.y.z"
compile "com.hazelcast:hazelcast:3.10"
```

**Note:** Although Meteor is compiled against Hazelcast 3.10, no specific Hazelcast client library dependency has been bundled with Meteor. This lets you to use any 3.9+ API-compatible Hazelcast client library in your application.

Meteor is packaged as two separate modules:

1. `meteor-core` — The Meteor implementation. This is the only module you need in production.
2. `meteor-assurance` — Mocking components and test utilities. Normally, this module would only be used during testing and should be declared in the `testCompile` configuration. See below.

```groovy
testCompile "com.obsidiandynamics.meteor:meteor-assurance:x.y.z"
testCompile "com.hazelcast:hazelcast:3.10:tests"
```

## A pub-sub example
The following snippet publishes a message and consumes it from a polling loop.

```java
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
```

Some things to note:

* If your application is already using Hazelcast, you should recycle the same `HazelcastInstance` for Meteor as you use elsewhere in your process. (Unless you wish to bulkhead the two for whatever reason.) Otherwise, you can create a new instance as in the above example.
* The `publishAsync()` method is non-blocking, returning a `CompletableFuture` that is assigned the record's offset. Alternatively, you can call an overloaded version, providing a `PublishCallback` to be notified when the record was actually sent, or if an error occurred. Most things in Meteor are done asynchronously.
* The `StreamConfig` objects _must_ be identical among all publishers and subscribers. The stream capacity, number of sync/async replicas, persistence configuration and so forth, must be agreed upon in advance by all parties.
* Calling `withGroup()` on the `SubscriberConfig` assigns a subscriber group, meaning that only one subscriber will be allowed to pull messages off the stream (others will hold back). We refer to this type as a **grouped subscriber**.
* A grouped subscriber can (and should) persist its offset on the grid by calling `confirm()`, allowing other subscribers to take over from the point of failure. This has the effect of advancing the group's read offset to the last offset successfully read by the sole active subscriber. Alternatively, a subscriber can call `confirm(long)`, passing in a specific offset.  
* Polling with `Subscriber.poll()` returns a (possibly empty) batch of records. The timeout is in milliseconds (consistently throughout Meteor) and sets the upper bound on the blocking time. If there are accumulated records in the buffer prior to calling `poll()`, then the latter will return without further blocking.
* Clean up the publisher and subscriber instances by calling `terminate().joinSilently()`. This both instructs it to stop and subsequently awaits its termination.

## Logging
We use [Zerolog](https://github.com/obsidiandynamics/zerolog) (Zlg) within Meteor and also in our examples for low-overhead logging. While it's completely optional, Zlg works well with SLF4J and is optimised for performance-intensive applications. To install the Zlg bridge, either invoke `HazelcastZlgBridge.install()` at the start of your application (before you obtain a Hazelcast instance), or set the system property `hazelcast.logging.class` to `com.obsidiandynamics.zerolog.ZlgFactory`.

For SLF4J users, an added advantage of bridging to Zlg is that it preserves call site (class/method/line) location information for all logs that come out of Hazelcast. The SLF4J binding that's bundled Hazelcast is rather simplistic, forwarding minimal information to SLF4J. The Zlg-based binding is a comprehensive implementation, supporting SLFJ4's location-aware logging API.

To bind Zlg to SLF4J, add the following to your `build.gradle` in the `dependencies` section, along with your other SL4J and concrete logger dependencies. 

```groovy
runtime "com.obsidiandynamics.zerolog:zerolog-slf4j17:0.14.0"
```

Replace the version in the snippet with the version on the Zlg download badge below.

[![Download](https://api.bintray.com/packages/obsidiandynamics/zerolog/zerolog-core/images/download.svg) ](https://bintray.com/obsidiandynamics/zerolog/zerolog-core/_latestVersion)

## Working with byte arrays
We want to keep to a light feature set until the project matures to a production-grade system. For the time being, there is no concept of object (de)serialization built into Meteor; instead records deal directly with byte arrays. While it means you have to push your own bytes around, it gives you the most flexibility with the choice of serialization frameworks. In practice, all serializers convert between objects and either character or byte streams, so plugging in a serializer is a trivial matter.

**Note:** Future iterations may include a mechanism for serializing objects and, crucially, _pipelining_ — where (de)serialization is performed in a different thread to the rest of the application, thereby capitalising on multi-core CPUs.

## Switching providers
`HazelcastProvider` is an abstract factory for obtaining `HazelcastInstance` objects with varying behaviour. Its use is completely optional — if you are already accustomed to using a `HazelcastInstanceFactory`, you may continue to do so. The real advantage is that it allows for dependency inversion — decoupling your application from the physical grid. Presently, there are two implementations:

* `GridProvider` is a factory for `HazelcastInstance` instances that connect to a real grid. The instances produced are the same as those made by `HazelcastInstanceFactory`.
* `TestProvider` is a factory for connecting to a virtual 'test' grid; one which is simulated internally and doesn't leave the confines of the JVM. `TestProvider` requires the `meteor-assurance` module on the classpath.

## Initial offset scheme
When a subscriber attaches to a stream for the first time, it needs an initial offset to start consuming from. This is configured by passing an `InitialOffsetScheme` enum to the `SubscriberConfig`, as shown in the snippet below.

```java
new SubscriberConfig().withInitialOffsetScheme(InitialOffsetScheme.AUTO)
```

The following table describes the different schemes and their behaviour, which varies depending on whether the subscriber is grouped or ungrouped. The default scheme is `AUTO`.

Scheme          |Ungrouped subscriber behaviour      |Grouped subscriber behaviour
:---------------|:-----------------------------------|:---------------------------
`EARLIEST`      |Starts from the first available offset in the stream.|Does nothing if a persisted offset exists; otherwise starts from the first available offset in the stream.
`LATEST`        |Starts from the last available offset in the stream.|Does nothing if a persisted offset exists; otherwise starts from the latest available offset in the stream.
`NONE`          |Illegal in this context; throws an `InvalidInitialOffsetSchemeException` when initialising the subscriber.|Does nothing if a persisted offset exists; otherwise will throw an `OffsetLoadException` when initialising the subscriber.
`AUTO`          |Acts as `LATEST`.                   |Acts as `EARLIEST`.

In addition to the initial offset reset scheme, an ungrouped subscriber may be also be repositioned to any offset by calling the `Subscriber.seek(long offset)` method, specifying the new offset. The offset must be in the valid range (between the first and last offsets in the stream), otherwise an error will occur either during a call to `offset()` or later, during a `poll()`.

## Asynchronous subscriber
Using the `Subscriber` API directly implies that you will dedicate an application thread to continuously invoke `poll()`, presumably from a loop. Alternatively, Meteor provides an asynchronous wrapper — `Receiver` — a background worker thread that continuously polls an underlying `Subscriber` instance for messages. Example below.

```java
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
```

Once attached, a `Receiver` instance will live alongside its backing `Subscriber`. Terminating the subscriber will result in terminating the attached receiver.

**Note:** At most one receiver may be attached; calling `attachReceiver()` a second time will result in an `IllegalStateException`. 

# Use cases
The following section compares typical enterprise messaging scenarios, traditionally implemented using MQ and pub-sub middleware, with constructs and patterns native to Meteor.

## Stream processing
This is usually the main driver behind adopting a message streaming platform — processing large volumes of contiguous data in real-time. The type of streaming data may be quite diverse, ranging from events, analytics, price quotes, transactions, commands, queries and so forth. 

It's common for streaming data to follow a specific order, which may be **total** or **partial**. When reasoning about order, it helps to consider pairwise relations. For example, chronological order is total because every conceivable pair of events can be placed in some deterministic order, given sufficient clock resolution. Causal order is partial as only some pairs of events may be deterministically ordered, while others are arbitrarily ordered and may freely commute (as in an Abelian group). A total order carries stronger information density than partial order and is generally more restrictive.

Meteor streams support total ordering of records and, by extension, partial ordering. Total order is expensive, but occasionally warranted. Totally ordered data usually imposes constraints on the number of subscribers that may process the stream (the subscriber ecosystem is assumably order-dependent and binds via a subscriber group). When dealing with partially ordered data, consider instead publishing the data across multiple streams, where each stream is totally ordered. This allows for the subscriber ecosystem to process data in parallel; the greater the number of streams, the more subscribers may be used to process the data in parallel.

**Note:** Meteor does not (yet) support subscriber load balancing. So while multiple subscribers across different JVMs may be used to read from several streams in parallel, there is no guarantee that the load will be evenly distributed across the subscriber JVMs.

## Pub-sub topics
A pub-sub topic assumes a (multi)point-to-multipoint topology, with multiple interested parties that typically represent disparate applications. Reading from a topic does not result in message consumption (in that it does not affect message visibility), allowing multiple consumers to read the same message from the topic. Consumers are subscribed to the message feed only for as long as they remain connected; a disconnection (however brief) may result in message forfeiture. As such, topics in conventional MoM are often seen as best-effort constructs, yielding the highest performance with minimal guarantees; where these are insufficient, queues are used instead.

Pub-sub topics are trivially accommodated with Meteor streams. Multiple subscribers connect to a stream without specifying a subscriber group, thereby achieving an unbounded (multi)point-to-multipoint topology. The initial offset scheme is typically set to `LATEST`, which is the default for ungrouped subscribers in `AUTO` mode. The notion of messages not being consumed by any single party as well as the transient subscription model are both natively accommodated by Meteor.

## Message queues
MQ is considered to be a subset of the stream processing scenario, where there is typically (although not necessarily) an absence of total ordering between messages in the stream. Subscribers connect to the stream with an overarching subscriber group and with the initial offset scheme set to `EARLIEST`, being the default for grouped subscribers in `AUTO` mode.

An MQ typically accommodates multiple concurrent consumers for parallelism, each consuming one message from the queue at a time in arbitrary order (usually round-robin). Because streams are always assumed to be totally ordered (even if they might not be), this is not an option in Meteor. As such, and similarly to the stream processing scenario, if subscriber parallelism is required, the publisher should subdivide the messages into multiple streams.

**Note:** Future versions may allow the subscriber ecosystem to dynamically project (re-map) a totally ordered stream unto multiple partially ordered views and consume from these views in parallel.

Consumer failure is a standard concern in MQ-centric architectures. If a consumer fails during message processing, the in-flight message will eventually time out and will be redelivered to another consumer (or will at least be marked as 'unconsumed' on the broker). Meteor uses subscriber groups and subscriber read offset tracking to deal with failures. If a subscriber fail-over occurs, the newly assigned subscriber will resume processing from the last confirmed offset. Meteor subscribers must deal with at-least-once delivery; the same principle applies to MQ consumers.

## Dead-letter queues
A brief note on dead-letter queues. A DLQ is a higher-order construct often appearing within mainstream MoM implementations as a way of dealing with unprocessable messages and ensuring message flow (the liveness property of a distributed system). The semantics of DLQs, while largely in the same spirit, vary subtly among implementations. The basic premise is that if a consumer is seemingly unable to deal with the message, this is eventually detected by the broker and the message is routed to the DLQ instead. (Often detection is simply based on subscriber timeouts and a fixed number of attempts, and occasionally false positives occur.)

Meteor (like other streaming platforms) offloads the responsibility of dealing with unprocessable messages to the subscribers. The basic rationale is that a streaming platform serves as an unopinionated, high performance streaming transport between publishers and subscribers, allowing for a variety of topologies to be superimposed, but without unnecessarily constraining either party. A MoM cannot definitively tell whether a message is unprocessable; a subscriber is best-placed to determine this locally and act accordingly. This may include silently dropping the message, logging an error, or republishing the message to an agreed stream — the latter being analogous to a DLQ.

A subscriber should generally err on the side of caution, not placing excessive trust in the quality of published messages, especially when messages originate from a different ecosystem or in low assurance environments. A good practice is to surround the receipt of a record with a `try-catch` block, trapping and logging all exceptions, up to `Throwable`.

**Note:** We're not implying that a DLQ is entirely useless or is an anti-pattern; the claim is merely that a DLQ is not strictly necessary for the construction of robust message-oriented systems and that alternate patterns may be used.


# Architecture
To fully get your head around the design of Meteor, you need to first understand [Hazelcast and the basics of In-Memory Data Grids](https://hazelcast.com/use-cases/imdg/). In short, an IMDG pools the memory heap of multiple processes across different machines, creating the illusion of a massive computer comprising lots of cooperating processes, with the combined computational (RAM & CPU) resources. An IMDG is inherently elastic; processes are free to join and leave the grid at any time. The underlying data is sharded for performance and replicated across multiple processes for availability, and can be optionally persisted for durability.

The schematic below outlines the key architectural concepts. 

```
     +---------------+    +---------------+    +---------------+    +---------------+
     |  JVM process  |    |  JVM process  |    |  JVM process  |    |  JVM process  |
     |               |    |               |    |               |    |               |
+----+---------------+----+---------------+----+---------------+----+---------------+----+
|  METEOR SERVICE                      << streams >>                                     |
+----------------------------------------------------------------------------------------+
|  HAZELCAST IMDG                                                                        |
+----+---------------+----+---------------+----+---------------+----+---------------+----+
     |               |    |               |    |               |    |               |
     |  Application  |    |  Application  |    |  Application  |    |  Application  |
     |   (pub-sub)   |    |   (pub-sub)   |    |   (pub-sub)   |    |   (pub-sub)   |
     +---------------+    +---------------+    +---------------+    +---------------+
```

The Meteor architecture comprises just two major layers. The bottom layer is plain Hazelcast, providing foundational grid services and basic distributed data structures — hash maps, ring buffers, and so on. Meteor has no awareness of your physical grid topology, network security, addressing or discovery — it relies on being handed an appropriately configured `HazelcastInstance`. If your application already utilises Hazelcast, you would typically reuse the same `HazelcastInstance`.

The Meteor service layer is further composed of two notional sub-layers:

1. The **client layer** exposes high-level `Publisher` and `Subscriber` APIs to the application, a data model as well as a set of configuration objects. This is the façade that your application interacts with for publishing and subscribing to streams.
2. The **protocol layer** encompasses low-level capabilities required to operate a distributed message bus. This includes such aspects as leader election for managing subscriber group assignments, group offset tracking, load balancing, subscriber health monitoring and response, batching, data compression and record versioning. This layer is quite complex and is intentionally abstracted from your application by the client layer.

The relationship between your application code, Meteor and Hazelcast is depicted below.

```
+------------------------------------------------------------------------------+                                     
|                              Pub-Sub Application                             | <= application layer
+------------------------------------------------------------------------------+ 
                                       V
+------------------------------------------------------------------------------+
|   PUBLISHER API   |   SUBSCRIBER API   |   OBJECT MODEL   |  CONFIGURATION   | <= client layer
+------------------------------------------------------------------------------+
| Leader   | Subscriber | Subscriber | Subscriber | Record       | Batching &  |
| Election | Offset     | Group      | Health     | Marshalling  | Data        | <= protocol layer
|          | Tracking   | Balancing  | Monitoring | & Versioning | Compression |
+------------------------------------------------------------------------------+
                                       V
+------------------------------------------------------------------------------+                                     
|                                 Hazelcast IMDG                               | <= data grid
+------------------------------------------------------------------------------+ 
```

**Note:** Some of the capabilities described above exist only in design and are yet to be implemented. The outstanding capabilities are: record versioning, batching and compression. These should be implemented by the time Meteor reaches its 1.0.0 release milestone.

## Replicas
A stream is mapped to a single ring buffer by the protocol layer, which will be mastered by a single (leader) process node within Hazelcast. The ring buffer's leader is responsible for marshalling all writes to the buffer and serving the read queries. The leader will also optionally replicate writes to replicas, if these are configured in your `StreamConfig`. There are two types of replicas: **sync replicas** and **async replicas**. 

Sync replicas will be written to before the publish operation is acknowledged, and before any subscribers are allowed to see the published record. Sync replicas facilitate data redundancy but increase the latency of publishing to a stream. Sync replicas are fed in parallel; the publishing time is the ceiling of all individual replication times.

Async replicas are fed in the background, providing additional redundancy without the expense of blocking.

A production environment should be configured with at least one sync replica, ideally two sync replicas if possible. The default stream configuration is set to one sync replica and zero async replicas.

## Durability
In traditional client-server architectures (such as Kafka and Kinesis) the concepts of _availability_ and _durability_ are typically combined. A replica will be statically affiliated with a set of shards and will persist data locally, in stable storage, and will make that data available either for read queries or in the event of a leader failure. It is typically infeasible to move (potentially terabytes of) data from one replica to another.

By contrast, in an IMDG-based architecture, replicas are dynamic processes that join and leave the grid sporadically, and store a limited amount of data in memory. An IMDG employs consistent hashing in order to balance the shards across the cluster and assign replicas, providing both scalability and availability.

Further assigning storage responsibilities to replicas is intractable in the dynamic ecosystem of an IMDG. Even with consistent hashing, the amount of data migration would be prohibitive, leading to noticeable purturbations in performance when nodes join and leave the grid. As such, durability in an IMDG is an _orthogonal_ concern; the shard leader will delegate to a separate storage repository for writing and reading long-term data that may no longer be available in grid memory. This data store should be discoverable and accessible from all nodes in the grid.

In its present form, Meteor relies on Hazelcast's `RingbufferStore` to provide unbounded data persistence. This lets you plug in a standard database or a disk-backed cache (such as Redis) into Meteor. Subscribers will be able to read historical data from a stream, beyond what is accommodated by the underlying ring buffer's capacity. In future iterations, the plan for Meteor is to offer a turnkey orthogonal persistence engine that is optimised for storing large volumes of log-structured data.


# Further Reading
* [Roadmap](https://github.com/obsidiandynamics/meteor/tree/master/ROADMAP.md)