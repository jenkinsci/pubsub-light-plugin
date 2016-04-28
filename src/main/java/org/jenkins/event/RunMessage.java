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
package org.jenkins.event;

import hudson.model.Job;
import hudson.model.Run;
import hudson.security.AccessControlled;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Jenkins {@link Run} domain model {@link MessageBus} message instance.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public final class RunMessage extends JobMessage {
    
    private static final long serialVersionUID = -1L;

    public static final String RUN_START_KEY = "run.start";
    public static final String RUN_END_KEY = "run.end";
    
    transient Run messageRun;
    private transient boolean messageRunLookupComplete = false;

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
        this.messageRun = run;
        setProperty(OBJECT_NAME_KEY, run.getDisplayName());
        setProperty(OBJECT_ID_KEY, run.getId());
        setProperty(OBJECT_URL_KEY, run.getUrl());
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
     * Get the Jenkins {@link Run} assocated with this message.
     * @return The Jenkins {@link Run} assocated with this message,
     * or {code null} if the message is not associated with a
     * Jenkins {@link Run}.
     */
    protected synchronized @CheckForNull AccessControlled getAccessControlled() {
        if (messageRunLookupComplete || messageRun != null) {
            return messageRun;
        }
        
        try {
            Jenkins jenkins = Jenkins.getInstance();

            String jobName = getProperty(JOB_NAME_KEY);
            if (jobName != null) {
                Job job = (Job) jenkins.getItemByFullName(jobName);
                if (job != null) {
                    String buildId = getObjectId();
                    if (buildId != null) {
                        messageRun = job.getBuild(buildId);
                    }
                }
            }
        } finally {
            messageRunLookupComplete = true;
        }
        return messageRun;
    }
}
