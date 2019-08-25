package com.obsidiandynamics.meteor;

import java.util.*;

final class LeaseViewImpl implements LeaseView {
  private final Map<String, Lease> leases;
  
  private final long version;
  
  LeaseViewImpl(long version) {
    this.version = version;
    leases = new HashMap<>();
  }
  
  LeaseViewImpl(LeaseViewImpl original, long version) {
    this.version = version;
    leases = new HashMap<>(original.leases);
  }

  @Override
  public Map<String, Lease> asMap() {
    return this.leases;
  }
  
  @Override
  public long getVersion() {
    return version;
  }
  
  @Override
  public String toString() {
    return super.toString() + "@" + version;
  }

  public Lease put(String key, Lease lease) {
    return leases.put(key, lease);
  }

  public Lease getOrDefault(String resource, Lease vacant) {
    return leases.getOrDefault(resource, vacant);
  }
}
