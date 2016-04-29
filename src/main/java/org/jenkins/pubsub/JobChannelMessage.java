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

import hudson.model.Job;
import hudson.security.Permission;

import javax.annotation.Nonnull;

/**
 * Jenkins {@link Job} domain model {@link PubsubBus} message instance.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
abstract class JobChannelMessage<T extends JobChannelMessage> extends AccessControlledMessage {
    
    public static final String CHANNEL_NAME = "job";
    
    public static final String JOB_NAME_KEY = "jenkins.jobName";

    /**
     * Create a Hob message instance.
     */
    JobChannelMessage() {
        super();
        setChannelName(CHANNEL_NAME);
    }

    /**
     * Create a message instance associated with a Jenkins {@link Job}.
     * @param job The Jenkins {@link Job} that this message instance is to be associated.
     */
    public JobChannelMessage(@Nonnull Job job) {
        setProperty(JOB_NAME_KEY, job.getFullName());
    }

    @Override
    public final String getChannelName() {
        return CHANNEL_NAME;
    }

    @Override
    public final Message setChannelName(String name) {
        return super.setChannelName(CHANNEL_NAME);
    }
    
    public String getJobName() {
        return getProperty(JOB_NAME_KEY);
    }

    @Nonnull
    @Override
    protected Permission getRequiredPermission() {
        return Job.READ;
    }
}
