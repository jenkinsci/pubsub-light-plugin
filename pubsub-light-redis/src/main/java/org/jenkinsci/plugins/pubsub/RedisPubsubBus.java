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
import org.apache.commons.lang.StringUtils;
import redis.clients.jedis.*;

import javax.annotation.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import java.util.regex.*;

public class RedisPubsubBus extends PubsubBus {
    private static final Logger LOGGER = Logger.getLogger(RedisPubsubBus.class.getName());

    private static final Pattern CLIENT_INFO_SUB_PATTERN = Pattern.compile("^.* sub=(?<sub>\\d+) .*$");

    private final JedisPool jedisPool;

    private final RedisPubSubListener redisPubSubListener;

    /**
     * Single thread for running the blocking subscribe thread for this Pubsub instance.
     */
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Future<Void> subscribeFuture;

    private final Set<String> subscriptions = Collections.synchronizedSet(new HashSet<String>());

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
            }
        }

        List<? extends ChannelSubscriber> getSubscribers(final String channel) {
            final Set<FilteredChannelSubscriber> filteredChannelSubscribers = channelSubscribers.get(channel);
            final List<ChannelSubscriber> subscribers = new ArrayList<>();
            for (final FilteredChannelSubscriber filteredSubscriber : filteredChannelSubscribers) {
                subscribers.add(filteredSubscriber.getChannelSubscriber());
            }
            return channelSubscribers.get(channel) != null ? subscribers : new ArrayList<FilteredChannelSubscriber>();
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
    public void subscribe(@Nonnull final String channelName, @Nonnull final ChannelSubscriber channelSubscriber, @Nonnull final Authentication authentication, @CheckForNull final EventFilter eventFilter) {
        LOGGER.log(Level.FINER, "subscribe() - channelName={0}, subscriber={1}, authentication={2}, eventFilter={3}", new Object[]{channelName, channelSubscriber.toString(), authentication.getDetails(), eventFilter != null ? eventFilter.toJSON() : "null"});

        // start the subscribe thread for this instance if not already running and add the given (single) channel subscription
        if (subscriptions.add(channelName)) {
            if (subscribeFuture == null) {
                initSubscribeThread(channelName);
            } else {
                synchronized (redisPubSubListener) {
                    LOGGER.log(Level.FINER, "subscribe() - adding subscription to channelName={0} to existing redis pubsub client", channelName);
                    redisPubSubListener.subscribe(subscriptions.toArray(new String[0]));
                }
            }
        }

        synchronized (redisPubSubListener) {
            // add the given ChannelSubscriber to the given channel's callback list
            redisPubSubListener.subscribe(channelName, new FilteredChannelSubscriber(channelSubscriber, eventFilter));
        }
    }

    private void initSubscribeThread(@Nonnull final String channelName) {
        final String clientName = UUID.randomUUID().toString();

        final Integer nSubscriptions = getNSubscriptionsForClient(clientName);

        @SuppressWarnings("unchecked") final Callable<Void> callable = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                // the subscribe call blocks and stays open for the duration of the PubsubBus
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.clientSetname(clientName);
                    LOGGER.log(Level.FINER, "call() - opening blocking pubsub connection to redis, initial " +
                            "channelName={0}, clientName={1}", new Object[]{channelName, clientName});
                    jedis.subscribe(redisPubSubListener, channelName);
                    return null;
                }
            }
        };

        synchronized (redisPubSubListener) {
            subscribeFuture = executorService.submit(callable);
            // wait here until the client reports the number of subs has increased, for at most ~1 second
            for (int i = 0; i < 100; i++) {
                if (getNSubscriptionsForClient(clientName) == nSubscriptions + 1) {
                    return;
                }
                try {
                    Thread.sleep(10);
                } catch (final InterruptedException e) {
                    LOGGER.log(Level.SEVERE, "unexpected exception waiting for client subscription, channelName={0}, " +
                            "clientName={1}", new Object[]{channelName, clientName});
                    Throwables.propagate(e);
                }
            }
            LOGGER.log(Level.WARNING, "missing indication that client subscription was successful, channelName={}, clientName={}", new Object[]{channelName, clientName});
        }
    }

    /**
     * Returns the number of channel subscriptions owned by the client with the given name.
     */
    private int getNSubscriptionsForClient(final String clientName) {
        try (Jedis jedis = jedisPool.getResource()) {
            final String clientList = jedis.clientList();
            final String[] clientInfos = StringUtils.split(clientList, System.getProperty("line.separator"));
            for (final String info : clientInfos) {
                if (info.contains(clientName)) {
                    final Matcher matcher = CLIENT_INFO_SUB_PATTERN.matcher(info);
                    if (matcher.matches()) {
                        final Integer n = Integer.valueOf(matcher.group("sub"));
                        LOGGER.log(Level.FINER, "clientName={0}, nSubscriptions={1}", new Object[]{clientName, n});
                        return n;
                    }
                    LOGGER.log(Level.WARNING, "unable to find number of subscriptions for channelName={0}, clientInfo={1}", new Object[]{clientName, info});
                }
            }
            return 0;
        }
    }

    @Override
    public void unsubscribe(@Nonnull final String channelName, @Nonnull final ChannelSubscriber channelSubscriber) {
        LOGGER.log(Level.FINER, "unsubscribe() - channelName={0}, subscriber={1}", new Object[]{channelName, channelSubscriber.toString()});

        synchronized (redisPubSubListener) {
            // remove the given ChannelSubscriber to the given channel's callback list
            redisPubSubListener.unsubscribe(channelName, channelSubscriber);
        }

        synchronized (redisPubSubListener) {
            // if no ChannelSubscribers remain, unsub from redis
            if (redisPubSubListener.getSubscribers(channelName).size() == 0) {
                subscriptions.remove(channelName);
                redisPubSubListener.unsubscribe(channelName);
            }
        }
    }

    @Override
    public void shutdown() {
        if (redisPubSubListener != null) {
            if(redisPubSubListener.isSubscribed()) {
                redisPubSubListener.unsubscribe(subscriptions.toArray(new String[0]));
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
