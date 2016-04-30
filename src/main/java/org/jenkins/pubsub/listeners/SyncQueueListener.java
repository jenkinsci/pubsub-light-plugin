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
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import org.jenkins.pubsub.EventProps;
import org.jenkins.pubsub.Events;
import org.jenkins.pubsub.JobMessage;
import org.jenkins.pubsub.MessageException;
import org.jenkins.pubsub.PubsubBus;

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
        }
    }

    @Override
    public void onEnterBlocked(Queue.BlockedItem bi) {
        publish(bi, Events.JobChannel.job_run_queue_blocked);
    }

    private void publish(Queue.Item item, Events.JobChannel event) {
        publish(item, event, "QUEUED");
    }
    private void publish(Queue.Item item, Events.JobChannel event, String status) {
        Queue.Task task = item.task;
        if (task instanceof Job) {
            try {
                PubsubBus.getBus().publish(new JobMessage((Job)task)
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
