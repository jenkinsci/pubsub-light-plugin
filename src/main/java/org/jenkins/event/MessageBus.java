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

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.User;
import jenkins.model.TransientActionFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Properties;

/**
 * Abstract message bus.
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public abstract class MessageBus implements ExtensionPoint {
    
    private static MessageBus messageBus;
    
    /**
     * Get the installed {@link MessageBus} implementation.
     * @return The installed {@link MessageBus} implementation, or default
     * implementation if none are found.
     */
    public synchronized static @Nonnull MessageBus getBus() {
        if (messageBus == null) {
            ExtensionList<MessageBus> installedBusImpls = ExtensionList.lookup(MessageBus.class);
            if (!installedBusImpls.isEmpty()) {
                messageBus = installedBusImpls.get(0);
            } else {
                messageBus = new GuavaMessageBus();
            }
        }
        return messageBus;
    }

    /**
     * Create a new {@link ChannelPublisher} instance for the specified
     * channel name.
     * @param channelName       The channel name.
     * @return The {@link ChannelPublisher} instance.
     */
    public abstract @Nonnull ChannelPublisher newPublisher(@Nonnull String channelName);

    /**
     * Subscribe to events on the specified event channel.
     * @param channelName The channel name.
     * @param subscriber  The subscriber instance that will receive the events.
     * @param user        The Jenkins user associated with the subscriber.  
     * @param eventFilter A message filter, or {@code null} if no filtering is to be applied.
     *                    This tells the bus to only forward messages that match the properties
     *                    (names and values) specified in the filter.
     */
    public abstract void subscribe(@Nonnull String channelName,
                                       @Nonnull ChannelSubscriber subscriber,
                                       @Nonnull User user,
                                       @CheckForNull Properties eventFilter);

    /**
     * Unsubscribe from events on the specified event channel.
     * @param channelName The channel name.
     * @param subscriber  The subscriber instance that was used to receive events.
     */
    public abstract void unsubscribe(@Nonnull String channelName,
                                       @Nonnull ChannelSubscriber subscriber);
}
