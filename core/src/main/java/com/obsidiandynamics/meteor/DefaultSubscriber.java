package com.obsidiandynamics.meteor;

import static com.obsidiandynamics.retry.Retry.*;

import java.util.*;
import java.util.concurrent.*;

import com.hazelcast.core.*;
import com.hazelcast.ringbuffer.*;
import com.obsidiandynamics.func.*;
import com.obsidiandynamics.meteor.Receiver.*;
import com.obsidiandynamics.meteor.util.*;
import com.obsidiandynamics.retry.*;
import com.obsidiandynamics.worker.*;
import com.obsidiandynamics.worker.Terminator;

public final class DefaultSubscriber implements Subscriber, Joinable {
  /** Cycle backoff for the keeper thread. */
  private static final int KEEPER_BACKOFF_MILLIS = 1;

  /** The maximum amount of time an unassigned subscriber is allowed to sleep before re-checking the
   *  assignment status. */
  private static final int MAX_UNASSIGNED_SLEEP_MILLIS = 10;
  
  private final HazelcastInstance instance;
  
  private final SubscriberConfig config;
  
  private final RetryableRingbuffer<byte[]> buffer;
  
  private final RetryableMap<String, Long> offsets;
  
  private final Election election;
  
  private final UUID leaseCandidate;
  
  private final WorkerThread keeperThread;
  
  private final int readBatchSize;
  
  private volatile long nextReadOffset;
  
  private volatile long lastReadOffset;
  
  private volatile long scheduledConfirmOffset = Record.UNASSIGNED_OFFSET;
  
  private long lastConfirmedOffset = scheduledConfirmOffset;
  
  private volatile long scheduledExtendTimestamp = 0;
  
  private long lastExtendTimestamp = scheduledExtendTimestamp;
  
  private boolean active = true;
  
  private final Object activeLock = new Object();
  
  private volatile Receiver receiver;
  
  DefaultSubscriber(HazelcastInstance instance, SubscriberConfig config) {
    this.instance = instance;
    this.config = config;
    
    final StreamConfig streamConfig = config.getStreamConfig();
    final Retry retry = new Retry()
        .withExceptionMatcher(isA(HazelcastException.class))
        .withAttempts(Integer.MAX_VALUE)
        .withBackoff(100)
        .withFaultHandler(config.getZlg()::w)
        .withErrorHandler(config.getZlg()::e);
    buffer = new RetryableRingbuffer<>(retry, StreamHelper.getRingbuffer(instance, streamConfig));
    readBatchSize = Math.min(1_000, streamConfig.getHeapCapacity());
    
    if (config.hasGroup()) {
      // checks for IllegalArgumentException; no initial assignment is made until poll() is called
      getInitialOffset(true);
      nextReadOffset = Record.UNASSIGNED_OFFSET;
      
      offsets = new RetryableMap<>(retry, StreamHelper.getOffsetsMap(instance, streamConfig, config.getMapStoreConfig()));
      
      final IMap<String, byte[]> leases = StreamHelper.getLeaseMap(instance, streamConfig, config.getMapStoreConfig());
      leaseCandidate = UUID.randomUUID();
      election = new Election(config.getElectionConfig(), leases);
      election.getRegistry().enrol(config.getGroup(), leaseCandidate);
      election.start();
      
      keeperThread = WorkerThread.builder()
          .withOptions(new WorkerOptions().daemon().withName(Subscriber.class, streamConfig.getName(), "keeper"))
          .onCycle(this::keeperCycle)
          .buildAndStart();
    } else {
      if (config.getInitialOffsetScheme() == InitialOffsetScheme.NONE) {
        throw new InvalidInitialOffsetSchemeException("Cannot use initial offset scheme " + InitialOffsetScheme.NONE + 
                                                      " in an ungrouped context");
      }
      // performs initial offset assignment
      nextReadOffset = getInitialOffset(false);
      offsets = null;
      election = null;
      leaseCandidate = null;
      keeperThread = null;
    }
    lastReadOffset = nextReadOffset - 1;
  }
  
  @Override
  public SubscriberConfig getConfig() {
    return config;
  }
  
  HazelcastInstance getInstance() {
    return instance;
  }
  
  Election getElection() {
    return election;
  }
  
  private String getServiceInfo(DistributedObject obj) {
    final Partition partition = instance.getPartitionService().getPartition(obj.getPartitionKey());
    return String.format("serviceName=%s, partitionId=%d, owner=%s", 
                         obj.getServiceName(), partition.getPartitionId(), partition.getOwner());
  }
  
  private static final long computeWait(long wakeTime, long maxSleepMillis) {
    return Math.min(Math.max(0, wakeTime - System.currentTimeMillis()), maxSleepMillis);
  }

