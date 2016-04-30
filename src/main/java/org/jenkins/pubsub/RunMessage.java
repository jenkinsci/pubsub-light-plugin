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
import hudson.model.Result;
import hudson.model.Run;
import hudson.security.AccessControlled;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Jenkins {@link Run} domain model {@link PubsubBus} message instance.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public final class RunMessage extends JobChannelMessage<RunMessage> {
    
    private static final long serialVersionUID = -1L;
    
    transient Run run;

    /**
     * Create a plain message instance.
     */
    public RunMessage() {
        super();
    }

    /**
     * Create a message instance associated with a Jenkins {@link Run}.
     * @param run The Jenkins {@link Run} with this message instance is to be associated.
     */
    public RunMessage(@Nonnull Run run) {
        super(run.getParent());
        this.run = run;
        set(EventProps.Jenkins.jenkins_object_name, run.getDisplayName());
        set(EventProps.Jenkins.jenkins_object_id, run.getId());
        set(EventProps.Jenkins.jenkins_object_url, run.getUrl());
        set(EventProps.Job.job_run_queueId, Long.toString(run.getQueueId()));

        Result result = run.getResult();
        if (result != null) {
            set(EventProps.Job.job_run_status, result.toString());
        } else {
            set(EventProps.Job.job_run_status, "RUNNING");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Message clone() {
        Message clone = new RunMessage();
        clone.putAll(this);
        return clone;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected AccessControlled getAccessControlled() {
        return getRun();
    }

    private transient boolean runLookupComplete = false;
    /**
     * Get the Jenkins {@link Run} associated with this message.
     * @return The Jenkins {@link Run} associated with this message,
     * or {code null} if the message is not associated with a
     * Jenkins {@link Run}.
     */
    public synchronized @CheckForNull Run getRun() {
        if (runLookupComplete || run != null) {
            return run;
        }
        
        try {
            Job job = getJob();
            if (job != null) {
                String buildId = getObjectId();
                if (buildId != null) {
                    run = job.getBuild(buildId);
                }
            }
        } finally {
            runLookupComplete = true;
        }
        return run;
    }
}
