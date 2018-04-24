<img src="https://raw.githubusercontent.com/wiki/obsidiandynamics/hazelq/images/hazelq-logo.png" width="90px" alt="logo"/> HazelQ
===
Message streaming over Hazelcast IMDG.

[![Download](https://api.bintray.com/packages/obsidiandynamics/hazelq/hazelq-core/images/download.svg) ](https://bintray.com/obsidiandynamics/hazelq/hazelq-core/_latestVersion)
[![Build](https://travis-ci.org/obsidiandynamics/hazelq.svg?branch=master) ](https://travis-ci.org/obsidiandynamics/hazelq#)
[![codecov](https://codecov.io/gh/obsidiandynamics/hazelq/branch/master/graph/badge.svg)](https://codecov.io/gh/obsidiandynamics/hazelq)

# What is HazelQ
**TL;DR** — HazelQ is a broker-less, embeddable version of Kafka that runs in an In-Memory Data Grid.

## History
HazelQ started out as part of [Blackstrom](https://github.com/obsidiandynamics/blackstrom) — a research project into ultra-fast transactional mesh fabrics for distributed micro-service architectures. Blackstrom originally relied on Kafka, but we longed for a more lightweight distributed ledger for testing, simulation and small-scale deployments. It had to have a **zero deployment footprint** (no brokers or other middleware), be reasonably performant, reliable and highly available. We wanted Kafka, but without the brokers.

HazelQ showed lots of potential early in its journey, surpassing all expectations in terms of performance and scalability. Eventually it got broken off into a separate project, and so here we are.

## Key concepts


## Architecture
To get HazelQ, you need to first understand [Hazelcast and the basics of In-Memory Data Grids](https://hazelcast.com/use-cases/imdg/). In short, an IMDG pools the memory heap of multiple processes across different machines, creating the illusion of a massive computer comprising lots of cooperating processes, with the combined RAM and processing resources. An IMDG is inherently elastic; processes are free to join and leave the grid at any time. The shared data is replicated across multiple processes for availability and can be optionally persisted for durability.

The schematic below outlines the key architectural concepts. 

```
     +---------------+    +---------------+    +---------------+
     |      JVM      |    |      JVM      |    |      JVM      |
     |               |    |               |    |               |
+----+---------------+----+---------------+----+---------------+----+
|  HAZELQ SERVICE           << streams >>                           |
+-------------------------------------------------------------------+
|  HAZELCAST IMDG                                                   |
+----+---------------+----+---------------+----+---------------+----+
     |               |    |               |    |               |
     |  Application  |    |  Application  |    |  Application  |
     |   (pub/sub)   |    |   (pub/sub)   |    |   (pub/sub)   |
     +---------------+    +---------------+    +---------------+
```

The HazelQ architecture comprises just two major layers. The bottom layer is plain Hazelcast, providing foundational grid services and distributed data structures — hash maps, ring buffers, and so on. HazelQ has no awareness of your physical grid topology, network security, addressing or discovery — it relies on being handed an appropriately configured `HazelcastInstance`. If your application already utilises Hazelcast, you would typically reuse the same `HazelcastInstance`.

The HazelQ service layer is further composed of two notional sub-layers:

1. The client layer exposes high-level `Publisher` and `Subscriber` APIs to the application, as well as a set of configuration objects. This is the façade that your application interacts with for publishing and subscribing to streams.
2. The protocol layer encompasses low-level capabilities required to operate a distributed message stream. This includes such aspects as leader election for managing subscriber group assignments, group offset tracking, load balancing, subscriber health monitoring and response, batching, data compression and record versioning. This layer is quite complex and is intentionally abstracted from your application by the client layer.
