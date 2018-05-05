package com.obsidiandynamics.meteor.util;

import java.util.*;

import com.hazelcast.core.*;
import com.obsidiandynamics.retry.*;

public final class RetryableMap<K, V> {
  private final Retry retry;
  
  private final IMap<K, V> map;

  public RetryableMap(Retry retry, IMap<K, V> map) {
    this.retry = retry;
    this.map = map;
  }
  
  public V putIfAbsent(K key, V value) {
    return retry.run(() -> map.putIfAbsent(key, value));
  }
  
  public Set<Map.Entry<K, V>> entrySet() {
    return retry.run(() -> map.entrySet());
  }
  
  public boolean replace(K key, V oldValue, V newValue) {
    return retry.run(() -> map.replace(key, oldValue, newValue));
  }
  
  public boolean remove(Object key, Object value) {
    return retry.run(() -> map.remove(key, value));
  }

  public V get(Object key) {
    return retry.run(() -> map.get(key));
  }

  public V put(K key, V value) {
    return retry.run(() -> map.put(key, value));
  }
}