  @Override
  public RecordBatch poll(long timeoutMillis) throws InterruptedException {
    final boolean isGroupSubscriber = leaseCandidate != null;
    
    final long wake = System.currentTimeMillis() + timeoutMillis;
    for (;;) {
      final boolean isCurrentTenant = isGroupSubscriber && isCurrentTenant();
      
      if (! isGroupSubscriber || isCurrentTenant) {
        if (nextReadOffset == Record.UNASSIGNED_OFFSET) {
          nextReadOffset = loadConfirmedOffset() + 1;
          lastReadOffset = nextReadOffset - 1;
        }
      
        final ICompletableFuture<ReadResultSet<byte[]>> f = buffer.readManyAsync(nextReadOffset, 1, readBatchSize, StreamHelper::isNotNull);
        
        final long waitMillis = computeWait(wake, Long.MAX_VALUE);
        try {
          final ReadResultSet<byte[]> resultSet = f.get(waitMillis, TimeUnit.MILLISECONDS);
          lastReadOffset = resultSet.getSequence(resultSet.size() - 1);
          nextReadOffset = lastReadOffset + 1;
          return readBatch(resultSet);
        } catch (ExecutionException e) {
          if (e.getCause() instanceof StaleSequenceException) {
            // if a stale sequence exception is encountered, fast-forward the sequence to the last known head
            // sequence and add a further 'safety margin' so that the next read doesn't hit another stale offset
            final double safetyMarginFrac = config.getStaleReadSafetyMargin();
            final long headSeq = ((StaleSequenceException) e.getCause()).getHeadSeq();
            final long safetyMargin = (long) (config.getStreamConfig().getHeapCapacity() * safetyMarginFrac);
            final long ffNextReadOffset = headSeq + safetyMargin;
            config.getZlg().w("Sequence %,d was stale (head already at %,d), fast-forwarding to %,d", 
                              z -> z.arg(nextReadOffset).arg(headSeq).arg(ffNextReadOffset));
            nextReadOffset = ffNextReadOffset;
          } else {
            final String serviceInfo = getServiceInfo(buffer.getRingbuffer());
            final String m = String.format("Error reading at offset %,d from stream %s [%s]",
                                           nextReadOffset, config.getStreamConfig().getName(), serviceInfo);
            config.getExceptionHandler().onException(m, e.getCause());
            f.cancel(true);
            Thread.sleep(waitMillis);
            return RecordBatch.empty();
          }
        } catch (TimeoutException e) {
          f.cancel(true);
          return RecordBatch.empty();
        } finally {
          if (isCurrentTenant) {
            scheduledExtendTimestamp = System.currentTimeMillis();
          }
        }
      } else {
        nextReadOffset = Record.UNASSIGNED_OFFSET;
        final long sleepMillis = computeWait(wake, MAX_UNASSIGNED_SLEEP_MILLIS);
        if (sleepMillis > 0) {
          Thread.sleep(sleepMillis);
        } else {
          return RecordBatch.empty();
        }
      }
    }
  }
  
  private long loadConfirmedOffset() {
    final Long confirmedOffset = offsets.get(config.getGroup());
    if (confirmedOffset != null) {
      return confirmedOffset;
    } else {
      return getInitialOffset(true) - 1;
    }
  }
  
  private long getInitialOffset(boolean useGroups) {
    // resolve AUTO to the appropriate scheme (EARLIEST/LATEST/NONE) depending on group mode
    final InitialOffsetScheme concreteInitialOffsetScheme = 
        config.getInitialOffsetScheme().resolveConcreteScheme(useGroups);
    if (concreteInitialOffsetScheme == InitialOffsetScheme.EARLIEST) {
      return 0;
    } else if (concreteInitialOffsetScheme == InitialOffsetScheme.LATEST) {
      return buffer.tailSequence() + 1;
    } else {
      throw new OffsetLoadException("No persisted offset");
    }
  }
  
  private static RecordBatch readBatch(ReadResultSet<byte[]> resultSet) {
    final List<Record> records = new ArrayList<>(resultSet.size());
    long offset = resultSet.getSequence(0);
    for (byte[] result : resultSet) {
      records.add(new Record(result, offset++));
    }
    return new ListRecordBatch(records);
  }
  
  private void ensureGroupMode() {
    if (leaseCandidate == null) {
      throw new IllegalStateException("Cannot call this operation in an ungrouped context");
    }
  }
  
  private void ensureGroupFreeMode() {
    if (leaseCandidate != null) {
      throw new IllegalStateException("Cannot call this operation in a grouped context");
    }
  }
  
  @Override
  public void confirm() {
    ensureGroupMode();
    
    if (lastReadOffset >= StreamHelper.SMALLEST_OFFSET) {
      confirm(lastReadOffset);
    }
  }

