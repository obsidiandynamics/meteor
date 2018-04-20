package com.obsidiandynamics.hazelq;

import java.util.*;

interface ScavengeWatcher {
  static ScavengeWatcher nop = new ScavengeWatcher() {
    @Override public void onExpire(String resource, UUID tenant) {}
    @Override public void onAssign(String resource, UUID tenant) {}
  };
  
  static ScavengeWatcher nop() { return nop; }
  
  void onAssign(String resource, UUID tenant);

  void onExpire(String resource, UUID tenant);
}
