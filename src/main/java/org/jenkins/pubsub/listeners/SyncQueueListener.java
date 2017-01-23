/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkins.pubsub.listeners;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import hudson.model.queue.QueueTaskFuture;
import org.jenkins.pubsub.EventProps;
import org.jenkins.pubsub.Events;
import org.jenkins.pubsub.MessageException;
import org.jenkins.pubsub.PubsubBus;
import org.jenkins.pubsub.QueueTaskMessage;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Jenkins Queue event listener.
 * <p>
 * Publishes:
 * <ul>
 *     <li>{@link Events.JobChannel#job_run_queue_enter}</li>
 *     <li>{@link Events.JobChannel#job_run_queue_buildable}</li>
 *     <li>{@link Events.JobChannel#job_run_queue_left}</li>
 *     <li>{@link Events.JobChannel#job_run_queue_blocked}</li>
 * </ul>
 *  
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Extension
public class SyncQueueListener extends QueueListener {
    
    private static final Logger LOGGER = Logger.getLogger(SyncQueueListener.class.getName());

    //
    // The onLeft event does not mean that the queue task is actually "done", so we need to keep
    // track of queue tasks so that we can fire an event when the task is actually "done".
    //
    // Using some blocking queues for this, with a single thread checking the tasks to see if
    // they're done, firing the job_run_queue_task_complete event when they are.
    //
    // Added as a result of https://issues.jenkins-ci.org/browse/JENKINS-39794
    //
    private static BlockingQueue<Queue.LeftItem> queueTaskLeftPublishQueue = new LinkedBlockingQueue<>();
    private static BlockingQueue<Queue.LeftItem> tryLaterQueueTaskLeftQueue = new LinkedBlockingQueue<>();
    static {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Null to signal thread exit.
                queueTaskLeftPublishQueue = null;
            }
        });
        new Thread() {
            @Override
            public void run() {
                // Get a local ref to queueTaskLeftPublishQueue, in case it is null'd
                // later in the shutdown hook (see above).
                BlockingQueue<Queue.LeftItem> blockingQueueRef = queueTaskLeftPublishQueue;
                while (queueTaskLeftPublishQueue != null) {
                    try {
                        // Pull items off the queue and check are they "done". Publish them
                        // if they are, put them into a "try later" queue if they're not.
                        Queue.LeftItem leftItem = blockingQueueRef.poll(500, TimeUnit.MILLISECONDS);
                        if (leftItem != null) {
                            QueueTaskFuture<Queue.Executable> future = leftItem.getFuture();

                            // Drain the "try later" queue now before we possibly add more to
                            // it (see below). If the main queue (blockingQueueRef) was empty,
                            // the above poll on it would have ensured that we waited at least
                            // the poll timeout period before retrying ones that were not
                            // "done" on an earlier iteration.
                            tryLaterQueueTaskLeftQueue.drainTo(blockingQueueRef);

                            if (future.isDone()) {
                                publish(leftItem, Events.JobChannel.job_run_queue_task_complete, null);
                            } else  {
                                // Not done. Put the item back on the queue and test again later.
                                // However, don't put it back into the queue immediately. Putting it into
                                // the "try later" queue ensures that it will be left for a little while
                                // if the main queue is empty i.e. we avoid a tight loop here.
                                tryLaterQueueTaskLeftQueue.put(leftItem);
                            }
                        } else {
                            tryLaterQueueTaskLeftQueue.drainTo(blockingQueueRef);
                        }
                    } catch (InterruptedException e) {
                        // Queue access (or thread sleeping) error. This event is going to fall on
                        // the floor ... sorry !!
                        LOGGER.log(Level.WARNING, "Error publishing job_run_queue_task_complete event.", e);
                    }
                }
            }
        }.start();
    }

    @Override
    public void onEnterWaiting(Queue.WaitingItem wi) {
        publish(wi, Events.JobChannel.job_run_queue_enter);
    }

    @Override
    public void onEnterBuildable(Queue.BuildableItem bi) {
        publish(bi, Events.JobChannel.job_run_queue_buildable);
    }

    @Override
    public void onLeft(Queue.LeftItem li) {
        if (li.isCancelled()) {
            publish(li, Events.JobChannel.job_run_queue_left, "CANCELLED");
        } else {
            publish(li, Events.JobChannel.job_run_queue_left, "ALLOCATED");

            // Get a local ref to queueTaskLeftPublishQueue, in case it is null'd
            // later in the shutdown hook (see above).
            BlockingQueue<Queue.LeftItem> blockingQueueRef = queueTaskLeftPublishQueue;
            if (blockingQueueRef != null) {
                try {
                    blockingQueueRef.put(li);
                } catch (InterruptedException e) {
                    // Queue access error. This event is going to fall on
                    // the floor ... sorry !!
                    LOGGER.log(Level.WARNING, "Error publishing job_run_queue_task_complete event.", e);
                }
            }
        }
    }

    @Override
    public void onEnterBlocked(Queue.BlockedItem bi) {
        publish(bi, Events.JobChannel.job_run_queue_blocked);
    }

    private void publish(Queue.Item item, Events.JobChannel event) {
        publish(item, event, "QUEUED");
    }
    private static void publish(Queue.Item item, Events.JobChannel event, String status) {
        Queue.Task task = item.task;
        if (task instanceof Item) {
            try {
                PubsubBus.getBus().publish(new QueueTaskMessage(item, (Item)task)
                        .setEventName(event)
                        .set(EventProps.Job.job_run_queueId, Long.toString(item.getId()))
                        .set(EventProps.Job.job_run_status, status)
                );
            } catch (MessageException e) {
                LOGGER.log(Level.WARNING, "Error publishing Run queued event.", e);
            }
        }
    }
}
