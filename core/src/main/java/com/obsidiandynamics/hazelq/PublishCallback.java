package com.obsidiandynamics.hazelq;

@FunctionalInterface
public interface PublishCallback {
  static PublishCallback nop() { return (__offset, __error) -> {}; }
  
  void onComplete(long offset, Throwable error);
}
