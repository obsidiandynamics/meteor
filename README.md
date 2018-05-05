<img src="https://raw.githubusercontent.com/wiki/obsidiandynamics/meteor/images/meteor-logo.png" width="90px" alt="logo"/> Meteor
===
Real-time message streaming over Hazelcast IMDG.

[![Download](https://api.bintray.com/packages/obsidiandynamics/meteor/meteor-core/images/download.svg) ](https://bintray.com/obsidiandynamics/meteor/meteor-core/_latestVersion)
[![Build](https://travis-ci.org/obsidiandynamics/meteor.svg?branch=master) ](https://travis-ci.org/obsidiandynamics/meteor#)
[![codecov](https://codecov.io/gh/obsidiandynamics/meteor/branch/master/graph/badge.svg)](https://codecov.io/gh/obsidiandynamics/meteor)

# What is Meteor
**TL;DR** — Meteor is a broker-less, lightweight embeddable version of Kafka that runs in an In-Memory Data Grid.

## History
Meteor started out as a part of Blackstrom — a research project into ultra-fast transactional mesh fabric technology for distributed micro-service and event-driven architectures. Blackstrom originally relied on Kafka, but we longed for a more lightweight distributed ledger for testing, simulation and small-scale deployments. It had to have a **zero deployment footprint** (no brokers or other middleware), be reasonably performant, reliable and highly available. We wanted Kafka, but without the brokers.

To that point, Meteor wasn't designed to be a 'better Kafka', but rather an alternative streaming platform that has its own merits and may be a better fit in certain contexts.

Meteor showed lots of potential early in its journey, surpassing all expectations in terms of performance and scalability. Eventually it got broken off into a separate project, and so here we are.

## Fundamentals
### Real-time message streaming
The purpose of a real-time message streaming platform is to enable very high-volume, low-latency distribution of message-oriented content among distributed processes that are completely decoupled from each other. These process fall into one of two categories: publishers (emitters of message content) and subscribers (consumers of messages).

Message streaming platforms are similar to traditional message queues, but generally offer stronger temporal guarantees. Whereas messages in an MQ tend to be arbitrarily ordered and generally independent of one another, messages in a stream tend to be strongly ordered, often chronologically or causally. Also, a stream persists its messages, whereas an MQ will discard a message once it's been read. For this reason, message streaming tends to be a better fit for implementing Event-Driven Architectures, encompassing event sourcing, eventual consistency and CQRS concepts.

### Streams, records and offsets
A stream is a totally ordered sequence of records, and is fundamental to Meteor. A record has an ID (64-bit integer) and a payload, which is an array of bytes. 'Totally ordered' means that, for any given publisher, records will be written in the order they were emitted. If record _P_ was published before _Q_, then _P_ will precede _Q_ in the stream. Furthermore, they will be read in the same order by all subscribers; _P_ will always be read before _Q_. (Depending on the context, we may sometimes use the term _message_ to refer to a record, as messaging is the dominant use case for Meteor.)

There is no recognised causal ordering _across_ publishers; if two (or more) publishers emit records simultaneously, those records may materialise in arbitrary order. However, this ordering will be observed consistently across all subscribers.

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
Publishers place records into the stream, for consumption by any number of subscribers. The pub-sub topology adheres to a broad multipoint-to-multipoint model, meaning that there may be any number of publishers and subscribers simultaneously interacting with a stream. Depending on the actual solution context, stream topologies may be point-to-multipoint, multipoint-to-point and point-to-point.

### Subscribers
A subscriber is a stateful entity that reads a message from a stream, one at a time. **The act of reading a message does not consume it.** In fact, subscribers have absolutely no impact on the stream. This is a yet another point of distinction between a message stream and a traditional message queue (MQ).

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

By routinely reading a record from a stream, the subscriber implicitly indicates to its peers that it is 'healthy', thereby extending its assignment. However, should the subscriber fail to read again within the allowable deadline, it will be deemed as faulty and the stream will be reassigned to an alternate subscriber in the same group, so long as an alternate subscriber exists.

A grouped subscriber also tracks its current read offset locally. It also may (and typically will) write its last checkpoint offset back to the grid, recording it in a dedicated metadata area. This is called _confirming_ an offset. A confirmed offset implies that the record at that offset **and all prior records** have been dealt with by the subscriber. A word of caution: an offset should only be confirmed when your application is done with the record in question and all records before it. By 'done with' we mean the record has been processed to the point that any actions that would have resulted from the record have been carried out. This may include calling other APIs, updating a database, persisting the record's payload, or publishing more records.

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

Typically a subscriber will confirm its read offset linearly, in tandem with the processing of the records. (Read a record, confirm it, read the next, confirm, and so on.) However, the subscriber is free to adopt any confirmation strategy, so long as it doesn't confirm records that haven't yet been completely processed. A subscriber may, for example, elect to process a batch of records concurrently, and only confirm the last record when the entire batch is done.

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

At-least-once semantics do carry a caveat: under some circumstances it may be possible for your application to read the same message repeatedly, even if it had already dealt with the message. For example, you may have dealt with the message but crashed just before confirming it. When a new subscriber takes over the stream, it will replay messages from the last confirmed checkpoint. Alternatively, a message may have been confirmed and another subscriber in the group took over the stream, but due to the asynchronous nature of confirmations the new subscriber will replay from the point of the last received confirmation.

**Subscribers in message streaming applications must be idempotent.** In other words, processing the same record repeatedly should have no net effect on the subscriber ecosystem. If a record has no _additive_ effects, the subscriber is inherently idempotent. (For example, if the subscriber simply overwrites an existing database entry with a new one, then the update is idempotent.) Otherwise, the subscriber must check whether a record has already been processed, and to what extent, prior to processing a record. _The combination of at-least-once delivery and subscriber idempotence leads to exactly-once semantics._

### Stream capacity
Meteor stores the records in a distributed ring buffer within the IMDG — each stream corresponds to one Hazelcast ring buffer. The ring buffer has a configurable, albeit a finite capacity; when the capacity is exhausted, the oldest records will be overwritten. The subscriber API doesn't expose the ring topology; you just see an ordered list of records that gets truncated from the tail end when the buffer is at capacity. The default stream capacity is 10,000 records; this can be overridden via the `StreamConfig` class.

Meteor lets you access historical records beyond the buffer's capacity by providing a custom `RingbufferStore` implementation via `StreamConfig`.

# Getting Started
## Dependencies
Gradle builds are hosted on JCenter. Add the following snippet to your build file, replacing `x.y.z` with the version shown on the Download badge at the top of this README.

```groovy
compile "com.obsidiandynamics.meteor:meteor-core:x.y.z"
compile "com.hazelcast:hazelcast:3.10-BETA-2"
```

**Note:** Although Meteor is compiled against Hazelcast 3.10, no specific Hazelcast client library dependency has been bundled with Meteor. This lets you to use any 3.x API-compatible Meteor client library in your application without being constrained by transitive dependencies.

Meteor is packaged as two separate modules:

1. `meteor-core` — The Meteor implementation. This is the only module you need in production.
2. `meteor-assurance` — Mocking components and test utilities. Normally, this module would only be used during testing and should be declared in the `testCompile` configuration. See below.

```groovy
testCompile "com.obsidiandynamics.meteor:meteor-assurance:x.y.z"
testCompile "com.hazelcast:hazelcast:3.10-BETA-2"
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

Replace the version in the snippet with the version on the Zlg download badge.

[![Download](https://api.bintray.com/packages/obsidiandynamics/zerolog/zerolog-core/images/download.svg) ](https://bintray.com/obsidiandynamics/zerolog/zerolog-core/_latestVersion)

## Working with byte arrays
We want to keep to a light feature set until the project matures to a production-grade system. For the time being, there is no concept of object (de)serialization built into Meteor; instead records deal directly with byte arrays. While it means you have to push your own bytes around, it gives you the most flexibility with the choice of serialization frameworks. In practice, all serializers convert between objects and either character or byte streams, so plugging in a serializer is a trivial matter.

**Note:** Future iterations may include a mechanism for serializing objects and, crucially, _pipelining_ — where (de)serialization is performed in a different thread to the rest of the application, thereby capitalising on multi-core CPUs.

## Switching providers
`HazelcastProvider` is an abstract factory for obtaining `HazelcastInstance` objects with varying behaviour. Its use is completely optional — if you are already accustomed to using a `HazelcastInstanceFactory`, you may continue to do so. The real advantage is that it allows for dependency inversion — decoupling your application from the physical grid. Presently, there are two implementations:

* `GridProvider` is a factory for `HazelcastInstance` instances that connect to a real grid. The instances produced are the same as those made by `HazelcastInstanceFactory`.
* `TestProvider` is a factory for connecting to a virtual 'test' grid; one which is simulated internally and doesn't leave the confines of the JVM. `TestProvider` requires the `meteor-assurance` module on the classpath.

## Initial offset scheme
When a subscriber attaches to a stream for the first time, it needs an initial offset to start consuming from. This is configured by passing an `InitialSchemeOffset` enum to the `SubscriberConfig`, as shown in the snippet below.

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

# Use cases
## Stream processing

## Pub-sub topics

## Message queue
// TODO dead letter

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
A stream is mapped to a single ring buffer by the protocol layer, which will be mastered by (or lead) a single process node within Hazelcast. The ring buffer's leader is responsible for marshalling all writes to the buffer and serving the read queries. The leader will also optionally replicate writes to replicas, if these are configured in your `StreamConfig`. There are two types of replicas: **sync replicas** and **async replicas**. 

Sync replicas will be written to before the publish operation is acknowledged, and before any subscribers are allowed to see the published record. Sync replicas facilitate data redundancy but increase the latency of publishing to a stream. Sync replicas are fed in parallel; the publishing time is the ceiling of all individual replication times.

Async replicas are fed in the background, providing additional redundancy without the expense of blocking.

A production environment should be configured with at least one sync replica, ideally two sync replicas if possible. The default stream configuration is set to one sync replica and zero async replicas.

## Durability
In traditional client-server architectures (such as Kafka and Kinesis) the concepts of _availability_ and _durability_ are typically combined. A replica will be statically affiliated with a set of shards and will persist data locally, in stable storage, and will make that data available either for read queries or in the event of a leader failure. It is typically infeasible to move (potentially terabytes of) data from one replica to another.

By contrast, in an IMDG-based architecture, replicas are dynamic processes that join and leave the grid sporadically, and store a limited amount of data in memory. Replicas use consistent hashing in order to balance the shards across the cluster, providing both scalability and availability.

Further assigning storage responsibilities to replicas is intractable in the dynamic ecosystem of an IMDG. Even with consistent hashing, the amount of data migration would be prohibitive. As such, durability in an IMDG is orthogonal concern; the shard leader will delegate to a centralised storage repository for writing and reading long-term data that may no longer be available in grid memory.

In its present form, Meteor relies on Hazelcast's `RingbufferStore` to provide unbounded data persistence. This lets you plug in a standard database or a disk-backed cache (such as Redis) into Meteor. Subscribers will be able to read historical data from a stream, beyond what is accommodated by the underlying ring buffer's capacity. In future iterations, the plan for Meteor is to offer a turnkey orthogonal persistence engine that is optimised for storing large volumes of log-structured data.

# Roadmap
* Lanes within streams: **HIGH PRIORITY**
  - Currently the biggest limitation of Meteor, particularly when comparing to Kafka and Kinesis.
  - Currently there's no notion of parallelism within a stream. Under the current model, messages would have to be mapped to multiple streams and there's no (and should never be) load balancing _across_ streams, as streams are meant to be completely unrelated.
  - Need a set of totally ordered message sequences that roll into a single partially ordered stream.
  - Enables parallel stream processing use cases with multi-subscriber load balancing. Paves the way for a fully-fledged message streaming platform.
* Record versioning and backward compatibility with rolling upgrades: **HIGH PRIORITY**
  - Any changes to the record structure will break older clients when doing a rolling update. This means that the only way of upgrading a grid is to either bring it offline, or to terminate subscribers (which requires bespoke code on the application end).
  - Add a version field to the head of a batch. A publisher always writes to the ring buffer in the latest (from its perspective) version.
  - Forward compatibility: if a subscriber sees an unsupported message, it will halt processing. Assumingly at some point in the near future the subscriber's JVM is restarted and a new version of Meteor is loaded. 
  - We could even go as far as automatically unsubscribing the subscriber when it sees an unsupported version, causing a rebalancing of subscriber assignments. The lease might bounce around among old subscribers until eventually an upgraded subscriber is elected. (Over the upgrade window, the number of old subscribers should diminish rapidly.)
  - Backward compatibility: if a subscriber observes a message of an older version, it will apply a series of transforms to stage-wise upgrade the message to the current schema on the fly. As we can't rewrite the messages _in situ_, the Meteor codebase must retain all schemas and migration rules up to the current version. We could use separate project modules to store version-specific schemas and transforms; the apps can include only those dependencies they need.
  - If persistence is available, then upgrades could be made on data _in situ_ with write-back, thus avoiding the need to retain all schemas and transforms in the codebase. If the subscriber pull a ring buffer cell with a version that is less than N - 1, then it can fetch from the data store instead. The write-back upgrade could be done with older clients still connected to the grid; they would need to halt processing if they encounter a newer schema.
  - Alternatively, we could apply further versioning at the topic level. An upgrade would pump messages from one topic to the next, transforming the messages _en route_. This could be quite complicated in the presence of older publishers, who will continue to publish to the old stream, unless we atomically cut over all publishers (old and new) to the new stream, and support schema N - 1 in the new stream. Also, this approach would interfere with message offsets (although this could be corrected via `RingbufferStore`). We might have to bite the bullet and go with stage-wise upgrades.
  - Versioning only applies to the records' on-wire representation; not to their payload schema. Payload versioning is the application's concern.
  - Thought: we might make backward compatibility exclusions/dispensations for versions 0.x.x, as schema evolution will be particularly liberal in the beginning and the understanding is that the 0.x.x library hasn't reached a milestone that permits its use in systems with unbounded data retention requirements.
* Micro-batching and LZ4 data compression:
  - The built-in batching offered by Hazelcast is limited to 1,000 records and isn't tunable; also no opportunity to pass the batch through a compression filter or perform any other pre-processing.
  - Proposal is to create an independent micro-batching layer with customisable stream filters; LZ4 support with configurable (possibly even self-tunable) block sizes should be out of the box.
* Keys and key-based sharding
* Client-level message serialization support:
  - Currently the API expects you to work directly with byte arrays, which is arbitrarily flexible but assumes experienced coders.
  - Manual byte-pushing minimises opportunities for pipelining within the client API. (The application becomes responsible for pipelining.)
  - Support for message serialization will be baked into the client APIs.
  - OOTB support for Jackson, Gson, Kryo and `java.io.Serializable`, as well as custom serializers.
  - Serialization will apply to both keys and values; different serializers may be used.
* Pipelining of client Hazelcast API calls separately from message (de)serialization (using separate threads for I/O and serialization):
  - Approach is similar to how [Jackdaw](https://github.com/obsidiandynamics/jackdaw) pipelines Kafka I/O and serialization; only the pipelines will be integrated into the Meteor client API (because we can) and thus made completely transparent to the application.
* JMX metrics
* Auto-confirming of subscriber offsets. Currently this is a manual call to `Subscriber.confirm()`.
* Metadata server:
  - Currently all publisher and subscribers to a stream must agree on all of the stream's parameters — capacity, number of sync/async, replicas, storage implementation, etc. There is no way to discover this information. The present design, however restrictive, ensures that _any_ cohort can auto-create the stream if one doesn't exist. (In other words, streams are always created lazily, upon first use.) In practice, this is acceptable for long-lived streams and where the stream configuration is static and can be agreed upon and disseminated out-of-band.
  - Ideally, publishers and subscribers should refer to stream solely by its name, without concerning themselves with its underlying configuration. Create a stream metedata service that holds a serialized `StreamConfig` (e.g. JSON with YConf mappings) for a given stream name. (A distributed hash map should do.) 
  - The act of looking up the stream's metadata should be separate from the act of connecting to the stream for pub-sub. The lookup operation is done via a separate `MetadataService` API and may take an optional `Supplier<StreamConfig>`, in case the stream doesn't exist.
  - There would ideally be one application responsible for 'mastering' the stream; that application would house the stream config and pass it as the default value. Typically, that application would be one of the publishers. Other applications would perform the lookup without knowledge of the default value; if metadata is missing then the application would either back off or fail (or more pragmatically, fail after some number of back-offs). Perhaps the lookup API could take a timeout value, backing off and retrying behind the scenes.
  - Being a distributed hash map, the metadata map might itself be created lazily. For this reason, _all_ cohorts must agree on the metadata map configuration. Sensible defaults should be provided by Meteor, with the option to override.
  - Metadata persistence: this wouldn't be an issue for transient (non-persisted topics); however, persisted topics might survive their own metadata if the grid is reformed. The only problem is that there isn't a sensible default persistence configuration for the metadata hash map. The options are to either agree on a global configuration which is dispersed out-of-band, or to apply the `Supplier<MetadataConfig>` pattern and make one 'pilot' process responsible for metadata 'bootstrapping'. If all metadata replicas are lost, the other procs would have to wait for the pilot proc to join the grid. The same pilot proc could also be used to 'master' the streams — acting as a central repository of configuration, which it immediately transfers to the grid. For as long as the grid is intact, the pilot proc is dormant.
  - The pilot is a simple process attached to the grid that can be remotely configured using a Hazelcast topic.
* Parallel persistence engine:
  - Traditional challenge with persistence of ordered messages is that the writing a message blocks all other writers, waiting in a write queue. However, it's a simple model involving one large (albeit blocking) write per message. Reading a record is also done in one operation.
  - Proposed approach: publisher persists batch and obtains a unique (DB-assigned) store ID (slow operation, but done in parallel across publishers) before putting the compressed batch and the store ID on the ring buffer. `RingbufferStore` completes the loop by associating the store ID with the message offset (fast operation that is blocking within the shard leader), indexed by ring buffer offset, before acknowledging the write.
  - By the time the batch is observed by subscribers, the batch would have been persisted and linked back to the ring buffer offset.
  - If the ring buffer cell has lapsed, `RingbufferStore` looks up the store ID for the given ring buffer offset. Then the store ID is resolved to the batch data. (Two discrete operations are required for the read, which may be issued as one composite operation depending on the persistence stack and query language semantics.)
  - Persistence must also apply to subscriber offsets. Offsets may be persisted lazily; there's no need to fsync the offset before returning.
* Background (semi-)compaction:
  - Persisted messages are obsoleted in the background based on key, and are thereby excluded from the batch, leaving a hole which may in theory be squashed (as long as the intra-batch message numbering is preserved). If the last message in a batch is obsoleted, then the batch is fed as a special _void batch_ to the subscriber, with a pointer to the next non-void batch. This way, if there is a large void in the message log (several contiguous void batches), the subscriber can rapidly skip over those ring buffer cells.
  - This might be called semi-compaction as it works at a batch level; it doesn't shuffle messages between batches or try to splice buddying batches to avoid external fragmentation. The algorithm reaches peak efficiency when entire batches can be reclaimed and the subscribers can begin to fast-forward their offsets, skipping over the ring buffer cells. Even if there are still lots of old non-void batches left due to sparsely distributed relevant/un-compacted messages, we can still realise performance gains by allowing subscribers to silently skip over the compacted messages.
  - We're ultimately limited by a ring buffer's rigid structure which isn't naturally prone to compaction (as opposed to chained log nodes, which can easily be buddied and spliced). At best, we can reduce a ring buffer to a skip list.