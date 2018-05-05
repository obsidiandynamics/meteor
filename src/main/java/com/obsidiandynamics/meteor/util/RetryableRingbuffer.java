package com.obsidiandynamics.meteor.util;

import java.util.*;

import com.hazelcast.core.*;
import com.hazelcast.ringbuffer.*;
import com.obsidiandynamics.retry.*;

public final class RetryableRingbuffer<E> {
  private final Retry retry;
  
  private final Ringbuffer<E> ringbuffer;

  public RetryableRingbuffer(Retry retry, Ringbuffer<E> ringbuffer) {
    this.retry = retry;
    this.ringbuffer = ringbuffer;
  }
  
  public Ringbuffer<E> getRingbuffer() {
    return ringbuffer;
  }
  
  public ICompletableFuture<Long> addAllAsync(Collection<? extends E> collection, OverflowPolicy overflowPolicy) {
    return retry.run(() -> ringbuffer.addAllAsync(collection, overflowPolicy));
  }
  
  public long add(E item) {
    return retry.run(() -> ringbuffer.add(item));
  }

  public ICompletableFuture<ReadResultSet<E>> readManyAsync(long startSequence, int minCount, int maxCount, IFunction<E, Boolean> filter) {
    return retry.run(() -> ringbuffer.readManyAsync(startSequence, minCount, maxCount, filter));
  }

  public long tailSequence() {
    return retry.run(() -> ringbuffer.tailSequence());
  }
}
