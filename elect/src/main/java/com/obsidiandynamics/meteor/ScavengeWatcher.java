package com.obsidiandynamics.meteor;

import static com.obsidiandynamics.func.Functions.*;

import java.util.*;

interface ScavengeWatcher {
  ScavengeWatcher nop = new ScavengeWatcher() {
    @Override 
    public void onExpire(String resource, UUID tenant) {
      mustExist(resource);
      mustExist(tenant);
    }
    
    @Override 
    public void onAssign(String resource, UUID tenant) {
      mustExist(resource);
      mustExist(tenant);
    }
  };
  
  static ScavengeWatcher nop() { return nop; }
  
  void onAssign(String resource, UUID tenant);

  void onExpire(String resource, UUID tenant);
}
