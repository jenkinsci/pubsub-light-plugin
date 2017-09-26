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

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.jenkinsci.plugins.pubsub.message.EventFilter;
import org.jenkinsci.plugins.pubsub.message.Message;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Default {@link PubsubBus} implementation.
 * <p>
 * An in-memory implementation based on <a href="https://github.com/google/guava/wiki/EventBusExplained">Google's Guava EventBus</a>.
 * <p>
 * Use system property <strong><code>org.jenkins.pubsub.GuavaPubsubBus.MAX_THREADS</code></strong> to configure the
 * thread pool size used by the bus. The default value is 5 threads (falling back to 0 when idle).
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class GuavaPubsubBus extends PubsubBus {

    private final Map<String, EventBus> channels = new ConcurrentHashMap<>();
    private final Map<ChannelSubscriber, GuavaSubscriber> subscribers = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final int MAX_THREADS = Integer.getInteger(GuavaPubsubBus.class.getName() + ".MAX_THREADS", 5);

    public GuavaPubsubBus() {
        // Might want to make the executor configuration configurable.
        executor = new ThreadPoolExecutor(0, MAX_THREADS, 10L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    }

    @Nonnull
    @Override
    protected ChannelPublisher publisher(@Nonnull String channelName) {
        final EventBus channelBus = getChannelBus(channelName);
        return new ChannelPublisher() {
            public void publish(@Nonnull Message message) {
                channelBus.post(message);
            }
        };
    }

    @Override
    public void subscribe(@Nonnull String channelName, @Nonnull ChannelSubscriber subscriber, @Nonnull Principal principal, @CheckForNull EventFilter eventFilter) {
        GuavaSubscriber guavaSubscriber = new GuavaSubscriber(subscriber, principal, eventFilter);
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

    @Override
    public void shutdown() {
        if (!executor.isShutdown()) {
            executor.shutdown();
        }
    }

    protected EventBus getChannelBus(String channelName) {
        EventBus channelBus = channels.get(channelName);
        if (channelBus == null) {
            channelBus = new AsyncEventBus(channelName, executor);
            channels.put(channelName, channelBus);
        }
        return channelBus;
    }

    protected static class GuavaSubscriber {
        private ChannelSubscriber subscriber;
        private Principal principal;
        private final EventFilter eventFilter;

        public GuavaSubscriber(@Nonnull ChannelSubscriber subscriber, Principal principal, EventFilter eventFilter) {
            this.subscriber = subscriber;
            this.principal = principal;
            this.eventFilter = eventFilter;
        }

        @Subscribe
        public void onMessage(@Nonnull final Message message) {
            handleMessage(message);
        }

        /**
         * Extra method needed since @Subscribe annotation is not inherited.
         */
        protected void handleMessage(final Message message) {
            if (eventFilter != null && !message.containsAll(eventFilter)) {
                // Don't deliver the message.
                return;
            }
            subscriber.onMessage(message.clone());
        }

        public ChannelSubscriber getSubscriber() {
            return subscriber;
        }

        public Principal getPrincipal() {
            return principal;
        }

        public EventFilter getEventFilter() {
            return eventFilter;
        }
    }

    public Map<String, EventBus> getChannels() {
        return channels;
    }

    public Map<ChannelSubscriber, GuavaSubscriber> getSubscribers() {
        return subscribers;
    }

    public ExecutorService getExecutor() {
        return executor;
    }
}
