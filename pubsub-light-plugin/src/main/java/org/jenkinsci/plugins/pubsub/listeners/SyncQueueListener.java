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
package org.jenkinsci.plugins.pubsub.listeners;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import hudson.model.queue.QueueTaskFuture;
import org.jenkinsci.plugins.pubsub.JenkinsEventProps;
import org.jenkinsci.plugins.pubsub.JenkinsEvents;
import org.jenkinsci.plugins.pubsub.PubsubBus;
import org.jenkinsci.plugins.pubsub.exception.MessageException;
import org.jenkinsci.plugins.pubsub.message.QueueTaskMessage;

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
 *     <li>{@link JenkinsEvents.JobChannel#job_run_queue_enter}</li>
 *     <li>{@link JenkinsEvents.JobChannel#job_run_queue_buildable}</li>
 *     <li>{@link JenkinsEvents.JobChannel#job_run_queue_left}</li>
 *     <li>{@link JenkinsEvents.JobChannel#job_run_queue_blocked}</li>
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
    // The tryLaterQueueTaskLeftQueue tries to help ensure that we don't keep checking an incomplete task
    // when the main queue (queueTaskLeftPublishQueue) is empty. If we just put incomplete tasks directly back into
    // queueTaskLeftPublishQueue, we would pull it back out again immediately on the next iteration poll.
    // Putting it into tryLaterQueueTaskLeftQueue (and leaving queueTaskLeftPublishQueue empty) will mean that
    // we will "typically" wait POLL_TIMEOUT_MILLIS before checking it again. We say "typically" because, of course,
    // another task put into the queue will trigger processing again and cause tryLaterQueueTaskLeftQueue to get
    // drained into queueTaskLeftPublishQueue. That should be ok though.
    //
    // Added as a result of https://issues.jenkins-ci.org/browse/JENKINS-39794
    //
    private static BlockingQueue<Queue.LeftItem> queueTaskLeftPublishQueue = new LinkedBlockingQueue<>();
    private static BlockingQueue<Queue.LeftItem> tryLaterQueueTaskLeftQueue = new LinkedBlockingQueue<>(); // see comment above
    private static volatile boolean stopTaskLeftPublishing = false;
    private static final long POLL_TIMEOUT_MILLIS = 1000;

    static {
        new Thread() {
            @Override
            public void run() {
                try {
                    // Keep going 'til we're signaled to stop.
                    while (!stopTaskLeftPublishing) {
                        try {
                            // Pull items off the queue and check are they "done". Publish them
                            // if they are, put them into a "try later" queue if they're not.
                            Queue.LeftItem leftItem = queueTaskLeftPublishQueue.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                            if (leftItem != null) {
                                QueueTaskFuture<Queue.Executable> future = leftItem.getFuture();

                                if (future.isDone()) {
                                    // Drain the "try later" queue now before we possibly add more to
                                    // it (see below). If the main queue (queueTaskLeftPublishQueue) was empty,
                                    // the above poll on it would have ensured that we waited at least
                                    // the poll timeout period before retrying ones that were not
                                    // "done" on an earlier iteration.
                                    // See top level comments.
                                    tryLaterQueueTaskLeftQueue.drainTo(queueTaskLeftPublishQueue);

                                    publish(leftItem, JenkinsEvents.JobChannel.job_run_queue_task_complete, null);
                                } else  {
                                    // Not done. Put the item back on the queue and test again later.
                                    // However, don't put it back into the queue immediately. Putting it into
                                    // the "try later" queue ensures that it will be left for a little while
                                    // if the main queue is empty i.e. we avoid a tight loop here.
                                    // See top level comments.
                                    tryLaterQueueTaskLeftQueue.put(leftItem);
                                }
                            } else {
                                // See top level comments.
                                tryLaterQueueTaskLeftQueue.drainTo(queueTaskLeftPublishQueue);
                            }
                        } catch (InterruptedException e) {
                            // Queue access (or thread sleeping) error. This event is going to fall on
                            // the floor ... sorry !!
                            LOGGER.log(Level.WARNING, "Error publishing job_run_queue_task_complete event.", e);
                        }
                    }
                } finally {
                    queueTaskLeftPublishQueue.clear();
                    tryLaterQueueTaskLeftQueue.clear();
                }
            }
        }.start();
    }

    public static void shutdown() {
        stopTaskLeftPublishing = true;
    }

    @Override
    public void onEnterWaiting(Queue.WaitingItem wi) {
        publish(wi, JenkinsEvents.JobChannel.job_run_queue_enter);
    }

    @Override
    public void onEnterBuildable(Queue.BuildableItem bi) {
        publish(bi, JenkinsEvents.JobChannel.job_run_queue_buildable);
    }

    @Override
    public void onLeft(Queue.LeftItem li) {
        if (li.isCancelled()) {
            publish(li, JenkinsEvents.JobChannel.job_run_queue_left, "CANCELLED");
        } else {
            publish(li, JenkinsEvents.JobChannel.job_run_queue_left, "ALLOCATED");

            if (!stopTaskLeftPublishing) {
                try {
                    queueTaskLeftPublishQueue.put(li);
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
        publish(bi, JenkinsEvents.JobChannel.job_run_queue_blocked);
    }

    private void publish(Queue.Item item, JenkinsEvents.JobChannel event) {
        publish(item, event, "QUEUED");
    }
    private static void publish(Queue.Item item, JenkinsEvents.JobChannel event, String status) {
        LOGGER.log(Level.FINER, "publish() - item={0}, event={1}, status={2}", new Object[]{ item.toString(), event.toString(), status });

        Queue.Task task = item.task;
        if (task instanceof Item) {
            try {
                PubsubBus.getBus().publish(new QueueTaskMessage(item, (Item)task)
                        .setEventName(event)
                        .set(JenkinsEventProps.Job.job_run_queueId, Long.toString(item.getId()))
                        .set(JenkinsEventProps.Job.job_run_status, status)
                );
            } catch (MessageException e) {
                LOGGER.log(Level.WARNING, "Error publishing Run queued event.", e);
            }
        }
    }
}
