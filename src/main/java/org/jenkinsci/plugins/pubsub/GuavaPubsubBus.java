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
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.ACL;
import hudson.util.CopyOnWriteMap;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.security.core.Authentication;

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
public final class GuavaPubsubBus extends PubsubBus {
    
    private final Map<String, EventBus> channels = new CopyOnWriteMap.Hash<>();
    private final Map<ChannelSubscriber, GuavaSubscriber> subscribers = new CopyOnWriteMap.Hash<>();
    private ExecutorService executor;
    private final int MAX_THREADS = Integer.getInteger(GuavaPubsubBus.class.getName() + ".MAX_THREADS", 5);

    public GuavaPubsubBus() {
        start();
    }

    @NonNull
    @Override
    protected ChannelPublisher publisher(@NonNull String channelName) {
        final EventBus channelBus = getChannelBus(channelName);
        return channelBus::post;
    }

    @Override
    public void subscribe2(@NonNull String channelName, @NonNull ChannelSubscriber subscriber, @NonNull Authentication authentication, @CheckForNull EventFilter eventFilter) {
        GuavaSubscriber guavaSubscriber = new GuavaSubscriber(subscriber, authentication, eventFilter);
        EventBus channelBus = getChannelBus(channelName);
        channelBus.register(guavaSubscriber);
        subscribers.put(subscriber, guavaSubscriber);
    }

    @Override
    public void unsubscribe(@NonNull String channelName, @NonNull ChannelSubscriber subscriber) {
        GuavaSubscriber guavaSubscriber = subscribers.remove(subscriber);
        if (guavaSubscriber != null) {
            EventBus channelBus = getChannelBus(channelName);
            channelBus.register(guavaSubscriber);
            channelBus.unregister(guavaSubscriber);
        }
    }

    @Override
    public void start(){
        if(executor!=null&&!executor.isShutdown()){
            return;
        }
        // Might want to make the executor configuration configurable.
        executor = new ThreadPoolExecutor( 0, MAX_THREADS, 10L, TimeUnit.SECONDS,
                                           new LinkedBlockingQueue<>(),
                                           r -> new Thread( r, "GuavaPubsubBus.ThreadPoolExecutor" ));
    }

    @Override
    public void shutdown() {
        if (!executor.isShutdown()) {
            executor.shutdown();
        }
    }

    private EventBus getChannelBus(String channelName) {
        EventBus channelBus = channels.get(channelName);
        if (channelBus == null) {
            channelBus = new AsyncEventBus(channelName, executor);
            channels.put(channelName, channelBus);
        }
        return channelBus;
    }
    
    private static class GuavaSubscriber {
        private ChannelSubscriber subscriber;
        private Authentication authentication;
        private final EventFilter eventFilter;

        public GuavaSubscriber(@NonNull ChannelSubscriber subscriber, Authentication authentication, EventFilter eventFilter) {
            this.subscriber = subscriber;
            if (authentication != null) {
                this.authentication = authentication;
            } else {
                this.authentication = Jenkins.ANONYMOUS2;
            }
            this.eventFilter = eventFilter;
        }

        @Subscribe
        public void onMessage(@NonNull final Message message) {
            if (eventFilter != null && !message.containsAll(eventFilter)) {
                // Don't deliver the message.
                return;
            }
            if (message instanceof AccessControlledMessage) {
                if (authentication != null) {
                    final AccessControlledMessage accMessage = (AccessControlledMessage) message;
                    try (var ignored = ACL.as2(authentication)) {
                        if (accMessage.hasPermission(accMessage.getRequiredPermission())) {
                            subscriber.onMessage(message.clone());
                        }
                    }
                }
            } else {
                subscriber.onMessage(message.clone());
            }
        }
    }
}
