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

import com.google.common.eventbus.EventBus;
import hudson.ExtensionList;
import hudson.ExtensionListListener;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.pubsub.exception.MessageException;
import org.jenkinsci.plugins.pubsub.listeners.SyncQueueListener;
import org.jenkinsci.plugins.pubsub.message.AccessControlledMessage;
import org.jenkinsci.plugins.pubsub.message.EventFilter;
import org.jenkinsci.plugins.pubsub.message.Message;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link PubsubBus} implementation utilizing {@link EventBus} for Jenkins plugins.
 * <p>
 * Use system property <strong><code>org.jenkins.pubsub.GuavaPubsubBus.MAX_THREADS</code></strong> to configure the
 * thread pool size used by the bus. The default value is 5 threads (falling back to 0 when idle).
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public final class JenkinsGuavaPubsubBus extends GuavaPubsubBus {
    private static final Logger LOGGER = Logger.getLogger(JenkinsGuavaPubsubBus.class.getName());

    private static List<AbstractChannelSubscriber> autoSubscribers = new CopyOnWriteArrayList<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    SyncQueueListener.shutdown();
                } finally {
                    if (pubsubBus != null) {
                        try {
                            unregisterAutoChannelSubscribers(pubsubBus);
                        } finally {
                            pubsubBus.shutdown();
                        }
                    }
                }
            }
        });
    }

    /**
     * Get the installed {@link PubsubBus} implementation or build a new one.
     * <p>
     * Use {@link PubsubBus#getBus()} to obtain a PubsubBus instance, not this method.
     *
     * @return The installed {@link PubsubBus} implementation, or default
     * implementation if none are found.
     */
    private synchronized static @Nonnull PubsubBus getJenkinsBus() {
        if (pubsubBus == null) {
            ExtensionList<PubsubBus> installedBusImpls = ExtensionList.lookup(PubsubBus.class);
            if (!installedBusImpls.isEmpty()) {
                pubsubBus = installedBusImpls.get(0);
            } else {
                pubsubBus = new JenkinsGuavaPubsubBus();
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
        return pubsubBus;
    }

    @Override
    public void publish(@Nonnull final Message message) throws MessageException {
        if (message instanceof AccessControlledMessage) {
            AccessControlled accessControlled = ((AccessControlledMessage) message).getAccessControlled();
            if (accessControlled != null) {
                message.set(JenkinsEventProps.Jenkins.jenkins_object_type, accessControlled.getClass().getName());
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

        super.publish(message);
    }

    @Override
    public void subscribe(@Nonnull String channelName, @Nonnull ChannelSubscriber subscriber, @Nonnull Principal principal, @CheckForNull EventFilter eventFilter) {
        GuavaSubscriber jenkinsGuavaSubscriber = new JenkinsGuavaSubscriber(subscriber, principal, eventFilter);
        EventBus channelBus = getChannelBus(channelName);
        channelBus.register(jenkinsGuavaSubscriber);
        getSubscribers().put(subscriber, jenkinsGuavaSubscriber);
    }

    protected static class JenkinsGuavaSubscriber extends GuavaSubscriber {
        private Authentication authentication;

        public JenkinsGuavaSubscriber(@Nonnull ChannelSubscriber subscriber, Principal principal, EventFilter eventFilter) {
            super(subscriber, principal, eventFilter);
            if (principal != null) {
                this.authentication = (Authentication) principal;
            } else {
                this.authentication = Jenkins.ANONYMOUS;
            }
        }

        @Override
        protected void handleMessage(final Message message) {
            if (getEventFilter() != null && !message.containsAll(getEventFilter())) {
                // Don't deliver the message.
                return;
            }
            if (message instanceof AccessControlledMessage) {
                if (authentication != null) {
                    final AccessControlledMessage accMessage = (AccessControlledMessage) message;
                    ACL.impersonate(authentication, new Runnable() {
                        @Override
                        public void run() {
                            if (accMessage.hasPermission(accMessage.getRequiredPermission())) {
                                getSubscriber().onMessage(message.clone());
                            }
                        }
                    });
                }
            } else {
                getSubscriber().onMessage(message.clone());
            }
        }
    }

    /**
     * Channel subscription can be managed by another ExtensionPoint impl, or can
     * be triggered automatically by implementing {@link AbstractChannelSubscriber}.
     *
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
