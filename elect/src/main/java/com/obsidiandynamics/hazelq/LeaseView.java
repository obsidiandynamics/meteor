package com.obsidiandynamics.hazelq;

import java.util.*;

public interface LeaseView {
  Map<String, Lease> asMap();
  
  long getVersion();
  
  default Lease getLease(String resource) {
    return asMap().getOrDefault(resource, Lease.vacant());
  }
  
  default boolean isCurrentTenant(String resource, UUID candidate) {
    return getLease(resource).isHeldByAndCurrent(candidate);
  }
}
