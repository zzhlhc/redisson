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

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * RxJava2 interface for BlockingQueue
 *
 * @author Nikita Koksharov
 * @param <V> the type of elements held in this collection
 */
public interface RBlockingQueueRx<V> extends RQueueRx<V> {

    /**
     * Retrieves and removes first available head element of <b>any</b> queue,
     * waiting up to the specified wait time if necessary for an element to become available
     * in any of defined queues <b>including</b> queue own.
     *
     * @param queueNames - names of queue
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return Flowable object with the head of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     */
    Maybe<V> pollFromAny(long timeout, TimeUnit unit, String... queueNames);

    /**
     * Retrieves and removes first available head element of <b>any</b> queue,
     * waiting up to the specified wait time if necessary for an element to become available
     * in any of defined queues <b>including</b> queue itself.
     *
     * @param queueNames queue names. Queue name itself is always included
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @return the head of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     * @throws InterruptedException if interrupted while waiting
     */
    Maybe<Entry<String, V>> pollFromAnyWithName(Duration timeout, String... queueNames) throws InterruptedException;

    /**
     * Retrieves and removes first available tail element of <b>any</b> queue,
     * waiting up to the specified wait time if necessary for an element to become available
     * in any of defined queues <b>including</b> queue itself.
     *
     * @param queueNames queue names. Queue name itself is always included
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @return the tail of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     * @throws InterruptedException if interrupted while waiting
     */
    Maybe<Entry<String, V>> pollLastFromAnyWithName(Duration timeout, String... queueNames) throws InterruptedException;

    /**
     * Retrieves and removes first available head elements of <b>any</b> queue,
     * waiting up to the specified wait time if necessary for an element to become available
     * in any of defined queues <b>including</b> queue itself.
     *
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count elements amount
     * @param queueNames name of queues
     * @return the head elements
     */
    Maybe<Map<String, List<V>>> pollFirstFromAny(Duration duration, int count, String... queueNames);

    /**
     * Retrieves and removes first available tail elements of <b>any</b> queue,
     * waiting up to the specified wait time if necessary for an element to become available
     * in any of defined queues <b>including</b> queue itself.
     *
     * <p>
     * Requires <b>Redis 7.0.0 and higher.</b>
     *
     * @param duration how long to wait before giving up
     * @param count elements amount
     * @param queueNames name of queues
     * @return the tail elements
     */
    Maybe<Map<String, List<V>>> pollLastFromAny(Duration duration, int count, String... queueNames);

    /**
     * Removes at most the given number of available elements from
     * this queue and adds them to the given collection in async mode.  A failure
     * encountered while attempting to add elements to
     * collection {@code c} may result in elements being in neither,
     * either or both collections when the associated exception is
     * thrown.  Attempts to drain a queue to itself result in
     * {@code IllegalArgumentException}. Further, the behavior of
     * this operation is undefined if the specified collection is
     * modified while the operation is in progress.
     *
     * @param c the collection to transfer elements into
     * @param maxElements the maximum number of elements to transfer
     * @return the number of elements transferred
     * @throws UnsupportedOperationException if addition of elements
     *         is not supported by the specified collection
     * @throws ClassCastException if the class of an element of this queue
     *         prevents it from being added to the specified collection
     * @throws NullPointerException if the specified collection is null
     * @throws IllegalArgumentException if the specified collection is this
     *         queue, or some property of an element of this queue prevents
     *         it from being added to the specified collection
     */
    Single<Integer> drainTo(Collection<? super V> c, int maxElements);

    /**
     * Removes all available elements from this queue and adds them
     * to the given collection in async mode.  This operation may be more
     * efficient than repeatedly polling this queue.  A failure
     * encountered while attempting to add elements to
     * collection {@code c} may result in elements being in neither,
     * either or both collections when the associated exception is
     * thrown.  Attempts to drain a queue to itself result in
     * {@code IllegalArgumentException}. Further, the behavior of
     * this operation is undefined if the specified collection is
     * modified while the operation is in progress.
     *
     * @param c the collection to transfer elements into
     * @return the number of elements transferred
     * @throws UnsupportedOperationException if addition of elements
     *         is not supported by the specified collection
     * @throws ClassCastException if the class of an element of this queue
     *         prevents it from being added to the specified collection
     * @throws NullPointerException if the specified collection is null
     * @throws IllegalArgumentException if the specified collection is this
     *         queue, or some property of an element of this queue prevents
     *         it from being added to the specified collection
     */
    Single<Integer> drainTo(Collection<? super V> c);

    /**
     * Retrieves and removes last available tail element of this queue and adds it at the head of <code>queueName</code>,
     * waiting up to the specified wait time if necessary for an element to become available.
     *
     * @param queueName - names of destination queue
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the tail of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     */
    Maybe<V> pollLastAndOfferFirstTo(String queueName, long timeout, TimeUnit unit);

    /**
     * Retrieves and removes the head of this queue in async mode, waiting up to the
     * specified wait time if necessary for an element to become available.
     *
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return the head of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     */
    Maybe<V> poll(long timeout, TimeUnit unit);

    /**
     * Retrieves and removes the head of this queue in async mode, waiting if necessary
     * until an element becomes available.
     *
     * @return the head of this queue
     */
    Single<V> take();
    
    /**
     * Retrieves and removes last available tail element of <b>any</b> queue and adds it at the head of <code>queueName</code>,
     * waiting if necessary for an element to become available
     * in any of defined queues <b>including</b> queue itself.
     *
     * @param queueName - names of destination queue
     * @return the tail of this queue
     */
    Single<V> takeLastAndOfferFirstTo(String queueName);

    /**
     * Inserts the specified element into this queue in async mode, waiting if necessary
     * for space to become available.
     *
     * @param e the element to add
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null
     * @throws IllegalArgumentException if some property of the specified
     *         element prevents it from being added to this queue
     * @return void
     */
    Completable put(V e);

    /**
     * Retrieves and removes continues stream of elements from the head of this queue.
     * Waits for next element become available.
     * 
     * @return stream of elements
     */
    Flowable<V> takeElements();

}
