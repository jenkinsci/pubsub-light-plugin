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

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.User;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Properties;

/**
 * Abstract message bus.
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public abstract class PubsubBus implements ExtensionPoint {
    
    private static PubsubBus pubsubBus;
    
    /**
     * Get the installed {@link PubsubBus} implementation.
     * @return The installed {@link PubsubBus} implementation, or default
     * implementation if none are found.
     */
    public synchronized static @Nonnull
    PubsubBus getBus() {
        if (pubsubBus == null) {
            ExtensionList<PubsubBus> installedBusImpls = ExtensionList.lookup(PubsubBus.class);
            if (!installedBusImpls.isEmpty()) {
                pubsubBus = installedBusImpls.get(0);
            } else {
                pubsubBus = new GuavaPubsubBus();
            }
        }
        return pubsubBus;
    }

    /**
     * Publish a message on a channel.
     * <p>
     * The message instance must have the {@link Message#setChannelName(String) channel}
     * and {@link Message#setEventName(String) event} name properties set on it.
     *     
     * @param message The message properties.
     */
    public void publish(@Nonnull Message message) throws MessageException {
        String channelName = message.getChannelName();
        String eventName = message.getEventName();
        
        if (channelName == null || channelName.length() == 0) {
            throw new MessageException(String.format("Channel name property '%s' not set on the Message instance.", Message.CHANNEL_NAME_KEY));
        }
        if (eventName == null || eventName.length() == 0) {
            throw new MessageException(String.format("Event name property '%s' not set on the Message instance.", Message.EVENT_NAME_KEY));
        }

        publisher(channelName).publish(message);
    }

    /**
     * Get/create a new {@link ChannelPublisher} instance for the specified
     * channel name.
     * @param channelName       The channel name.
     * @return The {@link ChannelPublisher} instance.
     */
    protected abstract @Nonnull ChannelPublisher publisher(@Nonnull String channelName);

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