  @Override
  public void confirm(long offset) {
    ensureGroupMode();
    
    if (offset < StreamHelper.SMALLEST_OFFSET || offset > lastReadOffset) {
      throw new IllegalArgumentException(String.format("Illegal offset %d; last read %d", offset, lastReadOffset));
    }
    
    scheduledConfirmOffset = offset;
  }
  
  @Override
  public void seek(long offset) {
    ensureGroupFreeMode();
    
    if (offset < StreamHelper.SMALLEST_OFFSET) throw new IllegalArgumentException("Invalid seek offset " + offset);
    nextReadOffset = offset;
  }
  
  private void keeperCycle(WorkerThread t) throws InterruptedException {
    final long scheduledConfirmOffset = this.scheduledConfirmOffset;
    final long scheduledExtendTimestamp = this.scheduledExtendTimestamp;
    
    boolean performedWork = false;
    synchronized (activeLock) {
      if (active) {
        if (scheduledConfirmOffset != lastConfirmedOffset) {
          performedWork = true;
          putOffset(scheduledConfirmOffset);
          lastConfirmedOffset = scheduledConfirmOffset;
        }
        
        if (scheduledExtendTimestamp != lastExtendTimestamp) {
          final long timeSinceLastExtend = System.currentTimeMillis() - lastExtendTimestamp;
          if (timeSinceLastExtend >= config.getMinLeaseExtendInterval()) {
            performedWork = true;
            extendLease(scheduledExtendTimestamp);
            lastExtendTimestamp = scheduledExtendTimestamp;
          }
        }
      } else {
        // avoid confirming offsets or extending the lease if this subscriber has been deactivated,
        // but update the timestamps to thwart future attempts
        lastConfirmedOffset = scheduledConfirmOffset;
        lastExtendTimestamp = scheduledExtendTimestamp;
      }
    }
    
    if (! performedWork) {
      Thread.sleep(KEEPER_BACKOFF_MILLIS);
    }
  }
  
  private void putOffset(long offset) {
    if (isCurrentTenant()) {
      doWithExceptionHandler(() -> offsets.put(config.getGroup(), offset), 
                         config.getExceptionHandler(), 
                         "Failed to update offset");
    } else {
      final String m = String.format("Failed confirming offset %s for stream %s: %s is not the current tenant for group %s",
                                     offset, config.getStreamConfig().getName(), leaseCandidate, config.getGroup());
      config.getExceptionHandler().onException(m, null);
    }
  }
  
  private void extendLease(long timestamp) {
    doWithExceptionHandler(() -> election.extend(config.getGroup(), leaseCandidate), 
                       config.getExceptionHandler(), 
                       "Failed to extend lease");
  }
  
  private boolean isCurrentTenant() {
    return election.getLeaseView().isCurrentTenant(config.getGroup(), leaseCandidate);
  }
  
  @Override
  public boolean isAssigned() {
    return leaseCandidate == null || isCurrentTenant();
  }
  
  @Override
  public void deactivate() {
    deactivate(config.getExceptionHandler());
  }
  
  private void deactivate(ExceptionHandler errorHandler) {
    ensureGroupMode();
    
    synchronized (activeLock) {
      election.getRegistry().unenrol(config.getGroup(), leaseCandidate);
      if (isCurrentTenant()) {
        doWithExceptionHandler(() -> election.yield(config.getGroup(), leaseCandidate), 
                           errorHandler, 
                           "Failed to yield lease");
      }
      active = false;
    }
  }
  
  private static void doWithExceptionHandler(CheckedRunnable<?> r, ExceptionHandler errorHandler, String message) {
    try {
      r.run();
    } catch (Throwable e) {
      errorHandler.onException(message, e);
    }
  }
  
  @Override
  public void reactivate() {
    ensureGroupMode();
    
    synchronized (activeLock) {
      election.getRegistry().enrol(config.getGroup(), leaseCandidate);
      active = true;
    }
  }

  @Override
  public Receiver attachReceiver(RecordHandler recordHandler, int pollTimeoutMillis) {
    if (receiver != null) {
      throw new IllegalStateException("A receiver has already been attached");
    }
    
    return receiver = new DefaultReceiver(this, recordHandler, pollTimeoutMillis);
  }

  @Override
  public Joinable terminate() {
    if (leaseCandidate != null) {
      deactivate(ExceptionHandler.nop());
    }
    
    Terminator.blank()
    .add(Optional.ofNullable(receiver))
    .add(Optional.ofNullable(keeperThread))
    .add(Optional.ofNullable(election))
    .terminate();
    return this;
  }

  @Override
  public boolean join(long timeoutMillis) throws InterruptedException {
    return Joiner.blank()
    .add(Optional.ofNullable(receiver))
    .add(Optional.ofNullable(keeperThread))
    .add(Optional.ofNullable(election))
    .join(timeoutMillis);
  }
}
