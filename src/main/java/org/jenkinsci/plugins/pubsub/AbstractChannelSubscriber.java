/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package org.jenkinsci.plugins.pubsub;

import hudson.ExtensionPoint;
import hudson.Util;
import hudson.security.ACL;
import org.springframework.security.core.Authentication;

/**
 * Simple asynchronous {@link ChannelSubscriber} {@link ExtensionPoint} for Jenkins.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public abstract class AbstractChannelSubscriber implements ChannelSubscriber, ExtensionPoint {

    /**
     * Get the name of the channel on which the subscriber will listen.
     *
     * @return The channel name.
     */
    public abstract String getChannelName();

    /**
     * Get the {@link Authentication} used for listening for events on the channel.
     * <p>
     * Override to restrict. Default is {@link ACL#SYSTEM}.
     *
     * @return The {@link Authentication} used for listening for events on the channel.
     * @deprecated Use {@link #getAuthentication2()} instead.
     */
    @Deprecated
    public org.acegisecurity.Authentication getAuthentication() {
        return org.acegisecurity.Authentication.fromSpring(getAuthentication2());
    }

    /**
     * Get the {@link Authentication} used for listening for events on the channel.
     * <p>
     * Override to restrict. Default is {@link ACL#SYSTEM2}.
     *
     * @return The {@link Authentication} used for listening for events on the channel.
     */
    public Authentication getAuthentication2() {
        if (Util.isOverridden(AbstractChannelSubscriber.class, getClass(), "getAuthentication")) {
            return getAuthentication().toSpring();
        } else {
            return ACL.SYSTEM2;
        }
    }

    /**
     * Get the event filter to be used for messages on the channel.
     * <p>
     * Override this method to define an {@link EventFilter} instance.
     * Default is {@code null} i.e. no filtering.
     *
     * @return The {@link EventFilter} instance.
     */
    public EventFilter getEventFilter() {
        return null;
    }
}
