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
package org.jenkins.pubsub;

import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.listeners.ItemListener;

/**
 * Pre-defined event type name enumerations.
 * <p>
 * Of course new events (not pre-defined here) can be created. The idea of
 * types pre-defined here is to try help standardise on the event types.
 * <p>
 * If you find yourself needing a new event type (or channel), consider
 * creating a Pull Request on this repo, adding it as one of the pre-defined
 * event types.
 * <p>
 * <strong>*** SEE the docs on the nested types for more details ***</strong>
 *  
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 * @see EventProps
 */
public interface Events {

    /**
     * Pre-defined "job" channel events.
     * <ul>
     *     <li>See {@link org.jenkins.pubsub.EventProps.Job} for pre-defined job channel event properties.</li>
     * </ul>
     */
    enum JobChannel {
        
        // Job CRUD events

        /**
         * Job created.
         * @see ItemListener#onCreated(Item)
         */
        job_crud_created,

        /**
         * Job deleted.
         * @see ItemListener#onDeleted(Item)
         */
        job_crud_deleted,
        
        /**
         * Job updated.
         * @see ItemListener#onUpdated(Item)
         */
        job_crud_updated,
        
        /**
         * Job renamed.
         * @see org.jenkins.pubsub.EventProps.Item#item_rename_before
         * @see org.jenkins.pubsub.EventProps.Item#item_rename_after
         * @see ItemListener#onRenamed(Item, String, String)
         */
        job_crud_renamed,
        
        // Job run events
        
        /**
         * Job run queue entered.
         * @see hudson.model.queue.QueueListener#onEnterWaiting(Queue.WaitingItem)  
         */
        job_run_queue_enter,
        /**
         * Job run queue buildable.
         * @see hudson.model.queue.QueueListener#onEnterBuildable(Queue.BuildableItem) 
         */
        job_run_queue_buildable,
        /**
         * Job run queue left.
         * @see hudson.model.queue.QueueListener#onLeft(Queue.LeftItem)  
         */
        job_run_queue_left,
        /**
         * Job run queue blocked.
         * @see hudson.model.queue.QueueListener#onEnterBlocked(Queue.BlockedItem)
         */
        job_run_queue_blocked,
        /**
         * Job run started.
         */
        job_run_started,
        /**
         * Job run ended.
         */
        job_run_ended;

        /**
         * The channel name.
         */
        public static final String NAME = "job";
    }
}
