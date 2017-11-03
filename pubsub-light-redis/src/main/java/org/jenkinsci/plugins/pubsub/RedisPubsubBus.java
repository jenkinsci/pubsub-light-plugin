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

import com.google.common.base.Throwables;
import org.acegisecurity.Authentication;
import redis.clients.jedis.*;

import javax.annotation.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class RedisPubsubBus extends PubsubBus {
    private static final Logger LOGGER = Logger.getLogger(RedisPubsubBus.class.getName());

    private final JedisPool jedisPool;
    private final RedisPubSubListener redisPubSubListener;

    /**
     * Single thread for running the blocking subscribe thread for this Pubsub instance.
     */
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Future<Void> subscribeFuture;

    public RedisPubsubBus() {
        RedisConfig redisConfig;
        final ServiceLoader<RedisConfig> loader = ServiceLoader.load(RedisConfig.class);
        if (loader.iterator().hasNext()) {
            redisConfig = loader.iterator().next();
            LOGGER.log(Level.FINER, "RedisPubsubBus() - instantiated redisConfig={0} impl", redisConfig.getClass().getSimpleName());
        } else {
            redisConfig = new DefaultRedisConfig();
        }

        final JedisPoolConfig config = new JedisPoolConfig();
        config.setMinIdle(2);

        jedisPool = new JedisPool(config, redisConfig.getRedisHost(), redisConfig.getRedisPort(), redisConfig.isRedisSSL());

        redisPubSubListener = new RedisPubSubListener();
    }

    private class DefaultRedisConfig implements RedisConfig {
        @Override
        public String getRedisHost() {
            return "localhost";
        }

        @Override
        public int getRedisPort() {
            return 6379;
        }

        @Override
        public boolean isRedisSSL() {
            return false;
        }
    }

    /**
     * Jedis abstraction for handling redis pub/sub events.
     */
    static class RedisPubSubListener extends JedisPubSub {
        private static final Logger LOGGER = Logger.getLogger(RedisPubSubListener.class.getName());

        /**
         * All {@link ChannelSubscriber} associated with the given channel name.
         */
        private final Map<String, Set<FilteredChannelSubscriber>> channelSubscribers = new ConcurrentHashMap<>();

        @Override
        public void onMessage(final String channel, final String message) {
            LOGGER.log(Level.FINEST, "onMessage() - channel={0}, message={1}", new Object[]{channel, message});
            final Set<FilteredChannelSubscriber> subscribers = channelSubscribers.get(channel);
            for (final ChannelSubscriber subscriber : subscribers) {
                subscriber.onMessage(Message.fromString(message));
            }
        }

        @Override
        public void onSubscribe(final String channel, final int subscribedChannels) {
            LOGGER.log(Level.FINER, "onSubscribe() - channel={0}, subscribedChannels={1}", new Object[]{channel, subscribedChannels});
        }

        @Override
        public void onUnsubscribe(final String channel, final int subscribedChannels) {
            LOGGER.log(Level.FINER, "onUnsubscribe() - channel={0}, subscribedChannels={1}", new Object[]{channel, subscribedChannels});
        }

        void subscribe(final String channel, final FilteredChannelSubscriber subscriber) {
            if (channelSubscribers.get(channel) == null) {
                channelSubscribers.put(channel, new HashSet<FilteredChannelSubscriber>());
                subscribe(channelSubscribers.keySet().toArray(new String[0]));
                waitForSubscriptionNChange(channelSubscribers.keySet().size());
            }
            channelSubscribers.get(channel).add(subscriber);
        }

        void unsubscribe(final String channel, final ChannelSubscriber channelSubscriber) {
            if (channelSubscribers.get(channel) != null) {
                final Iterator<FilteredChannelSubscriber> subscribers = channelSubscribers.get(channel).iterator();
                while (subscribers.hasNext()) {
                    if (subscribers.next().getChannelSubscriber().equals(channelSubscriber)) {
                        subscribers.remove();
                    }
                }

                if (channelSubscribers.get(channel).isEmpty()) {
                    channelSubscribers.remove(channel);
                    unsubscribe(channel);
                    waitForSubscriptionNChange(channelSubscribers.keySet().size());
                }
            }
        }

        void unsubscribeAll() {
            unsubscribe(channelSubscribers.keySet().toArray(new String[0]));
            channelSubscribers.clear();
            waitForSubscriptionNChange(0);
        }

        /**
         * Blocking wait for the number of channel subscriptions reported by the listener to become expected.
         */
        private void waitForSubscriptionNChange(final int expectedN) {
            Callable<Void> waitForSubscription = new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    int actualN;
                    do {
                        actualN = getSubscribedChannels();
                        LOGGER.log(Level.FINEST, "call() - waiting for nSubs to change, actualN={0}, expectedN={1}", new Object[]{actualN, expectedN});
                    } while (actualN != expectedN);
                    return null;
                }
            };

            try {
                Executors.newSingleThreadExecutor().submit(waitForSubscription).get(5, TimeUnit.SECONDS);
            } catch (final TimeoutException e) {
                LOGGER.log(Level.SEVERE, "timeout waiting for client un/subscription");
                Throwables.propagate(e);
            } catch (final Exception e) {
                LOGGER.log(Level.WARNING, "unexpected exception waiting for client un/subscription");
            }
        }
    }

    /**
     * {@link ChannelSubscriber} decorator to provide filtering for onMessage.
     */
    static class FilteredChannelSubscriber implements ChannelSubscriber {
        private final ChannelSubscriber channelSubscriber;

        private final EventFilter eventFilter;

        FilteredChannelSubscriber(final ChannelSubscriber channelSubscriber, final EventFilter eventFilter) {
            this.channelSubscriber = channelSubscriber;
            this.eventFilter = eventFilter;
        }

        @Override
        public void onMessage(@Nonnull final Message message) {
            if (eventFilter != null && !message.containsAll(eventFilter)) {
                // don't send messages that don't contain all key/value pairs contained in the given EventFilter
                return;
            }
            channelSubscriber.onMessage(message);
        }

        ChannelSubscriber getChannelSubscriber() {
            return channelSubscriber;
        }

    }

    @Nonnull
    @Override
    protected ChannelPublisher publisher(@Nonnull final String channelName) {
        return new ChannelPublisher() {
            @Override
            public void publish(@Nonnull final Message message) {
                LOGGER.log(Level.FINER, "publish() - channelName={0}, message={1}", new Object[]{channelName, message.toJSON()});
                try (Jedis jedis = jedisPool.getResource()) {
                    LOGGER.log(Level.FINER, "publish() - jedis={0}, active={1}, idle={2}, waiters={3}", new Object[]{jedis, jedisPool.getNumActive(), jedisPool.getNumIdle(), jedisPool.getNumWaiters()});
                    jedis.publish(channelName, message.toJSON());
                }
            }
        };
    }

    @Override
    public void subscribe(@Nonnull final String channelName, @Nonnull final ChannelSubscriber channelSubscriber,
                          @Nonnull final Authentication authentication, @CheckForNull final EventFilter eventFilter) {

        LOGGER.log(Level.FINER, "subscribe() - channelName={0}, subscriber={1}, authentication={2}, eventFilter={3}",
                new Object[]{channelName, channelSubscriber.toString(), authentication.getDetails(), eventFilter != null ? eventFilter.toJSON() : "null"});

        synchronized (redisPubSubListener) {
            // start the subscribe thread for this instance if not already running and add the given (single) channel subscription
            if (subscribeFuture == null) {
                LOGGER.log(Level.FINER, "subscribe() - initializing subscription thread and subscribing to channelName={0}", channelName);
                initSubscribeThread(channelName);
                redisPubSubListener.waitForSubscriptionNChange(1);
            } else {
                LOGGER.log(Level.FINER, "subscribe() - adding subscription to channelName={0} to existing redis pubsub client", channelName);
            }

            redisPubSubListener.subscribe(channelName, new FilteredChannelSubscriber(channelSubscriber, eventFilter));
        }
    }

    private void initSubscribeThread(@Nonnull final String channelName) {
        @SuppressWarnings("unchecked") final Callable<Void> callable = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                // the subscribe call blocks and stays open for the duration of the PubsubBus
                try (Jedis jedis = jedisPool.getResource()) {
                    final String clientName = UUID.randomUUID().toString();
                    jedis.clientSetname(clientName);
                    LOGGER.log(Level.FINER, "call() - opening blocking pubsub connection to redis, initial " +
                            "channelName={0}, clientName={1}", new Object[]{channelName, clientName});
                    jedis.subscribe(redisPubSubListener, channelName);
                    return null;
                }
            }
        };

        subscribeFuture = executorService.submit(callable);
    }

    @Override
    public void unsubscribe(@Nonnull final String channelName, @Nonnull final ChannelSubscriber channelSubscriber) {
        LOGGER.log(Level.FINER, "unsubscribe() - channelName={0}, subscriber={1}", new Object[]{channelName, channelSubscriber.toString()});

        synchronized (redisPubSubListener) {
            // remove the given ChannelSubscriber to the given channel's callback list and if no ChannelSubscribers remain, unsub from redis
            redisPubSubListener.unsubscribe(channelName, channelSubscriber);
        }
    }

    @Override
    public void shutdown() {
        if (redisPubSubListener != null) {
            if (redisPubSubListener.isSubscribed()) {
                redisPubSubListener.unsubscribeAll();
            }
        }
        if (!executorService.isShutdown()) {
            executorService.shutdown();
        }
        if (subscribeFuture != null) {
            subscribeFuture.cancel(true);
        }
        if (jedisPool != null) {
            jedisPool.destroy();
        }
    }

    /**
     * Returns the list of channels subscribed to in Redis (by 1 or more clients).
     */
    List<String> getSubscriptions() {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.pubsubChannels("*");
        }
    }
}
