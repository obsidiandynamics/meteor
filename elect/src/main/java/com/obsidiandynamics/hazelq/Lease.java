package com.obsidiandynamics.hazelq;

import java.nio.*;
import java.text.*;
import java.util.*;

import org.apache.commons.lang3.builder.*;

public final class Lease {
  private static final Lease vacant = new Lease(null, 0);
  
  public static Lease vacant() { return vacant; }
  
  private final UUID tenant;
  
  private final long expiry;

  Lease(UUID tenant, long expiry) {
    this.tenant = tenant;
    this.expiry = expiry;
  }
  
  public boolean isVacant() {
    return tenant == null;
  }
  
  public boolean isHeldBy(UUID candidate) {
    return candidate.equals(tenant);
  }
  
  public boolean isHeldByAndCurrent(UUID candidate) {
    return isHeldBy(candidate) && isCurrent();
  }
  
  public UUID getTenant() {
    return tenant;
  }

  public long getExpiry() {
    return expiry;
  }
  
  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(tenant).append(expiry).hashCode();
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (obj instanceof Lease) {
      final Lease that = (Lease) obj;
      return new EqualsBuilder().append(tenant, that.tenant).append(expiry, that.expiry).isEquals();
    } else {
      return false;
    }
  }
  
  public boolean isCurrent() {
    return expiry != 0 && System.currentTimeMillis() <= expiry;
  }

  @Override
  public String toString() {
    return Lease.class.getSimpleName() + " [tenant=" + tenant + ", expiry=" + formatExpiry(expiry) + "]";
  }
  
  public static String formatExpiry(long expiry) {
    if (expiry == Long.MAX_VALUE) {
      return "eschaton";
    } else if (expiry == 0) {
      return "epoch";
    } else {
      return new SimpleDateFormat("MMM dd HH:mm:ss.SSS zzz yyyy").format(new Date(expiry));
    }
  }
  
  public byte[] pack() {
    final ByteBuffer buf = ByteBuffer.allocate(24);
    buf.putLong(tenant.getMostSignificantBits());
    buf.putLong(tenant.getLeastSignificantBits());
    buf.putLong(expiry);
    return buf.array();
  }
  
  public static Lease unpack(byte[] bytes) {
    final ByteBuffer buf = ByteBuffer.wrap(bytes);
    final UUID tenant = new UUID(buf.getLong(), buf.getLong());
    final long expiry = buf.getLong();
    return new Lease(tenant, expiry);
  }
  
  public static Lease forever(UUID tenant) {
    return new Lease(tenant, Long.MAX_VALUE);
  }
  
  public static Lease expired(UUID tenant) {
    return new Lease(tenant, 0);
  }
}
