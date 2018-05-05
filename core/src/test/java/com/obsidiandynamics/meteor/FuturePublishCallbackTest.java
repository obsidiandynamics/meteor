package com.obsidiandynamics.meteor;

import static org.junit.Assert.*;

import java.util.concurrent.*;

import org.junit.*;

public final class FuturePublishCallbackTest {
  @Test
  public void testWithSuccess() throws InterruptedException, ExecutionException {
    final FuturePublishCallback f = new FuturePublishCallback();
    f.onComplete(10, null);
    assertEquals(10, (long) f.get());
  }

  @Test(expected=ExecutionException.class)
  public void testWithError() throws InterruptedException, ExecutionException {
    final FuturePublishCallback f = new FuturePublishCallback();
    f.onComplete(Record.UNASSIGNED_OFFSET, new Exception("simulated error"));
    f.get();
  }
}
