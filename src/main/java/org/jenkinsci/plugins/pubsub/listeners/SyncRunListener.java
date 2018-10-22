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
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import org.jenkinsci.plugins.pubsub.Events;
import org.jenkinsci.plugins.pubsub.MessageException;
import org.jenkinsci.plugins.pubsub.PubsubBus;
import org.jenkinsci.plugins.pubsub.RunMessage;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Jenkins Run event listener.
 * <p>
 * Publishes:
 * <ul>
 *     <li>{@link Events.JobChannel#job_run_started job_run_started}</li>
 *     <li>{@link Events.JobChannel#job_run_ended job_run_ended}</li>
 * </ul>
 * 
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Extension
public class SyncRunListener extends RunListener<Run<?,?>> {
    
    private static final Logger LOGGER = Logger.getLogger(SyncRunListener.class.getName());

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
        try {
            PubsubBus.getBus().publish(
                    new RunMessage(run)
                        .withCauses(run)
                        .setEventName(Events.JobChannel.job_run_started)
            );
        } catch (MessageException e) {
            LOGGER.log(Level.WARNING, "Error publishing Run start event.", e);
        }
    }

    @Override
    public void onFinalized(Run<?, ?> run) {
        try {
            PubsubBus.getBus().publish(new RunMessage(run)
                    .setEventName(Events.JobChannel.job_run_ended)
            );
        } catch (MessageException e) {
            LOGGER.log(Level.WARNING, "Error publishing Run end event.", e);
        }
    }
}
