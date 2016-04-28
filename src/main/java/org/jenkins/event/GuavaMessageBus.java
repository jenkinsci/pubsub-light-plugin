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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.util.CopyOnWriteMap;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Properties;

/**
 * Default {@link MessageBus} implementation.
 * <p/>
 * An in-memory implementation based on <a href="https://github.com/google/guava/wiki/EventBusExplained">Google's Guava EventBus</a>.
 * 
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
class GuavaMessageBus extends MessageBus {
    
    private Map<String, EventBus> channels = new CopyOnWriteMap.Hash<String, EventBus>();
    private Map<ChannelSubscriber, GuavaSubscriber> subscribers = new CopyOnWriteMap.Hash<ChannelSubscriber, GuavaSubscriber>();
    
    @Nonnull
    @Override
    public ChannelPublisher newPublisher(@Nonnull String channelName) {
        final EventBus channelBus = getChannelBus(channelName);
        return new ChannelPublisher() {
            public void publish(@Nonnull Message message) {
                channelBus.post(message);
            }
        };
    }

    @Override
    public void subscribe(@Nonnull String channelName, @Nonnull ChannelSubscriber subscriber, @Nonnull User user, @CheckForNull Properties eventFilter) {
        GuavaSubscriber guavaSubscriber = new GuavaSubscriber(subscriber, user, eventFilter);
        EventBus channelBus = getChannelBus(channelName);
        channelBus.register(guavaSubscriber);
        subscribers.put(subscriber, guavaSubscriber);
    }

    @Override
    public void unsubscribe(@Nonnull String channelName, @Nonnull ChannelSubscriber subscriber) {
        GuavaSubscriber guavaSubscriber = subscribers.remove(subscriber);
        if (guavaSubscriber != null) {
            EventBus channelBus = getChannelBus(channelName);
            channelBus.register(guavaSubscriber);
            channelBus.unregister(guavaSubscriber);
        }
    }

    private EventBus getChannelBus(String channelName) {
        EventBus channelBus = channels.get(channelName);
        if (channelBus == null) {
            channelBus = new EventBus(channelName);
            channels.put(channelName, channelBus);
        }
        return channelBus;
    }
    
    private class GuavaSubscriber {
        private ChannelSubscriber subscriber;
        private final Authentication authentication;
        private final Properties eventFilter;

        public GuavaSubscriber(@Nonnull ChannelSubscriber subscriber, @Nonnull User user, Properties eventFilter) {
            this.subscriber = subscriber;
            this.authentication = user.impersonate();
            this.eventFilter = eventFilter;
        }

        @Subscribe
        public void onMessage(@Nonnull final Message message) {
            if (eventFilter != null && !message.containsAll(eventFilter)) {
                // Don't deliver the message.
                return;
            }
            if (message instanceof AccessControlledMessage) {
                final AccessControlledMessage accMessage = (AccessControlledMessage) message;
                ACL.impersonate(authentication, new Runnable() {
                    @Override
                    public void run() {
                        if (accMessage.hasPermission(accMessage.getRequiredPermission())) {
                            subscriber.onMessage(message.clone());
                        }
                    }
                });
            } else {
                subscriber.onMessage(message.clone());
            }
        }
    }
}
