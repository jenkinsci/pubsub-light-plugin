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

import hudson.ExtensionList;
import hudson.ExtensionListListener;
import hudson.ExtensionPoint;
import hudson.security.AccessControlled;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.pubsub.listeners.SyncQueueListener;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract Pub-sub bus.
 * 
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public abstract class PubsubBus implements ExtensionPoint {

    private static final Logger LOGGER = Logger.getLogger(PubsubBus.class.getName());

    private static List<AbstractChannelSubscriber> autoSubscribers = new CopyOnWriteArrayList<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    SyncQueueListener.shutdown();
                } finally {
                    if (Holder.pubsubBus != null) {
                        try {
                            unregisterAutoChannelSubscribers(Holder.pubsubBus);
                        } finally {
                            Holder.pubsubBus.shutdown();
                        }
                    }
                }
            }
        });
    }

    private static final class Holder {
        static final PubsubBus pubsubBus;

        static {
            ExtensionList<PubsubBus> installedBusImpls = ExtensionList.lookup(PubsubBus.class);
            if (!installedBusImpls.isEmpty()) {
                pubsubBus = installedBusImpls.get(0);
            } else {
                pubsubBus = new GuavaPubsubBus();
            }

            // Register the auto-subscribers.
            registerAutoChannelSubscribers(pubsubBus);
            // And listen for new ones being installed e.g. after a plugin is installed.
            ExtensionList.lookup(AbstractChannelSubscriber.class).addListener(new ExtensionListListener() {
                @Override
                public void onChange() {
                    registerAutoChannelSubscribers(pubsubBus);
                }
            });
        }
    }

    
    /**
     * Get the installed {@link PubsubBus} implementation.
     * @return The installed {@link PubsubBus} implementation, or default
     * implementation if none are found.
     */
    public static @Nonnull PubsubBus getBus() {
        return Holder.pubsubBus;
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
            throw new MessageException(String.format("Channel name property '%s' not set on the Message instance.", EventProps.Jenkins.jenkins_channel));
        }
        if (eventName == null || eventName.length() == 0) {
            throw new MessageException(String.format("Event name property '%s' not set on the Message instance.", EventProps.Jenkins.jenkins_event));
        }

        // Make sure the channel name is set on the message.
        // In case getChannelName is overridden.
        message.setChannelName(channelName);

        if (message instanceof AccessControlledMessage) {
            AccessControlled accessControlled = ((AccessControlledMessage) message).getAccessControlled();
            if (accessControlled != null) {
                message.set(EventProps.Jenkins.jenkins_object_type, accessControlled.getClass().getName());
            }
        }

        // Apply event enrichers.
        ExtensionList<MessageEnricher> messageEnrichers = ExtensionList.lookup(MessageEnricher.class);
        if (!messageEnrichers.isEmpty()) {
            for (MessageEnricher enricher : messageEnrichers) {
                try {
                    enricher.enrich(message);
                } catch (Exception e) {
                    throw new MessageException(String.format("Event enrichment failure due to unexpected exception in %s.", enricher.getClass().getName()), e);
                }
            }
        }

        // No publish it...
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
     * @param authentication The authentication to which the subscription is associated.
     * @param eventFilter A message filter, or {@code null} if no filtering is to be applied.
     *                    This tells the bus to only forward messages that match the properties
     *                    (names and values) specified in the filter.
     */
    public abstract void subscribe(@Nonnull String channelName,
                                       @Nonnull ChannelSubscriber subscriber,
                                       @Nonnull Authentication authentication,
                                       @CheckForNull EventFilter eventFilter);

    /**
     * Unsubscribe from events on the specified event channel.
     * @param channelName The channel name.
     * @param subscriber  The subscriber instance that was used to receive events.
     */
    public abstract void unsubscribe(@Nonnull String channelName,
                                       @Nonnull ChannelSubscriber subscriber);

    /**
     * Shutdown the bus.
     */
    public abstract void shutdown();

    /**
     * Channel subscription can be managed by another ExtensionPoint impl, or can
     * be triggered automatically by implementing {@link AbstractChannelSubscriber}.
     * @param pubsubBus The bus instance.
     */
    private synchronized static void registerAutoChannelSubscribers(PubsubBus pubsubBus) {
        List<AbstractChannelSubscriber> newAutoSubscribersList = new CopyOnWriteArrayList<>();
        ExtensionList<AbstractChannelSubscriber> subscribers = ExtensionList.lookup(AbstractChannelSubscriber.class);

        for (AbstractChannelSubscriber subscriber : subscribers) {
            // If it's not already subscribed, subscribe it.
            if (!autoSubscribers.contains(subscriber)) {
                pubsubBus.subscribe(
                        subscriber.getChannelName(),
                        subscriber,
                        subscriber.getAuthentication(),
                        subscriber.getEventFilter());
            }
            newAutoSubscribersList.add(subscriber);
        }

        autoSubscribers = newAutoSubscribersList;
    }

    private synchronized static void unregisterAutoChannelSubscribers(PubsubBus pubsubBus) {
        for (AbstractChannelSubscriber subscriber : autoSubscribers) {
            try {
                pubsubBus.unsubscribe(subscriber.getChannelName(), subscriber);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error doing auto unsubscribe.", e);
            }
        }
    }
}
