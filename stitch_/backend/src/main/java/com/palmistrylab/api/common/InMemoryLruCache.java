package com.palmistrylab.api.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 线程安全的简单 LRU 缓存（原先 PalmistryService 里三份手写 LinkedHashMap 各自为政）。
 * 只用于单实例内存缓存；多实例部署时缓存语义会随实例分裂（TD §13.1 已知事项）。
 */
public class InMemoryLruCache<K, V> {

  private final Map<K, V> cache;

  public InMemoryLruCache(int maxSize) {
    this.cache = Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;
      }
    });
  }

  public V get(K key) {
    return cache.get(key);
  }

  public void put(K key, V value) {
    cache.put(key, value);
  }
}
