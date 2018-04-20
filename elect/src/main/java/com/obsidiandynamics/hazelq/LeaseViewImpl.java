package com.obsidiandynamics.hazelq;

import java.util.*;

final class LeaseViewImpl extends HashMap<String, Lease> implements LeaseView {
  private static final long serialVersionUID = 1L;
  
  private final long version;
  
  LeaseViewImpl(long version) {
    this.version = version;
  }
  
  LeaseViewImpl(LeaseViewImpl original, long version) {
    super(original);
    this.version = version;
  }

  @Override
  public Map<String, Lease> asMap() {
    return this;
  }
  
  @Override
  public long getVersion() {
    return version;
  }
  
  @Override
  public String toString() {
    return super.toString() + "@" + version;
  }
}
