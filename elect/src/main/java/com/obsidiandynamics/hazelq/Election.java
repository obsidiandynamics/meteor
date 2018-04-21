package com.obsidiandynamics.hazelq;

import java.util.*;

import com.hazelcast.core.*;
import com.obsidiandynamics.hazelq.util.*;
import com.obsidiandynamics.retry.*;
import com.obsidiandynamics.worker.*;
import com.obsidiandynamics.zerolog.*;

public final class Election implements Terminable, Joinable {
  private static final Zlg zlg = Zlg.forDeclaringClass().get();
  
  private final ElectionConfig config;
  
  private final RetryableMap<String, byte[]> leases;
  
  private final Registry registry;
  
  private final WorkerThread scavengerThread;
  
  private final Object scavengeLock = new Object();
  
  private final Object viewLock = new Object();
  
  private ScavengeWatcher scavengeWatcher = ScavengeWatcher.nop();
  
  private volatile LeaseViewImpl leaseView = new LeaseViewImpl(0);
  
  private long nextViewVersion = 1;
  
  public Election(ElectionConfig config, IMap<String, byte[]> leases) {
    this.config = config;

    final Retry retry = new Retry()
        .withExceptionClass(HazelcastException.class)
        .withAttempts(Integer.MAX_VALUE)
        .withBackoff(100)
        .withFaultHandler(zlg::w)
        .withErrorHandler(zlg::e);
    this.leases = new RetryableMap<>(retry, leases);
    registry = new Registry();
    
    scavengerThread = WorkerThread.builder()
        .withOptions(new WorkerOptions().daemon().withName(Election.class, "scavenger"))
        .onCycle(this::scavegerCycle)
        .build();
  }
  
  public Election start() {
    scavengerThread.start();
    return this;
  }
  
  public Registry getRegistry() {
    return registry;
  }
  
  private void scavegerCycle(WorkerThread t) throws InterruptedException {
    scavenge();
    Thread.sleep(config.getScavengeInterval());
  }
  
  void scavenge() {
    reloadView();
    
    synchronized (scavengeLock) {
      final Set<String> resources = registry.getCandidatesView().keySet();
      for (String resource : resources) {
        final Lease existingLease = leaseView.getOrDefault(resource, Lease.vacant());
        if (! existingLease.isCurrent()) {
          if (existingLease.isVacant()) {
            zlg.d("Lease of %s is vacant", z -> z.arg(resource)); 
          } else {
            scavengeWatcher.onExpire(resource, existingLease.getTenant());
            zlg.d("Lease of %s by %s expired at %s", 
                  z -> z
                  .arg(resource)
                  .arg(existingLease::getTenant)
                  .arg(Args.map(existingLease::getExpiry, Lease::formatExpiry)));
          }
          
          final UUID nextCandidate = registry.getRandomCandidate(resource);
          if (nextCandidate != null) {
            final boolean success;
            final Lease newLease = new Lease(nextCandidate, System.currentTimeMillis() + config.getLeaseDuration());
            if (existingLease.isVacant()) {
              final byte[] previous = leases.putIfAbsent(resource, newLease.pack());
              success = previous == null;
            } else {
              success = leases.replace(resource, existingLease.pack(), newLease.pack());
            }
            
            if (success) {
              zlg.d("New lease of %s by %s until %s", 
                    z -> z
                    .arg(resource)
                    .arg(nextCandidate)
                    .arg(Args.map(newLease::getExpiry, Lease::formatExpiry)));
              reloadView();
              scavengeWatcher.onAssign(resource, nextCandidate);
            }
          }
        }
      }
    }
  }
  
  private void reloadView() {
    synchronized (viewLock) {
      final LeaseViewImpl newLeaseView = new LeaseViewImpl(nextViewVersion++);
      for (Map.Entry<String, byte[]> leaseTableEntry : leases.entrySet()) {
        final Lease lease = Lease.unpack(leaseTableEntry.getValue());
        newLeaseView.put(leaseTableEntry.getKey(), lease);
      }
      leaseView = newLeaseView;
    }
  }

  public LeaseView getLeaseView() {
    return leaseView;
  }
  
  public void extend(String resource, UUID tenant) throws NotTenantException {
    for (;;) {
      final Lease existingLease = checkCurrent(resource, tenant);
      final Lease newLease = new Lease(tenant, System.currentTimeMillis() + config.getLeaseDuration());
      final boolean extended = leases.replace(resource, existingLease.pack(), newLease.pack());
      if (extended) {
        reloadView();
        return;
      } else {
        reloadView();
      }
    }
  }
  
  public void yield(String resource, UUID tenant) throws NotTenantException {
    for (;;) {
      final Lease existingLease = checkCurrent(resource, tenant);
      final boolean removed = leases.remove(resource, existingLease.pack());
      if (removed) {
        reloadView();
        return;
      } else {
        reloadView();
      }
    }
  }
  
  private Lease checkCurrent(String resource, UUID assumedTenant) throws NotTenantException {
    final Lease existingLease = leaseView.getOrDefault(resource, Lease.vacant());
    if (! existingLease.isHeldByAndCurrent(assumedTenant)) {
      final String m = String.format("Leader of %s is %s until %s", 
                                     resource, existingLease.getTenant(), Lease.formatExpiry(existingLease.getExpiry()));
      throw new NotTenantException(m);
    } else {
      return existingLease;
    }
  }

  void setScavengeWatcher(ScavengeWatcher scavengeWatcher) {
    this.scavengeWatcher = scavengeWatcher;
  }
  
  @Override
  public Joinable terminate() {
    scavengerThread.terminate();
    return this;
  }

  @Override
  public boolean join(long timeoutMillis) throws InterruptedException {
    return scavengerThread.join(timeoutMillis);
  }
}
