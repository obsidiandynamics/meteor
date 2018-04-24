<img src="https://raw.githubusercontent.com/wiki/obsidiandynamics/hazelq/images/hazelq-logo.png" width="90px" alt="logo"/> HazelQ
===
Message streaming over Hazelcast IMDG.

[![Download](https://api.bintray.com/packages/obsidiandynamics/hazelq/hazelq-core/images/download.svg) ](https://bintray.com/obsidiandynamics/hazelq/hazelq-core/_latestVersion)
[![Build](https://travis-ci.org/obsidiandynamics/hazelq.svg?branch=master) ](https://travis-ci.org/obsidiandynamics/hazelq#)
[![codecov](https://codecov.io/gh/obsidiandynamics/hazelq/branch/master/graph/badge.svg)](https://codecov.io/gh/obsidiandynamics/hazelq)

# What is HazelQ
**TL;DR** — HazelQ is a broker-less, embeddable version of Kafka that runs in an In-Memory Data Grid.

## History
HazelQ started out as a part of Blackstrom — a research project into ultra-fast transactional mesh fabric technology for distributed micro-service and event-driven architectures. Blackstrom originally relied on Kafka, but we longed for a more lightweight distributed ledger for testing, simulation and small-scale deployments. It had to have a **zero deployment footprint** (no brokers or other middleware), be reasonably performant, reliable and highly available. We wanted Kafka, but without the brokers.

HazelQ showed lots of potential early in its journey, surpassing all expectations in terms of performance and scalability. Eventually it got broken off into a separate project, and so here we are.

## Fundamentals
### Streams, records and offsets
A stream is a totally ordered sequence of records, and is fundamental to HazelQ. A record has an ID (64-bit integer) and a payload, which is an array of bytes. 'Totally ordered' means that, for any given publisher, records will be written in the order they were emitted. If record _P_ was published before _Q_, then _P_ will precede _Q_ in the stream. Furthermore, they will be read in the same order by all subscribers; _P_ will always be read before _Q_.

There is no recognised causal ordering _across_ publishers; if two (or more) publishers emit records simultaneously, those records may materialise in arbitrary order. However, this ordering will be observed consistently across all subscribers.

The record offset uniquely identifies a record in the stream, and is used for O(1) lookups. The offset is a strictly monotonically increasing integer in a sparse address space, meaning that each successive offset is always higher than its predecessor and there may be varying gaps between successively assigned offsets. Your application shouldn't try to interpret the offset or guess what the next offset might be; it may, however, infer the relative order of any record pair based on their offsets, sort the records, and so forth.

```
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
```

### Publishers
Publishers place records into the stream, for consumption by any number of subscribers. The pub/sub topology adheres to a multipoint-to-multipoint model, meaning that there may be any number of publishers and subscribers interacting with a stream. Often a single publisher is used with multiple subscribers.

### Subscribers
A subscriber is a stateful entity that reads a message from a stream, one at a time. **The act of reading a message does not consume it.** In fact, subscribers have absolutely no impact on the stream. This is the main point of distinction between a message stream and a traditional message queue (MQ).

A subscriber internally maintains an offset that points to the next message in the stream, advancing the offset for every successive read. When a subscriber first attaches to a stream, it may elect to start at the head-end or the tail-end of the stream, or seek to a particular offset.

Subscribers retain their offset state locally. Because subscribers do not interfere, there may be any number of subscribers reading the same stream, each with their own position in the stream. Subscribers may operate at any rate; a slow/backlogged subscriber has no negative effect on its peers.

### Subscriber groups
Subscribers may be optionally associated with a group, which provides exclusivity of stream reading, as well as offset tracking. For a set of subscribers sharing a common group, at most one subscriber is allowed to read from the stream. The assignment of a subscriber to a stream is random, biased towards first-come-first-serve. Only the assigned subscriber may read from the stream; other subscribers will 'circle' the stream in a holding pattern. 

By routinely reading a message from a stream, the subscriber implicitly indicates that it is healthy, thereby maintaining its assignment indefinitely. However, should the subscriber fail to read again within the allowable deadline, it will be deemed as faulty and the stream will be reassigned to an alternate subscriber in the same group.

A grouped subscriber can write its last-read offset back to the grid, recording it in a dedicated metadata area. This is called _confirming_ an offset. A confirmed offset implies that the record at that offset **and all prior records** have been dealt with by the group. A word of caution: an offset should only be confirmed when your application is done with the message in question and all messages before it.

If an ungrouped subscriber is closed and reopened, it will lose its offset. It is up to the application to appropriately reset the free subscribers.

### At-least-once delivery

### Stream capacity, replication and persistence
HazelQ stores the records in a distributed ring buffer.

## Architecture
To get HazelQ, you need to first understand [Hazelcast and the basics of In-Memory Data Grids](https://hazelcast.com/use-cases/imdg/). In short, an IMDG pools the memory heap of multiple processes across different machines, creating the illusion of a massive computer comprising lots of cooperating processes, with the combined computational (RAM & CPU) resources. An IMDG is inherently elastic; processes are free to join and leave the grid at any time. The underlying data is sharded for performance and replicated across multiple processes for availability, and can be optionally persisted for durability.

The schematic below outlines the key architectural concepts. 

```
     +---------------+    +---------------+    +---------------+    +---------------+
     |  JVM process  |    |  JVM process  |    |  JVM process  |    |  JVM process  |
     |               |    |               |    |               |    |               |
+----+---------------+----+---------------+----+---------------+----+---------------+----+
|  HAZELQ SERVICE                      << streams >>                                     |
+----------------------------------------------------------------------------------------+
|  HAZELCAST IMDG                                                                        |
+----+---------------+----+---------------+----+---------------+----+---------------+----+
     |               |    |               |    |               |    |               |
     |  Application  |    |  Application  |    |  Application  |    |  Application  |
     |   (pub/sub)   |    |   (pub/sub)   |    |   (pub/sub)   |    |   (pub/sub)   |
     +---------------+    +---------------+    +---------------+    +---------------+
```

The HazelQ architecture comprises just two major layers. The bottom layer is plain Hazelcast, providing foundational grid services and basic distributed data structures — hash maps, ring buffers, and so on. HazelQ has no awareness of your physical grid topology, network security, addressing or discovery — it relies on being handed an appropriately configured `HazelcastInstance`. If your application already utilises Hazelcast, you would typically reuse the same `HazelcastInstance`.

The HazelQ service layer is further composed of two notional sub-layers:

1. The **client layer** exposes high-level `Publisher` and `Subscriber` APIs to the application, a data model as well as a set of configuration objects. This is the façade that your application interacts with for publishing and subscribing to streams.
2. The **protocol layer** encompasses low-level capabilities required to operate a distributed message bus. This includes such aspects as leader election for managing subscriber group assignments, group offset tracking, load balancing, subscriber health monitoring and response, batching, data compression and record versioning. This layer is quite complex and is intentionally abstracted from your application by the client layer.

The relationship between your application code, HazelQ and Hazelcast is depicted below.

```
+------------------------------------------------------------------------------+                                     
|                              Pub/Sub Application                             | <= application layer
+------------------------------------------------------------------------------+ 
                                     ||||||
                                     VVVVVV
+------------------------------------------------------------------------------+
|   PUBLISHER API   |   SUBSCRIBER API   |   OBJECT MODEL   |  CONFIGURATION   | <= client layer
+------------------------------------------------------------------------------+
| Leader   | Subscriber | Subscriber | Subscriber | Record       | Batching &  |
| Election | Offset     | Group      | Health     | Marshalling  | Data        | <= protocol layer
|          | Tracking   | Balancing  | Monitoring | & Versioning | Compression |
+------------------------------------------------------------------------------+
                                     ||||||
                                     VVVVVV
+------------------------------------------------------------------------------+                                     
|                                 Hazelcast IMDG                               | <= data grid
+------------------------------------------------------------------------------+ 
```

**Note:** Some of the capabilities described above exist only in design and are yet to be implemented. The outstanding capabilities are: record versioning, batching and compression. These should be implemented by the time HazelQ reaches its 1.0.0 release milestone.

## Persistence

# Getting Started

# Use cases
## Distributed ledger

## Stream processing

## Pub/sub topics

## Message queue

# Roadmap
* Lanes within streams: **HIGH PRIORITY**
  - Currently the biggest limitation of HazelQ, particularly when comparing to Kafka and Kinesis.
  - Need a set of totally ordered message sequences that roll into a single partially ordered stream.
  - Enables parallel stream processing use cases with multi-subscriber load balancing. Paves the way for a fully-fledged message streaming platform.
  - Currently there's no notion of parallelism. Under the current model, messages would have to be mapped to multiple streams and there's no (and should never be) load balancing _across_ streams, as streams are meant to be completely unrelated.
* Record versioning and backward compatibility with rolling upgrades: **HIGH PRIORITY**
  - Any changes to the record structure will break older clients when doing a rolling update. This means that the only way of upgrading a grid is to either bring it offline, or to terminate subscribers (which requires bespoke code on the application end).
  - Add a version field to the head of a batch. A publisher always writes to the ring buffer in the latest (from its perspective) version.
  - Forward compatibility: if a subscriber sees an unsupported message, it will halt processing. Assumingly at some point in the near future the subscriber's JVM is restarted and a new version of HazelQ is loaded. 
  - We could even go as far as automatically unsubscribing the subscriber when it sees an unsupported version, causing a rebalancing of subscriber assignments. The lease might bounce around among old subscribers until eventually an upgraded subscriber is elected. (Over the upgrade window, the number of old subscribers should diminish rapidly.)
  - Backward compatibility: if a subscriber observes a message of an older version, it will apply a series of transforms to stage-wise upgrade the message to the current schema on the fly. As we can't rewrite the messages _in situ_, the HazelQ codebase must retain all schemas and migration rules up to the current version. We could use separate project modules to store version-specific schemas and transforms; the apps can include only those dependencies they need.
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
  - Approach is similar to how [Jackdaw](https://github.com/obsidiandynamics/jackdaw) pipelines Kafka I/O and serialization; only the pipelines will be integrated into the HazelQ client API (because we can) and thus made completely transparent to the application.
* JMX metrics
* Auto-confirm of subscriber offsets. Currently this is a manual call to `Subscriber.confirm()`.
* Metadata server:
  - Currently all publisher and subscribers to a stream must agree on all of the stream's parameters — capacity, number of sync/async, replicas, storage implementation, etc. There is no way to discover this information. The present design, however restrictive, ensures that _any_ cohort can auto-create the stream if one doesn't exist. (In other words, streams are always created lazily, upon first use.) In practice, this is acceptable for long-lived streams and where the stream configuration is static and can be agreed upon and disseminated out-of-band.
  - Ideally, publishers and subscribers should refer to stream solely by its name, without concerning themselves with its underlying configuration. Create a stream metedata service that holds a serialized `StreamConfig` (e.g. JSON with YConf mappings) for a given stream name. (A distributed hash map should do.) 
  - The act of looking up the stream's metadata should be separate from the act of connecting to the stream for pub/sub. The lookup operation is done via a separate `MetadataService` API and may take an optional `Supplier<StreamConfig>`, in case the stream doesn't exist.
  - There would ideally be one application responsible for 'mastering' the stream; that application would house the stream config and pass it as the default value. Typically, that application would be one of the publishers. Other applications would perform the lookup without knowledge of the default value; if metadata is missing then the application would either back off or fail (or more pragmatically, fail after some number of back-offs). Perhaps the lookup API could take a timeout value, backing off and retrying behind the scenes.
  - Being a distributed hash map, the metadata map might itself be created lazily. For this reason, _all_ cohorts must agree on the metadata map configuration. Sensible defaults should be provided by HazelQ, with the option to override.
  - Metadata persistence: this wouldn't be an issue for transient (non-persisted topics); however, persisted topics might survive their own metadata if the grid is reformed. The only problem is that there isn't a sensible default persistence configuration for the metadata hash map. The options are to either agree on a global configuration which is dispersed out-of-band, or to apply the `Supplier<MetadataConfig>` pattern and make one 'pilot' process responsible for metadata 'bootstrapping'. If all metadata replicas are lost, the other procs would have to wait for the pilot proc to join the grid. The same pilot proc could also be used to 'master' the streams — acting as a central repository of configuration, which it immediately transfers to the grid. For as long as the grid is intact, the pilot proc is dormant.
  - The pilot is a simple process attached to the grid that can be remotely configured using a Hazelcast topic.
* Parallel persistence engine:
  - Traditional challenge with persistence of ordered messages is that the writing a message blocks all other writers, waiting in a write queue. However, it's a simple model involving one large (albeit blocking) write per message. Reading a record is also done in one operation.
  - Proposed approach: publisher persists batch and obtains a unique (DB-assigned) store ID (slow operation, but done in parallel across publishers) before putting the compressed batch and the store ID on the ring buffer. `RingbufferStore` completes the loop by associating the store ID with the message offset (fast operation that is blocking within the master shard), indexed by ring buffer offset, before acknowledging the write.
  - By the time the batch is observed by subscribers, the batch would have been persisted and linked back to the ring buffer offset.
  - If the ring buffer cell has lapsed, `RingbufferStore` looks up the store ID for the given ring buffer offset. Then the store ID is resolved to the batch data. (Two discrete operations are required for the read, which may be issued as one composite operation depending on the persistence stack and query language semantics.)
  - Persistence must also apply to subscriber offsets. Offsets may be persisted lazily; there's no need to fsync the offset before returning.
* Background (semi-)compaction:
  - Persisted messages are obsoleted in the background based on key, and are thereby excluded from the batch, leaving a hole which may in theory be squashed (as long as the intra-batch message numbering is preserved). If the last message in a batch is obsoleted, then the batch is fed as a special _void batch_ to the subscriber, with a pointer to the next non-void batch. This way, if there is a large void in the message log (several contiguous void batches), the subscriber can rapidly skip over those ring buffer cells.
  - This might be called semi-compaction as it works at a batch level; it doesn't shuffle messages between batches or try to splice buddying batches to avoid external fragmentation. The algorithm reaches peak efficiency when entire batches can be reclaimed and the subscribers can begin to fast-forward their offsets, skipping over the ring buffer cells. Even if there are still lots of old non-void batches left due to sparsely distributed relevant/un-compacted messages, we can still realise performance gains by allowing subscribers to silently skip over the compacted messages.
  - We're ultimately limited by the ring buffer structure which isn't naturally prone to compaction (as opposed to chained log nodes, which can easily be buddied and spliced). Effectively, we are trying to reduce a ring buffer to a skip list.