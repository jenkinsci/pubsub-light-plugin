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
import hudson.security.AccessControlled;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Jenkins Job Channel {@link Item} domain model {@link PubsubBus} message instance.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public abstract class JobChannelMessage<T extends JobChannelMessage> extends AccessControlledMessage {
    
    transient Item jobChannelItem;

    /**
     * Create a Hob message instance.
     */
    JobChannelMessage() {
        super();
        setChannelName(Events.JobChannel.NAME);
    }

    public JobChannelMessage(@Nonnull Item jobChannelItem) {
        setJobChannelItem(jobChannelItem);
    }

    @Override
    public final String getChannelName() {
        return Events.JobChannel.NAME;
    }

    @Override
    public final Message setChannelName(String name) {
        return super.setChannelName(Events.JobChannel.NAME);
    }

    public String getJobName() {
        return get(EventProps.Job.job_name);
    }

    private transient boolean jobLookupComplete = false;

    /**
     * Get the Jenkins {@link Item} associated with this message.
     * @return The Jenkins {@link Item} associated with this message,
     * or {code null} if the message is not associated with a
     * Jenkins {@link Item}.
     */
    public synchronized @CheckForNull
    Item getJobChannelItem() {
        if (jobLookupComplete || jobChannelItem != null) {
            return jobChannelItem;
        }
        
        try {
            String jobName = get(EventProps.Job.job_name);
            if (jobName != null) {
                Jenkins jenkins = Jenkins.getInstance();
                jobChannelItem = (Item) jenkins.getItemByFullName(jobName);
            }
        } finally {
            jobLookupComplete = true;
        }
        return jobChannelItem;
    }
    
    private synchronized void setJobChannelItem(@Nonnull Item jobChannelItem) {
        this.jobChannelItem = jobChannelItem;
        super.setChannelName(Events.JobChannel.NAME);
        set(EventProps.Job.job_name, jobChannelItem.getFullName());
        setItemProps(jobChannelItem);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AccessControlled getAccessControlled() {
        return getJobChannelItem();
    }

    @Nonnull
    @Override
    protected Permission getRequiredPermission() {
        return Item.READ;
    }
}
