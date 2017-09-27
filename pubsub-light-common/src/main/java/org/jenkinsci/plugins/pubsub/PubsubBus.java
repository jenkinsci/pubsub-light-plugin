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
package org.jenkinsci.plugins.pubsub;

import org.jenkinsci.plugins.pubsub.exception.MessageException;
import org.jenkinsci.plugins.pubsub.message.EventFilter;
import org.jenkinsci.plugins.pubsub.message.Message;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.logging.Logger;

/**
 * Abstract Pub-sub bus.
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public abstract class PubsubBus {

    private static final Logger LOGGER = Logger.getLogger(PubsubBus.class.getName());

    protected static PubsubBus pubsubBus;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (pubsubBus != null) {
                    pubsubBus.shutdown();
                }
            }
        });
    }

    /**
     * Build if necessary and return default {@link PubsubBus} implementation.
     *
     * @return a singleton instance of the default {@link PubsubBus} implementation.
     */
    public synchronized static @Nonnull PubsubBus getBus() {
        if (pubsubBus == null) {
            if(inJenkins()) {
                try {
                    Class<?> jenkinsPubSubImpl = Class.forName("org.jenkinsci.plugins.pubsub.JenkinsGuavaPubsubBus");
                    final Method getBusStaticMethod = jenkinsPubSubImpl.getDeclaredMethod("getJenkinsBus");
                    getBusStaticMethod.setAccessible(true);
                    pubsubBus = (PubsubBus) getBusStaticMethod.invoke(null);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("unable to find or instantiate Jenkins PubsubBus implementation");
                }
            } else {
                pubsubBus = new GuavaPubsubBus();
            }
        }
        return pubsubBus;
    }

    /**
     * Determine if this module is being run in a Jenkins environment.
     * <p>
     * The environment variable {@code IN_JENKINS} needs to be set somewhere in Jenkins.  System property provided for testing purposes.
     */
    private static boolean inJenkins() {
        return Boolean.valueOf(System.getenv("IN_JENKINS")) || Boolean.getBoolean("org.jenkinsci.plugins.pubsub.in.jenkins");
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
            throw new MessageException(String.format("Channel name property '%s' not set on the Message instance.", EventProps.channel_name));
        }
        if (eventName == null || eventName.length() == 0) {
            throw new MessageException(String.format("Event name property '%s' not set on the Message instance.", EventProps.event_name));
        }

        // Make sure the channel name is set on the message.
        // In case getChannelName is overridden.
        message.setChannelName(channelName);

        // Now publish it...
        publisher(channelName).publish(message);
    }

    /**
     * Get/create a new {@link ChannelPublisher} instance for the specified
     * channel name.
     *
     * @param channelName The channel name.
     * @return The {@link ChannelPublisher} instance.
     */
    protected abstract @Nonnull
    ChannelPublisher publisher(@Nonnull String channelName);

    /**
     * Subscribe to events on the specified event channel.
     *
     * @param channelName    The channel name.
     * @param subscriber     The subscriber instance that will receive the events.
     * @param principal      The principal with which the subscription is associated.
     * @param eventFilter    A message filter, or {@code null} if no filtering is to be applied.
     *                       This tells the bus to only forward messages that match the properties
     *                       (names and values) specified in the filter.
     */
    public abstract void subscribe(@Nonnull String channelName,
                                   @Nonnull ChannelSubscriber subscriber,
                                   @Nonnull Principal principal,
                                   @CheckForNull EventFilter eventFilter);

    /**
     * Unsubscribe from events on the specified event channel.
     *
     * @param channelName The channel name.
     * @param subscriber  The subscriber instance that was used to receive events.
     */
    public abstract void unsubscribe(@Nonnull String channelName,
                                     @Nonnull ChannelSubscriber subscriber);

    /**
     * Shutdown the bus.
     */
    public abstract void shutdown();
}
