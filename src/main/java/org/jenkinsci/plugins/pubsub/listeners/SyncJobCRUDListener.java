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
import hudson.model.Job;
import hudson.model.listeners.ItemListener;
import org.jenkinsci.plugins.pubsub.EventProps;
import org.jenkinsci.plugins.pubsub.Events;
import org.jenkinsci.plugins.pubsub.JobMessage;
import org.jenkinsci.plugins.pubsub.Message;
import org.jenkinsci.plugins.pubsub.MessageException;
import org.jenkinsci.plugins.pubsub.PubsubBus;
import org.jenkinsci.plugins.pubsub.SimpleMessage;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Job CRUD event listener.
 * <p>
 * Publishes:
 * <ul>
 *     <li>{@link Events.JobChannel#job_crud_created}</li>
 *     <li>{@link Events.JobChannel#job_crud_deleted}</li>
 *     <li>{@link Events.JobChannel#job_crud_renamed}</li>
 *     <li>{@link Events.JobChannel#job_crud_updated}</li>
 * </ul>
 *  
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Extension
public class SyncJobCRUDListener extends ItemListener {

    private static final Logger LOGGER = Logger.getLogger(SyncJobCRUDListener.class.getName());
    
    @Override
    public void onCreated(Item item) {
        publish(item, Events.JobChannel.job_crud_created, null);
    }

    @Override
    public void onDeleted(Item item) {
        publish(item, Events.JobChannel.job_crud_deleted, null);
    }

    @Override
    public void onRenamed(Item item, String oldName, String newName) {
        publish(item, Events.JobChannel.job_crud_renamed,  new SimpleMessage()
                .set(EventProps.Item.item_rename_before, oldName)
                .set(EventProps.Item.item_rename_after, newName)
        );
    }

    @Override
    public void onUpdated(Item item) {
        publish(item, Events.JobChannel.job_crud_updated, null);
    }

    private void publish(Item item, Events.JobChannel event, Properties properties) {
        if (item instanceof Job) {
            try {
                Message<JobMessage> message = new JobMessage(item).setEventName(event);
                
                if (properties != null) {
                    message.putAll(properties);
                }
                
                PubsubBus.getBus().publish(message);
            } catch (MessageException e) {
                LOGGER.log(Level.WARNING, "Error publishing Job CRUD event.", e);
            }
        }
    }
}
