package com.juick.android;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: san
 * Date: 4/2/13
 * Time: 12:21 PM
 * To change this template use File | Settings | File Templates.
 */
public class LRUCache<K, V> {

    class ValueHolder {
        V value;
        long timestamp;

        ValueHolder(V value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }

    HashMap<K, ValueHolder> map;
    int maxSize;
    V nullValue;
    int itemsCount = 0;

    public LRUCache(int maxSize, V nullValue) {
        map = new HashMap<K, ValueHolder>();
        this.maxSize = maxSize;
        this.nullValue = nullValue;
    }

    public synchronized void put(K key, V value) {
        ValueHolder oldValue = map.put(key, new ValueHolder(value, System.currentTimeMillis()));
        if (oldValue != null) {
            if (oldValue.value != nullValue) itemsCount--;
            if (oldValue.value != value) {
                cleanupValue(oldValue.value);
            }
        }
        if (value != nullValue) itemsCount++;
        if (itemsCount > maxSize) {
            cleanupOne();
        }
    }

    public synchronized V get(K key) {
        ValueHolder valueHolder = map.get(key);
        if (valueHolder != null) {
            valueHolder.timestamp = System.currentTimeMillis();
            return valueHolder.value;
        }
        return null;
    }

    void cleanupOne() {
        Iterator<Map.Entry<K,ValueHolder>> iterator = map.entrySet().iterator();
        Map.Entry<K,ValueHolder> oldestEntry = null;
        while (iterator.hasNext()) {
            Map.Entry<K, ValueHolder> next = iterator.next();
            final ValueHolder nextValue = next.getValue();
            if (nextValue.value == nullValue) continue;
            if (oldestEntry == null || nextValue.timestamp < oldestEntry.getValue().timestamp) {
                oldestEntry = next;
            }
        }
        if (oldestEntry != null) {
            map.remove(oldestEntry.getKey());
            cleanupValue(oldestEntry.getValue().value);
        }
    }

    void cleanupValue(V value) {

    }

}
