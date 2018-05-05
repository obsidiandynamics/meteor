package com.obsidiandynamics.meteor;

import java.util.*;

public final class Registry {
  private final Map<String, Set<UUID>> candidates = new HashMap<>();
  private final Object lock = new Object();
  
  public Registry withCandidate(String resource, UUID candidate) {
    enrol(resource, candidate);
    return this;
  }
  
  public void enrolAll(Registry source) {
    candidates.putAll(source.getCandidatesView());
  }

  public void enrol(String resource, UUID candidate) {
    synchronized (lock) {
      final Set<UUID> candidatesForResource = candidates.computeIfAbsent(resource, k -> new HashSet<>());
      candidatesForResource.add(candidate);
    }
  }
  
  public void unenrol(String resource, UUID candidate) {
    synchronized (lock) {
      final Set<UUID> candidatesForResource = candidates.getOrDefault(resource, Collections.emptySet());
      candidatesForResource.remove(candidate);
      if (candidatesForResource.isEmpty()) {
        candidates.remove(resource);
      }
    }
  }
  
  public Set<String> getResourcesView() {
    final Set<String> copy;
    synchronized (lock) {
      copy = new HashSet<>(candidates.keySet());
    }
    return Collections.unmodifiableSet(copy);
  }
  
  public Map<String, Set<UUID>> getCandidatesView() {
    final Map<String, Set<UUID>> copy = new HashMap<>();
    synchronized (lock) {
      for (Map.Entry<String, Set<UUID>> entry : candidates.entrySet()) {
        copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
      }
    }
    return Collections.unmodifiableMap(copy);
  }
  
  UUID getRandomCandidate(String resource) {
    synchronized (lock) {
      final Set<UUID> candidatesForResource = candidates.getOrDefault(resource, Collections.emptySet());
      if (candidatesForResource.isEmpty()) {
        return null;
      } else {
        final int randomIndex = (int) (Math.random() * candidatesForResource.size());
        return new ArrayList<>(candidatesForResource).get(randomIndex);
      }
    }
  }

  @Override
  public String toString() {
    return Registry.class.getSimpleName() + " [candidates=" + candidates + "]";
  }
}
