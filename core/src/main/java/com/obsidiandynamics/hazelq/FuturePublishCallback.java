package com.obsidiandynamics.hazelq;

import java.util.concurrent.*;

public final class FuturePublishCallback extends CompletableFuture<Long> implements PublishCallback {
  @Override
  public void onComplete(long offset, Throwable error) {
    if (error != null) {
      completeExceptionally(error);
    } else {
      complete(offset);
    }
  }
}
