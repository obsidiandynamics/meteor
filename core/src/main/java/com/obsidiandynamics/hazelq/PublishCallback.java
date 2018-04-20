package com.obsidiandynamics.hazelq;

@FunctionalInterface
public interface PublishCallback {
  static PublishCallback nop() { return (offset, error) -> {}; }
  
  void onComplete(long offset, Throwable error);
}
