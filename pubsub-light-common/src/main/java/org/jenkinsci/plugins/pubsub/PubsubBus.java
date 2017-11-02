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

import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ServiceLoader;
import java.util.logging.Level;
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
     * <p>
     * PubsubBus clients need to add a ServiceLoader spi configuration file to indicate which implementation to use.  For example,
     * clients of the default {@link GuavaPubsubBus} need to include the pubsub-light-guava-provider module.
     *
     * @return a singleton instance of the default {@link PubsubBus} implementation.
     */
    public synchronized static @Nonnull
    PubsubBus getBus() {
        ClassLoader loader;
        if (pubsubBus == null) {
            // use the Jenkins plugins uber class loader if available to ensure that the needed provider is visible to this plugin
            try {
                Class.forName("jenkins.model.Jenkins", false, PubsubBus.class.getClassLoader());
                loader = Jenkins.getInstanceOrNull() != null ? Jenkins.getInstance().getPluginManager().uberClassLoader : PubsubBus.class.getClassLoader();
            } catch (ClassNotFoundException e) {
                loader = PubsubBus.class.getClassLoader();
            }

            final ServiceLoader<PubsubBus> busLoader = ServiceLoader.load(PubsubBus.class, loader);
            if (busLoader.iterator().hasNext()) {
                pubsubBus = busLoader.iterator().next();
                LOGGER.log(Level.FINER, "getBus() - instantiated pubsubBus={0} impl", pubsubBus.getClass().getSimpleName());
            } else {
                throw new IllegalStateException("unable to find any PubsubBus implementations");
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
        LOGGER.log(Level.FINER, "publish() - message={0}", message.toString());

        String channelName = message.getChannelName();
        String eventName = message.getEventName();

        if (channelName == null || channelName.length() == 0) {
            throw new MessageException(String.format("Channel name property '%s' not set on the Message instance.", CommonEventProps.channel_name));
        }
        if (eventName == null || eventName.length() == 0) {
            throw new MessageException(String.format("Event name property '%s' not set on the Message instance.", CommonEventProps.event_name));
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
     * @param authentication The authentication to which the subscription is associated.
     * @param eventFilter    A message filter, or {@code null} if no filtering is to be applied.
     *                       This tells the bus to only forward messages that match the properties
     *                       (names and values) specified in the filter.
     */
    public abstract void subscribe(@Nonnull String channelName,
                                   @Nonnull ChannelSubscriber subscriber,
                                   @Nonnull Authentication authentication,
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
