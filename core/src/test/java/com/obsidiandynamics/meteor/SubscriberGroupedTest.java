package com.obsidiandynamics.meteor;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;

import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

import com.hazelcast.core.*;
import com.hazelcast.ringbuffer.*;
import com.hazelcast.util.executor.*;
import com.obsidiandynamics.func.*;
import com.obsidiandynamics.junit.*;
import com.obsidiandynamics.threads.*;

@RunWith(Parameterized.class)
public final class SubscriberGroupedTest extends AbstractPubSubTest {
  @Parameterized.Parameters
  public static List<Object[]> data() {
    return TestCycle.timesQuietly(1);
  }
  
  /**
   *  Seek can only be performed in an ungrouped context.
   */
  @Test(expected=IllegalStateException.class)
  public void testIllegalSeek() {
    final String stream = "s";
    final String group = randomGroup();
    final int capacity = 1;

    final ExceptionHandler eh = mockExceptionHandler();
    final DefaultSubscriber s =
        configureSubscriber(new SubscriberConfig()
                            .withGroup(group)
                            .withExceptionHandler(eh)
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream)
                                              .withHeapCapacity(capacity)));
    s.seek(0);
    verifyNoError(eh);
  }

  /**
   *  Consuming from an empty buffer should result in a zero-size batch.
   *  
   *  @throws InterruptedException
   */
  @Test
  public void testConsumeEmpty() throws InterruptedException {
    final String stream = "s";
    final String group = randomGroup();
    final int capacity = 10;
    final ExceptionHandler eh = mockExceptionHandler();
    final DefaultSubscriber s = 
        configureSubscriber(new SubscriberConfig()
                            .withGroup(group)
                            .withExceptionHandler(eh)
                            .withElectionConfig(new ElectionConfig().withScavengeInterval(1))
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream)
                                              .withHeapCapacity(capacity)));
    final IMap<String, Long> offsets = s.getInstance().getMap(Namespace.HAZELQ_META.qualify("offsets." + stream));

    final RecordBatch b0 = s.poll(1);
    assertEquals(0, b0.size());
    
    wait.untilTrue(s::isAssigned);
    final RecordBatch b1 = s.poll(1);
    assertEquals(0, b1.size());
    
    assertEquals(0, offsets.size());
    s.confirm();
    
    verifyNoError(eh);
  }
  
  /**
   *  Tests consumption when the subscriber is not assigned (due to an existing lease), resulting in an 
   *  empty batch.<p>
   *  
   *  The subscriber is then allowed to take over the lease while it is in a middle of a long poll. The
   *  test verifies the responsiveness of {@code poll()} during a lease transfer. (Behind the scenes, the
   *  long poll should be split into a series of short polls if the subscriber is unassigned, so that
   *  assignment is quickly picked up by the poller.)
   *  
   *  @throws InterruptedException
   */
  @Test
  public void testNotAssignedFollowedByAssignment() throws InterruptedException {
    final String stream = "s";
    final String group = randomGroup();
    final int capacity = 10;
    final ExceptionHandler eh = mockExceptionHandler();
    
    // start with an existing lease
    final HazelcastInstance instance = newInstance();
    final IMap<String, byte[]> leases = instance.getMap(Namespace.HAZELQ_META.qualify("lease." + stream));
    leases.put(group, Lease.forever(UUID.randomUUID()).pack());
    final DefaultSubscriber s = 
        configureSubscriber(instance,
                            new SubscriberConfig()
                            .withGroup(group)
                            .withExceptionHandler(eh)
                            .withElectionConfig(new ElectionConfig().withScavengeInterval(1))
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream)
                                              .withHeapCapacity(capacity)));
    final Ringbuffer<byte[]> buffer = s.getInstance().getRingbuffer(Namespace.HAZELQ_STREAM.qualify(stream));
    buffer.add("h0".getBytes());
    buffer.add("h1".getBytes());
    
    // shouldn't be able to consume any messages
    assertFalse(s.isAssigned());
    final RecordBatch b0 = s.poll(1);
    assertEquals(0, b0.size());
    
    // clear the lease behind the scenes, so that the subscriber can eventually take over
    final CyclicBarrier barrier = new CyclicBarrier(2);
    new Thread(() -> {
      Threads.await(barrier);
      Threads.sleep(10);
      leases.remove(group);
    }, "unblocker").start();
    
    Threads.await(barrier);
    
    // enter a long poll and verify that:
    // - the subscriber ends up consuming the messages;
    // - the assignment is picked up quickly (the poll returns in a short time)
    // - the subscriber is indicating that it is the assignee
    final long pollStarted = System.currentTimeMillis();
    final RecordBatch b1 = s.poll(60_000);
    final long pollTook = System.currentTimeMillis() - pollStarted;
    assertTrue("pollTook=" + pollTook, pollTook < 10_000);
    assertEquals(2, b1.size());
    assertTrue(s.isAssigned());
    
    verifyNoError(eh);
  }

  /**
   *  Tests consuming of messages, and that {@code Subscriber#poll(long)} extends
   *  the lease in the background. Also checks that we can confirm the offset of
   *  the last consumed message.
   *  
   *  @throws InterruptedException
   */
  @Test
  public void testConsumeExtendLeaseAndConfirm() throws InterruptedException {
    final String stream = "s";
    final String group = randomGroup();
    final int capacity = 10;

    final HazelcastInstance instance = newInstance();
    
    // start with an expired lease -- the new subscriber should take over it
    final IMap<String, byte[]> leases = instance.getMap(Namespace.HAZELQ_META.qualify("lease." + stream));
    leases.put(group, Lease.expired(UUID.randomUUID()).pack());

    final ExceptionHandler eh = mockExceptionHandler();
    final DefaultSubscriber s = 
        configureSubscriber(instance,
                            new SubscriberConfig()
                            .withGroup(group)
                            .withExceptionHandler(eh)
                            .withElectionConfig(new ElectionConfig().withScavengeInterval(1))
                            .withMinLeaseExtendInterval(0)
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream)
                                              .withHeapCapacity(capacity)));
    final Ringbuffer<byte[]> buffer = s.getInstance().getRingbuffer(Namespace.HAZELQ_STREAM.qualify(stream));
    final IMap<String, Long> offsets = s.getInstance().getMap(Namespace.HAZELQ_META.qualify("offsets." + stream));
    
    // start with the earliest offset
    offsets.put(group, -1L);
    
    // publish a pair of messages and wait until the subscriber is elected
    buffer.add("h0".getBytes());
    buffer.add("h1".getBytes());
    wait.untilTrue(s::isAssigned);
    
    // capture the original lease expiry so that it can be compared with the extended lease
    final long expiry0 = s.getElection().getLeaseView().getLease(group).getExpiry();
    
    // sleep and then consume messages -- this will eventually extend the lease
    final long sleepTime = 10;
    Thread.sleep(sleepTime);
    final RecordBatch b0 = s.poll(1_000);
    assertEquals(2, b0.size());
    assertArrayEquals("h0".getBytes(), b0.toList().get(0).getData());
    assertArrayEquals("h1".getBytes(), b0.toList().get(1).getData());
    
    // confirm offsets and check in the map
    s.confirm();
    wait.until(() -> assertEquals(1, (long) offsets.get(group)));
    
    // polling would also have extended the lease -- check the expiry
    wait.until(() -> {
      final long expiry1 = s.getElection().getLeaseView().getLease(group).getExpiry();
      assertTrue("expiry0=" + expiry0 + ", expiry1=" + expiry1, expiry1 >= expiry0 + sleepTime);
    });
    
    // another publish and poll to test that the lease will be extended again
    final long expiry2 = s.getElection().getLeaseView().getLease(group).getExpiry();
    buffer.add("h2".getBytes());
    buffer.add("h3".getBytes());
    Thread.sleep(sleepTime);
    final RecordBatch b1 = s.poll(1_000);
    assertEquals(2, b1.size());
    assertArrayEquals("h2".getBytes(), b1.toList().get(0).getData());
    assertArrayEquals("h3".getBytes(), b1.toList().get(1).getData());
    
    // we've set minLeaseExtendInterval=0, so expect the least to be extended
    wait.until(() -> {
      final long expiry3 = s.getElection().getLeaseView().getLease(group).getExpiry();
      assertTrue("expiry2=" + expiry2 + ", expiry3=" + expiry3, expiry3 >= expiry2 + sleepTime);
    });
    
    verifyNoError(eh);
  }

  /**
   *  Tests consuming of messages, and that {@code Subscriber#poll(long)} extends
   *  the lease in the background, subject to the constraints of {@code minLeaseExtendInterval},
   *  which should prevent the least from being extended unnecessarily during aggressive polling.
   *  
   *  @throws InterruptedException
   */
  @Test
  public void testConsumeExtendLeaseMinInterval() throws InterruptedException {
    final String stream = "s";
    final String group = randomGroup();
    final int capacity = 10;

    final HazelcastInstance instance = newInstance();
    
    // start with an expired lease -- the new subscriber should take over it
    final IMap<String, byte[]> leases = instance.getMap(Namespace.HAZELQ_META.qualify("lease." + stream));
    leases.put(group, Lease.expired(UUID.randomUUID()).pack());

    final ExceptionHandler eh = mockExceptionHandler();
    final DefaultSubscriber s = 
        configureSubscriber(instance,
                            new SubscriberConfig()
                            .withGroup(group)
                            .withExceptionHandler(eh)
                            .withElectionConfig(new ElectionConfig().withScavengeInterval(1))
                            .withMinLeaseExtendInterval(60_000)
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream)
                                              .withHeapCapacity(capacity)));
    final Ringbuffer<byte[]> buffer = s.getInstance().getRingbuffer(Namespace.HAZELQ_STREAM.qualify(stream));
    final IMap<String, Long> offsets = s.getInstance().getMap(Namespace.HAZELQ_META.qualify("offsets." + stream));
    
    // start with the earliest offset
    offsets.put(group, -1L);
    
    // publish a pair of messages and wait until the subscriber is elected
    buffer.add("h0".getBytes());
    buffer.add("h1".getBytes());
    wait.untilTrue(s::isAssigned);
    
    // capture the original lease expiry so that it can be compared with the extended lease
    final long expiry0 = s.getElection().getLeaseView().getLease(group).getExpiry();
    
    // sleep and then consume messages -- this will eventually extend the lease
    final long sleepTime = 10;
    Thread.sleep(sleepTime);
    final RecordBatch b0 = s.poll(1_000);
    assertEquals(2, b0.size());
    assertArrayEquals("h0".getBytes(), b0.toList().get(0).getData());
    assertArrayEquals("h1".getBytes(), b0.toList().get(1).getData());
    
    // polling would have extended the lease -- check the expiry
    wait.until(() -> {
      final long expiry1 = s.getElection().getLeaseView().getLease(group).getExpiry();
      assertTrue("expiry0=" + expiry0 + ", expiry1=" + expiry1, expiry1 >= expiry0 + sleepTime);
    });
    
    // another publish and poll to test that the least isn't extended due to a large minLeaseExtendInterval
    final long expiry2 = s.getElection().getLeaseView().getLease(group).getExpiry();
    buffer.add("h2".getBytes());
    buffer.add("h3".getBytes());
    Thread.sleep(sleepTime);
    final RecordBatch b1 = s.poll(1_000);
    assertEquals(2, b1.size());
    assertArrayEquals("h2".getBytes(), b1.toList().get(0).getData());
    assertArrayEquals("h3".getBytes(), b1.toList().get(1).getData());
    
    Thread.sleep(10);
    final long expiry3 = s.getElection().getLeaseView().getLease(group).getExpiry();
    assertEquals(expiry3, expiry2);
    
    verifyNoError(eh);
  }
  
  /**
   *  Test confirmation with an illegal offset -- below the minimum allowed.
   */
  @Test(expected=IllegalArgumentException.class)
  public void testConfirmFailureOffsetTooLow() {
    final String stream = "s";
    final String group = randomGroup();
    final int capacity = 10;
    final ExceptionHandler eh = mockExceptionHandler();
    final DefaultSubscriber s = 
        configureSubscriber(new SubscriberConfig()
                            .withGroup(group)
                            .withExceptionHandler(eh)
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream)
                                              .withHeapCapacity(capacity)));
    
    s.confirm(-1);
    verifyNoError(eh);
  }
  
  /**
   *  Test confirmation with an illegal offset -- above the last read offset.
   */
  @Test(expected=IllegalArgumentException.class)
  public void testConfirmFailureOffsetTooHigh() {
    final String stream = "s";
    final String group = randomGroup();
    final int capacity = 10;
    final ExceptionHandler eh = mockExceptionHandler();
    final DefaultSubscriber s = 
        configureSubscriber(new SubscriberConfig()
                            .withGroup(group)
                            .withExceptionHandler(eh)
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream)
                                              .withHeapCapacity(capacity)));
    
    s.confirm(0);
    verifyNoError(eh);
  }
  
  /**
   *  Tries to confirm the last read offset without having read anything from the stream.
   *  Should be quietly ignored.
   */
  @Test
  public void testConfirmLastReadWithoutReading() {
    final String stream = "s";
    final String group = randomGroup();
    final int capacity = 10;
    final ExceptionHandler eh = mockExceptionHandler();
    final DefaultSubscriber s = 
        configureSubscriber(new SubscriberConfig()
                            .withGroup(group)
                            .withExceptionHandler(eh)
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream)
                                              .withHeapCapacity(capacity)));
    
    s.confirm();
    verifyNoError(eh);
  }
  
  /**
   *  Tests the failure of a confirmation when the subscriber isn't the current tenant,
   *  followed by a deactivate which should err internally but stay silent.
   *  
   *  @throws InterruptedException
   */
  @Test
  public void testConfirmFailureAndDeactivateSuppression() throws InterruptedException {
    final String stream = "s";
    final String group = randomGroup();
    final int capacity = 10;
    
    final ExceptionHandler errorHandler = mock(ExceptionHandler.class);
    final DefaultSubscriber s = 
        configureSubscriber(new SubscriberConfig()
                            .withGroup(group)
                            .withExceptionHandler(errorHandler)
                            .withElectionConfig(new ElectionConfig().withScavengeInterval(1))
                            .withMinLeaseExtendInterval(0)
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream)
                                              .withHeapCapacity(capacity)));
    final IMap<String, byte[]> leases = s.getInstance().getMap(Namespace.HAZELQ_META.qualify("lease." + stream));
    final Ringbuffer<byte[]> buffer = s.getInstance().getRingbuffer(Namespace.HAZELQ_STREAM.qualify(stream));
    final IMap<String, Long> offsets = s.getInstance().getMap(Namespace.HAZELQ_META.qualify("offsets." + stream));
    offsets.put(group, -1L);
    
    // write some data so that we can read at least one record (otherwise we can't confirm an offset)
    buffer.add("hello".getBytes());
    wait.untilTrue(s::isAssigned);
    final long expiry0 = s.getElection().getLeaseView().getLease(group).getExpiry();

    Thread.sleep(10);
    final RecordBatch b0 = s.poll(1_000);
    assertEquals(1, b0.size());
    
    // wait until the subscriber has extended its lease
    wait.until(() -> {
      final Lease lease1 = s.getElection().getLeaseView().getLease(group);
      assertNotEquals(new UUID(0, 0), lease1.getTenant());
      final long expiry1 = lease1.getExpiry();
      assertTrue("expiry0=" + expiry0 + ", expiry1=" + expiry1, expiry1 > expiry0);
    });
    
    // forcibly take the lease away and confirm that the subscriber has seen this 
    leases.put(group, Lease.forever(new UUID(0, 0)).pack());
    wait.until(() -> assertFalse(s.isAssigned()));
    
    // schedule a confirmation and verify that it has failed
    s.confirm();
    wait.until(() -> {
      assertFalse(s.isAssigned());
      assertEquals(-1L, (long) offsets.get(group));
      verify(errorHandler).onException(isNotNull(), isNull());
    });
    
    // try deactivating (yield will only happen for a current tenant)
    s.deactivate();
    verifyNoMoreInteractions(errorHandler);
  }
  
  /**
   *  Tests read failure by rigging the ringbuffer to throw an exception when reading.<p>
   *  
   *  When this error is detected, the test is also rigged to evict the subscriber's tenancy from the
   *  lease map, thereby creating a second error when a lease extension is attempted.
   *  
   *  @throws InterruptedException
   */
  @Test
  public void testPollReadFailureAndExtendFailure() throws InterruptedException {
    final String stream = "s";
    final String group = randomGroup();
    final int capacity = 1;
    
    final ExceptionHandler eh = mock(ExceptionHandler.class);
    
    final HazelcastInstance realInstance = newInstance();
    final HazelcastInstance mockInstance = mock(HazelcastInstance.class);
    when(mockInstance.getConfig()).thenReturn(realInstance.getConfig());
    when(mockInstance.getMap(any())).thenAnswer(invocation -> realInstance.getMap(invocation.getArgument(0)));
    final PartitionService partitionService = mock(PartitionService.class);
    when(mockInstance.getPartitionService()).thenReturn(partitionService);
    final Partition partition = mock(Partition.class);
    when(partitionService.getPartition(any())).thenReturn(partition);
    @SuppressWarnings("unchecked")
    final Ringbuffer<Object> ringbuffer = mock(Ringbuffer.class);
    when(mockInstance.getRingbuffer(any())).thenReturn(ringbuffer);
    
    final Exception cause = new Exception("simulated error");
    when(ringbuffer.readManyAsync(anyLong(), anyInt(), anyInt(), any()))
    .thenReturn(new CompletedFuture<>(null, cause, null));
    
    final DefaultSubscriber s = 
        configureSubscriber(mockInstance,
                            new SubscriberConfig()
                            .withGroup(group)
                            .withExceptionHandler(eh)
                            .withElectionConfig(new ElectionConfig().withScavengeInterval(1))
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream)
                                              .withHeapCapacity(capacity)));
    final IMap<String, byte[]> leases = s.getInstance().getMap(Namespace.HAZELQ_META.qualify("lease." + stream));
    final Ringbuffer<byte[]> buffer = s.getInstance().getRingbuffer(Namespace.HAZELQ_STREAM.qualify(stream));
    final IMap<String, Long> offsets = s.getInstance().getMap(Namespace.HAZELQ_META.qualify("offsets." + stream));
    offsets.put(group, -1L);
    
    buffer.add("h0".getBytes());
    buffer.add("h1".getBytes());
    wait.untilTrue(s::isAssigned);
    
    // when an error occurs, forcibly reassign the lease
    doAnswer(invocation -> leases.put(group, Lease.forever(UUID.randomUUID()).pack()))
    .when(eh).onException(any(), any());
    
    // simulate an error by reading stale items
    Thread.sleep(10);
    final RecordBatch b = s.poll(1_000);
    assertEquals(0, b.size());
    
    // there should be two errors -- the first for the stale read, the second for the failed lease extension
    wait.until(() -> verify(eh).onException(isNotNull(), eq(cause)));
    wait.until(() -> verify(eh).onException(isNotNull(), any(NotTenantException.class)));
  }
  
  /**
   *  Tests the {@link InitialOffsetScheme#EARLIEST} offset initialisation.
   *  
   *  @throws InterruptedException
   */
  @Test
  public void testInitialOffsetEarliest() throws InterruptedException {
    final String stream = "s";
    final String group = randomGroup();
    final int capacity = 10;
    
    final HazelcastInstance instance = newInstance();
    final Ringbuffer<byte[]> buffer = instance.getRingbuffer(Namespace.HAZELQ_STREAM.qualify(stream));
    buffer.add("h0".getBytes());
    buffer.add("h1".getBytes());

    final ExceptionHandler eh = mockExceptionHandler();
    final DefaultSubscriber s = 
        configureSubscriber(instance,
                            new SubscriberConfig()
                            .withGroup(group)
                            .withExceptionHandler(eh)
                            .withInitialOffsetScheme(InitialOffsetScheme.EARLIEST)
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream)
                                              .withHeapCapacity(capacity)));
    wait.untilTrue(s::isAssigned);
    
    final RecordBatch b = s.poll(1_000);
    assertEquals(2, b.size());
    
    verifyNoError(eh);
  }
  
  /**
   *  Tests the {@link InitialOffsetScheme#LATEST} offset initialisation.
   *  
   *  @throws InterruptedException
   */
  @Test
  public void testInitialOffsetLatest() throws InterruptedException {
    final String stream = "s";
    final String group = randomGroup();
    final int capacity = 10;
    
    final HazelcastInstance instance = newInstance();
    final Ringbuffer<byte[]> buffer = instance.getRingbuffer(Namespace.HAZELQ_STREAM.qualify(stream));
    buffer.add("h0".getBytes());
    buffer.add("h1".getBytes());

    final ExceptionHandler eh = mockExceptionHandler();
    final DefaultSubscriber s = 
        configureSubscriber(instance,
                            new SubscriberConfig()
                            .withGroup(group)
                            .withExceptionHandler(eh)
                            .withInitialOffsetScheme(InitialOffsetScheme.LATEST)
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream)
                                              .withHeapCapacity(capacity)));
    wait.untilTrue(s::isAssigned);
    
    final RecordBatch b = s.poll(10);
    assertEquals(0, b.size());
    
    verifyNoError(eh);
  }
  
  /**
   *  Tests the {@link InitialOffsetScheme#NONE} offset initialisation. Because no offset
   *  has been written to the offsets map, this operation will fail.
   *  
   *  @throws InterruptedException
   */
  @Test(expected=OffsetLoadException.class)
  public void testInitialOffsetNone() throws InterruptedException {
    final String stream = "s";
    final String group = randomGroup();
    final int capacity = 10;
    
    final ExceptionHandler eh = mockExceptionHandler();
    configureSubscriber(new SubscriberConfig()
                        .withGroup(group)
                        .withInitialOffsetScheme(InitialOffsetScheme.NONE)
                        .withExceptionHandler(eh)
                        .withStreamConfig(new StreamConfig()
                                          .withName(stream)
                                          .withHeapCapacity(capacity)));
    verifyNoError(eh);
  }

  /**
   *  Tests two subscribers competing for the same stream. Deactivation and reactivation is used
   *  to test lease reassignment and verify that one subscriber can continue where the other has
   *  left off.
   *  
   *  @throws InterruptedException
   */
  @Test
  public void testTwoSubscribersWithActivation() throws InterruptedException {
    final String stream = "s";
    final String group = randomGroup();
    final int capacity = 10;

    final InstancePool instancePool = new InstancePool(2, this::newInstance);
    instancePool.prestartAll();
    final HazelcastInstance instance0 = instancePool.get();
    final HazelcastInstance instance1 = instancePool.get();
    final Ringbuffer<byte[]> buffer = instance0.getRingbuffer(Namespace.HAZELQ_STREAM.qualify(stream));
    final IMap<String, Long> offsets = instance0.getMap(Namespace.HAZELQ_META.qualify("offsets." + stream));
    offsets.put(group, -1L);
    
    final ExceptionHandler eh0 = mockExceptionHandler();
    final DefaultSubscriber s0 = 
        configureSubscriber(instance0,
                            new SubscriberConfig()
                            .withGroup(group)
                            .withExceptionHandler(eh0)
                            .withElectionConfig(new ElectionConfig().withScavengeInterval(1))
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream)
                                              .withHeapCapacity(capacity)));
    
    buffer.add("h0".getBytes());
    buffer.add("h1".getBytes());
    wait.untilTrue(s0::isAssigned);
    
    final ExceptionHandler eh1 = mockExceptionHandler();
    final DefaultSubscriber s1 = 
        configureSubscriber(instance1,
                            new SubscriberConfig()
                            .withGroup(group)
                            .withExceptionHandler(eh1)
                            .withElectionConfig(new ElectionConfig().withScavengeInterval(1))
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream)
                                              .withHeapCapacity(capacity)));
    Thread.sleep(10);
    assertFalse(s1.isAssigned()); // s0 is the tenant

    // consume from s0 -- should succeed
    final RecordBatch s0_b0 = s0.poll(1_000);
    assertEquals(2, s0_b0.size());
    assertArrayEquals("h0".getBytes(), s0_b0.toList().get(0).getData());
    assertArrayEquals("h1".getBytes(), s0_b0.toList().get(1).getData());
    
    // confirm offsets and check in the map
    s0.confirm();
    wait.until(() -> assertEquals(1, (long) offsets.get(group)));
    
    // consume from s1 -- should return an empty batch
    final RecordBatch s1_b0 = s0.poll(10);
    assertEquals(0, s1_b0.size());
    
    // publish more records -- should only be available to s0
    buffer.add("h2".getBytes());
    buffer.add("h3".getBytes());

    final RecordBatch s0_b1 = s0.poll(1_000);
    assertEquals(2, s0_b1.size());
    assertArrayEquals("h2".getBytes(), s0_b1.toList().get(0).getData());
    assertArrayEquals("h3".getBytes(), s0_b1.toList().get(1).getData());

    final RecordBatch s1_b1 = s1.poll(10);
    assertEquals(0, s1_b1.size());
    
    // deactivate s0 -- it will eventually lose the ability to consume messages
    s0.deactivate();
    wait.until(() -> assertFalse(s0.isAssigned()));
    final RecordBatch s0_b2 = s0.poll(10);
    assertEquals(0, s0_b2.size());
    
    // s1 will eventually take tenancy
    wait.untilTrue(s1::isAssigned);
    
    // consuming now from s1 should fetch the last two records (the first two were already confirmed)
    final RecordBatch s1_b2 = s1.poll(1_000);
    assertEquals(2, s1_b2.size());
    assertArrayEquals("h2".getBytes(), s1_b2.toList().get(0).getData());
    assertArrayEquals("h3".getBytes(), s1_b2.toList().get(1).getData());
    
    // confirm h2 only and switch tenancy back to s0
    s1.confirm(2);
    wait.until(() -> assertEquals(2, (long) offsets.get(group)));
    s1.deactivate();
    s0.reactivate();
    
    // consuming now from s0 should only fetch h3, as h2 was confirmed
    wait.until(() -> {
      final RecordBatch s0_b3;
      try {
        s0_b3 = s0.poll(1);
      } catch (InterruptedException e) { return; }
      assertEquals(1, s0_b3.size());
      assertArrayEquals("h3".getBytes(), s0_b3.toList().get(0).getData());
    });
    
    wait.until(() -> assertFalse(s1.isAssigned()));
    verifyNoError(eh0, eh1);
  }
  
  /**
   *  Tests two subscribers using two separate groups. Each subscriber will receive the
   *  same messages.
   *  
   *  @throws InterruptedException
   */
  @Test
  public void testTwoSubscribersTwoGroups() throws InterruptedException {
    final String stream = "s";
    final String group0 = randomGroup();
    final String group1 = randomGroup();
    final int capacity = 10;

    final InstancePool instancePool = new InstancePool(2, this::newInstance);
    instancePool.prestartAll();
    final HazelcastInstance instance0 = instancePool.get();
    final HazelcastInstance instance1 = instancePool.get();
    final Ringbuffer<byte[]> buffer = instance0.getRingbuffer(Namespace.HAZELQ_STREAM.qualify(stream));
    final IMap<String, Long> offsets = instance0.getMap(Namespace.HAZELQ_META.qualify("offsets." + stream));
    offsets.put(group0, -1L);
    offsets.put(group1, -1L);
    
    final ExceptionHandler eh0 = mockExceptionHandler();
    final DefaultSubscriber s0 = 
        configureSubscriber(instance0,
                            new SubscriberConfig()
                            .withGroup(group0)
                            .withExceptionHandler(eh0)
                            .withElectionConfig(new ElectionConfig().withScavengeInterval(1))
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream)
                                              .withHeapCapacity(capacity)));
    final ExceptionHandler eh1 = mockExceptionHandler();
    final DefaultSubscriber s1 = 
        configureSubscriber(instance1,
                            new SubscriberConfig()
                            .withGroup(group1)
                            .withExceptionHandler(eh1)
                            .withElectionConfig(new ElectionConfig().withScavengeInterval(1))
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream)
                                              .withHeapCapacity(capacity)));
    
    buffer.add("h0".getBytes());
    buffer.add("h1".getBytes());
    wait.untilTrue(s0::isAssigned);
    wait.untilTrue(s1::isAssigned);

    // consume from s0 and s1, and verify that both received the same messages
    final RecordBatch s0_b0 = s0.poll(1_000);
    assertEquals(2, s0_b0.size());
    assertArrayEquals("h0".getBytes(), s0_b0.toList().get(0).getData());
    assertArrayEquals("h1".getBytes(), s0_b0.toList().get(1).getData());

    final RecordBatch s1_b0 = s1.poll(1_000);
    assertEquals(2, s1_b0.size());
    assertArrayEquals("h0".getBytes(), s1_b0.toList().get(0).getData());
    assertArrayEquals("h1".getBytes(), s1_b0.toList().get(1).getData());
    
    // consume again from s0 and s1 should result in an empty batch
    final RecordBatch s0_b1 = s0.poll(10);
    assertEquals(0, s0_b1.size());
    
    final RecordBatch s1_b1 = s1.poll(10);
    assertEquals(0, s1_b1.size());
    
    verifyNoError(eh0, eh1);
  }
  
  /**
   *  Tests two subscribers with two separate streams but using identical group IDs. Each subscriber will 
   *  receive messages from its own stream -- there are no interactions among groups for different streams.
   *  
   *  @throws InterruptedException
   */
  @Test
  public void testTwoSubscribersTwoStreams() throws InterruptedException {
    final String stream0 = "s";
    final String stream1 = "s1";
    final String group = randomGroup();
    final int capacity = 10;

    final InstancePool instancePool = new InstancePool(2, this::newInstance);
    instancePool.prestartAll();
    final HazelcastInstance instance0 = instancePool.get();
    final HazelcastInstance instance1 = instancePool.get();
    final Ringbuffer<byte[]> buffer0 = instance0.getRingbuffer(Namespace.HAZELQ_STREAM.qualify(stream0));
    final Ringbuffer<byte[]> buffer1 = instance1.getRingbuffer(Namespace.HAZELQ_STREAM.qualify(stream1));
    
    final ExceptionHandler eh0 = mockExceptionHandler();
    final DefaultSubscriber s0 = 
        configureSubscriber(instance0,
                            new SubscriberConfig()
                            .withGroup(group)
                            .withExceptionHandler(eh0)
                            .withElectionConfig(new ElectionConfig().withScavengeInterval(1))
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream0)
                                              .withHeapCapacity(capacity)));

    final ExceptionHandler eh1 = mockExceptionHandler();
    final DefaultSubscriber s1 = 
        configureSubscriber(instance1,
                            new SubscriberConfig()
                            .withGroup(group)
                            .withExceptionHandler(eh1)
                            .withElectionConfig(new ElectionConfig().withScavengeInterval(1))
                            .withStreamConfig(new StreamConfig()
                                              .withName(stream1)
                                              .withHeapCapacity(capacity)));
    // both subscribers should assume leadership -- each for its own stream
    wait.untilTrue(s0::isAssigned);
    wait.untilTrue(s1::isAssigned);

    // publish to s0's stream buffer
    buffer0.add("s0h0".getBytes());
    buffer0.add("s0h1".getBytes());
    
    // consume from s0
    final RecordBatch s0_b0 = s0.poll(1_000);
    assertEquals(2, s0_b0.size());
    assertArrayEquals("s0h0".getBytes(), s0_b0.toList().get(0).getData());
    assertArrayEquals("s0h1".getBytes(), s0_b0.toList().get(1).getData());
    
    // consumer again from s0 -- should get an empty batch
    final RecordBatch s0_b1 = s0.poll(10);
    assertEquals(0, s0_b1.size());
    
    // publish to s1's stream buffer
    buffer1.add("s1h0".getBytes());
    buffer1.add("s1h1".getBytes());

    // consume from s1
    final RecordBatch s1_b0 = s1.poll(1_000);
    assertEquals(2, s1_b0.size());
    assertArrayEquals("s1h0".getBytes(), s1_b0.toList().get(0).getData());
    assertArrayEquals("s1h1".getBytes(), s1_b0.toList().get(1).getData());
    
    // consumer again from s1 -- should get an empty batch
    final RecordBatch s1_b1 = s1.poll(10);
    assertEquals(0, s1_b1.size());
    
    verifyNoError(eh0, eh1);
  }
}