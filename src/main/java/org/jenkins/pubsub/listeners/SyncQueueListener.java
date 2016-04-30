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
 *     <li>{@link Events.JobChannel#job_run_queued job_run_queued}</li>
 * </ul>
 *  
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Extension
public class SyncQueueListener extends QueueListener {
    
    private static final Logger LOGGER = Logger.getLogger(SyncQueueListener.class.getName());

    @Override
    public void onEnterWaiting(Queue.WaitingItem wi) {
        Queue.Task task = wi.task;
        if (task instanceof Job) {
            try {
                PubsubBus.getBus().publish(new JobMessage((Job)task)
                        .setEventName(Events.JobChannel.job_run_queued)
                        .set(EventProps.Job.job_run_queueId, Long.toString(wi.getId()))
                );
            } catch (MessageException e) {
                LOGGER.log(Level.WARNING, "Error publishing Run queued event.", e);
            }
        }
    }
}
