/**
 * Copyright (c) 2013-2024 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.api;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.redisson.api.map.MapLoader;
import org.redisson.api.map.MapWriter;
import org.redisson.api.map.event.MapEntryListener;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * <p>Map-based cache with ability to set TTL for each entry via
 * {@link #put(Object, Object, long, TimeUnit)} or {@link #putIfAbsent(Object, Object, long, TimeUnit)} method.
 * And therefore has an complex lua-scripts inside.</p>
 *
 * <p>Current redis implementation doesnt have map entry eviction functionality.
 * Thus entries are checked for TTL expiration during any key/value/entry read operation.
 * If key/value/entry expired then it doesn't returns and clean task runs asynchronous.
 * Clean task deletes removes 100 expired entries at once.
 * In addition there is {@link org.redisson.eviction.EvictionScheduler}. This scheduler
 * deletes expired entries in time interval between 5 seconds to 2 hours.</p>
 *
 * <p>If eviction is not required then it's better to use {@link org.redisson.reactive.RedissonMapReactive}.</p>
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public interface RMapCacheRx<K, V> extends RMapRx<K, V>, RDestroyable {

    /**
     * Sets max size of the map.
     * Superfluous elements are evicted using LRU algorithm.
     * 
     * @param maxSize - max size
     * @return void
     */
    Completable setMaxSize(int maxSize);

    /**
     * Sets max size of the map and overrides current value.
     * Superfluous elements are evicted using defined algorithm.
     *
     * @param maxSize - max size
     * @param mode - eviction mode
     */
    Completable setMaxSize(int maxSize, EvictionMode mode);

    /**
     * Tries to set max size of the map. 
     * Superfluous elements are evicted using LRU algorithm. 
     *
     * @param maxSize - max size
     * @return <code>true</code> if max size has been successfully set, otherwise <code>false</code>.
     */
    Single<Boolean> trySetMaxSize(int maxSize);

    /**
     * Tries to set max size of the map.
     * Superfluous elements are evicted using defined algorithm.
     *
     * @param maxSize - max size
     * @param mode - eviction mode
     * @return <code>true</code> if max size has been successfully set, otherwise <code>false</code>.
     */
    Single<Boolean> trySetMaxSize(int maxSize, EvictionMode mode);

    /**
     * If the specified key is not already associated
     * with a value, associate it with the given value.
     * <p>
     * Stores value mapped by key with specified time to live.
     * Entry expires after specified time to live.
     * If the map previously contained a mapping for
     * the key, the old value is replaced by the specified value.
     *
     * @param key - map key
     * @param value - map value
     * @param ttl - time to live for key\value entry.
     *              If <code>0</code> then stores infinitely.
     * @param unit - time unit
     * @return previous associated value
     */
    Maybe<V> putIfAbsent(K key, V value, long ttl, TimeUnit unit);

    /**
     * If the specified key is not already associated
     * with a value, associate it with the given value.
     * <p>
     * Stores value mapped by key with specified time to live and max idle time.
     * Entry expires when specified time to live or max idle time has expired.
     * <p>
     * If the map previously contained a mapping for
     * the key, the old value is replaced by the specified value.
     *
     * @param key - map key
     * @param value - map value
     * @param ttl - time to live for key\value entry.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * @param ttlUnit - time unit
     * @param maxIdleTime - max idle time for key\value entry.
     *              If <code>0</code> then max idle time doesn't affect entry expiration.
     * @param maxIdleUnit - time unit
     * <p>
     * if <code>maxIdleTime</code> and <code>ttl</code> params are equal to <code>0</code>
     * then entry stores infinitely.
     *
     * @return previous associated value
     */
    Maybe<V> putIfAbsent(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit);
    /**
     * If the specified key is not already associated
     * with a value, attempts to compute its value using the given mapping function and enters it into this map .
     * <p>
     * Stores value mapped by key with specified time to live.
     * Entry expires after specified time to live.
     *
     * @param key - map key
     * @param ttl - time to live for key\value entry.
     *              If <code>0</code> then stores infinitely.
     * @param mappingFunction the mapping function to compute a value
     * @return current associated value
     */
    Maybe<V> computeIfAbsent(K key, Duration ttl, Function<? super K, ? extends V> mappingFunction);
    /**
     * Stores value mapped by key with specified time to live.
     * Entry expires after specified time to live.
     * If the map previously contained a mapping for
     * the key, the old value is replaced by the specified value.
     *
     * @param key - map key
     * @param value - map value
     * @param ttl - time to live for key\value entry.
     *              If <code>0</code> then stores infinitely.
     * @param unit - time unit
     * @return previous associated value
     */
    Maybe<V> put(K key, V value, long ttl, TimeUnit unit);

    /**
     * Stores value mapped by key with specified time to live and max idle time.
     * Entry expires when specified time to live or max idle time has expired.
     * <p>
     * If the map previously contained a mapping for
     * the key, the old value is replaced by the specified value.
     *
     * @param key - map key
     * @param value - map value
     * @param ttl - time to live for key\value entry.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * @param ttlUnit - time unit
     * @param maxIdleTime - max idle time for key\value entry.
     *              If <code>0</code> then max idle time doesn't affect entry expiration.
     * @param maxIdleUnit - time unit
     * <p>
     * if <code>maxIdleTime</code> and <code>ttl</code> params are equal to <code>0</code>
     * then entry stores infinitely.
     *
     * @return previous associated value
     */
    Maybe<V> put(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit);

    /**
     * Associates the specified <code>value</code> with the specified <code>key</code>
     * in batch.
     * <p>
     * If {@link MapWriter} is defined then new map entries will be stored in write-through mode.
     *
     * @param map - mappings to be stored in this map
     * @param ttl - time to live for all key\value entries.
     *              If <code>0</code> then stores infinitely.
     * @param ttlUnit - time unit
     */
    Completable putAll(java.util.Map<? extends K, ? extends V> map, long ttl, TimeUnit ttlUnit);

    /**
     * Stores value mapped by key with specified time to live.
     * Entry expires after specified time to live.
     * <p>
     * If the map previously contained a mapping for
     * the key, the old value is replaced by the specified value.
     * <p>
     * Works faster than usual {@link #put(Object, Object, long, TimeUnit)}
     * as it not returns previous value.
     *
     * @param key - map key
     * @param value - map value
     * @param ttl - time to live for key\value entry.
     *              If <code>0</code> then stores infinitely.
     * @param unit - time unit
     * 
     * @return <code>true</code> if key is a new key in the hash and value was set.
     *         <code>false</code> if key already exists in the hash and the value was updated.
     */
    Single<Boolean> fastPut(K key, V value, long ttl, TimeUnit unit);

    /**
     * Stores value mapped by key with specified time to live and max idle time.
     * Entry expires when specified time to live or max idle time has expired.
     * <p>
     * If the map previously contained a mapping for
     * the key, the old value is replaced by the specified value.
     * <p>
     * Works faster than usual {@link #put(Object, Object, long, TimeUnit, long, TimeUnit)}
     * as it not returns previous value.
     *
     * @param key - map key
     * @param value - map value
     * @param ttl - time to live for key\value entry.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * @param ttlUnit - time unit
     * @param maxIdleTime - max idle time for key\value entry.
     *              If <code>0</code> then max idle time doesn't affect entry expiration.
     * @param maxIdleUnit - time unit
     * <p>
     * if <code>maxIdleTime</code> and <code>ttl</code> params are equal to <code>0</code>
     * then entry stores infinitely.

     * @return <code>true</code> if key is a new key in the hash and value was set.
     *         <code>false</code> if key already exists in the hash and the value was updated.
     */
    Single<Boolean> fastPut(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit);
    
    /**
     * If the specified key is not already associated
     * with a value, associate it with the given value.
     * <p>
     * Stores value mapped by key with specified time to live and max idle time.
     * Entry expires when specified time to live or max idle time has expired.
     * <p>
     * Works faster than usual {@link #putIfAbsent(Object, Object, long, TimeUnit, long, TimeUnit)}
     * as it not returns previous value.
     *
     * @param key - map key
     * @param value - map value
     * @param ttl - time to live for key\value entry.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * @param ttlUnit - time unit
     * @param maxIdleTime - max idle time for key\value entry.
     *              If <code>0</code> then max idle time doesn't affect entry expiration.
     * @param maxIdleUnit - time unit
     * <p>
     * if <code>maxIdleTime</code> and <code>ttl</code> params are equal to <code>0</code>
     * then entry stores infinitely.
     *
     * @return <code>true</code> if key is a new key in the hash and value was set.
     *         <code>false</code> if key already exists in the hash
     */
    Single<Boolean> fastPutIfAbsent(K key, V value, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit);

    /**
     * Use {@link #expireEntry(Object, Duration, Duration)} instead.
     *
     * @param key - map key
     * @param ttl - time to live for key\value entry.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * @param ttlUnit - time unit
     * @param maxIdleTime - max idle time for key\value entry.
     *              If <code>0</code> then max idle time doesn't affect entry expiration.
     * @param maxIdleUnit - time unit
     * <p>
     * if <code>maxIdleTime</code> and <code>ttl</code> params are equal to <code>0</code>
     * then entry stores infinitely.
     *
     * @return returns <code>false</code> if entry already expired or doesn't exist,
     *         otherwise returns <code>true</code>.
     */
    @Deprecated
    Single<Boolean> updateEntryExpiration(K key, long ttl, TimeUnit ttlUnit, long maxIdleTime, TimeUnit maxIdleUnit);

    /**
     * Updates time to live and max idle time of specified entry by key.
     * Entry expires when specified time to live or max idle time was reached.
     * <p>
     * Returns <code>false</code> if entry already expired or doesn't exist,
     * otherwise returns <code>true</code>.
     *
     * @param key map key
     * @param ttl time to live for key\value entry.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * @param maxIdleTime max idle time for key\value entry.
     *              If <code>0</code> then max idle time doesn't affect entry expiration.
     * <p>
     * if <code>maxIdleTime</code> and <code>ttl</code> params are equal to <code>0</code>
     * then entry stores infinitely.
     *
     * @return returns <code>false</code> if entry already expired or doesn't exist,
     *         otherwise returns <code>true</code>.
     */
    Single<Boolean> expireEntry(K key, Duration ttl, Duration maxIdleTime);

    /**
     * Updates time to live and max idle time of specified entries by keys.
     * Entries expires when specified time to live or max idle time was reached.
     * <p>
     * Returns amount of updated entries.
     *
     * @param keys map keys
     * @param ttl time to live for key\value entries.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * @param maxIdleTime max idle time for key\value entries.
     *              If <code>0</code> then max idle time doesn't affect entry expiration.
     * <p>
     * if <code>maxIdleTime</code> and <code>ttl</code> params are equal to <code>0</code>
     * then entries are stored infinitely.
     *
     * @return amount of updated entries.
     */
    Single<Integer> expireEntries(Set<K> keys, Duration ttl, Duration maxIdleTime);

    /**
     * Sets time to live and max idle time of specified entry by key.
     * If these parameters weren't set before.
     * Entry expires when specified time to live or max idle time was reached.
     * <p>
     * Returns <code>false</code> if entry already has expiration time or doesn't exist,
     * otherwise returns <code>true</code>.
     *
     * @param key map key
     * @param ttl time to live for key\value entry.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * @param maxIdleTime max idle time for key\value entry.
     *              If <code>0</code> then max idle time doesn't affect entry expiration.
     * <p>
     * if <code>maxIdleTime</code> and <code>ttl</code> params are equal to <code>0</code>
     * then entry stores infinitely.
     *
     * @return returns <code>false</code> if entry already has expiration time or doesn't exist,
     *         otherwise returns <code>true</code>.
     */
    Single<Boolean> expireEntryIfNotSet(K key, Duration ttl, Duration maxIdleTime);

    /**
     * Sets time to live and max idle time of specified entries by keys.
     * If these parameters weren't set before.
     * Entries expire when specified time to live or max idle time was reached.
     * <p>
     * Returns amount of updated entries.
     *
     * @param keys map keys
     * @param ttl time to live for key\value entry.
     *              If <code>0</code> then time to live doesn't affect entry expiration.
     * @param maxIdleTime max idle time for key\value entry.
     *              If <code>0</code> then max idle time doesn't affect entry expiration.
     * <p>
     * if <code>maxIdleTime</code> and <code>ttl</code> params are equal to <code>0</code>
     * then entry stores infinitely.
     *
     * @return amount of updated entries.
     */
    Single<Integer> expireEntriesIfNotSet(Set<K> keys, Duration ttl, Duration maxIdleTime);

    /**
     * Returns the value mapped by defined <code>key</code> or {@code null} if value is absent.
     * <p>
     * If map doesn't contain value for specified key and {@link MapLoader} is defined
     * then value will be loaded in read-through mode.
     * <p>
     * Idle time of entry is not taken into account.
     * Entry last access time isn't modified if map limited by size.
     *
     * @param key the key
     * @return the value mapped by defined <code>key</code> or {@code null} if value is absent
     */
    Maybe<V> getWithTTLOnly(K key);

    /**
     * Returns map slice contained the mappings with defined <code>keys</code>.
     * <p>
     * If map doesn't contain value/values for specified key/keys and {@link MapLoader} is defined
     * then value/values will be loaded in read-through mode.
     * <p>
     * NOTE: Idle time of entry is not taken into account.
     * Entry last access time isn't modified if map limited by size.
     *
     * @param keys map keys
     * @return Map slice
     */
    Single<Map<K, V>> getAllWithTTLOnly(Set<K> keys);

    /**
     * Returns the number of entries in cache.
     * This number can reflects expired entries too
     * due to non realtime cleanup process.
     *
     */
    @Override
    Single<Integer> size();
    
    /**
     * Remaining time to live of map entry associated with a <code>key</code>. 
     * 
     * @param key - map key
     * @return time in milliseconds
     *          -2 if the key does not exist.
     *          -1 if the key exists but has no associated expire.
     */
    Single<Long> remainTimeToLive(K key);

    /**
     * Adds map entry listener
     *
     * @see org.redisson.api.map.event.EntryCreatedListener
     * @see org.redisson.api.map.event.EntryUpdatedListener
     * @see org.redisson.api.map.event.EntryRemovedListener
     * @see org.redisson.api.map.event.EntryExpiredListener
     *
     * @param listener - entry listener
     * @return listener id
     */
    Single<Integer> addListener(MapEntryListener listener);

}
