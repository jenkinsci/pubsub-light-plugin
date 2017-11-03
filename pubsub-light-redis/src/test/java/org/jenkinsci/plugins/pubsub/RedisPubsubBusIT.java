package org.jenkinsci.plugins.pubsub;

import com.google.common.collect.*;
import org.acegisecurity.providers.TestingAuthenticationToken;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.*;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class RedisPubsubBusIT {
    private static final String CHANNEL_NAME = "job";
    private static final TestingAuthenticationToken AUTHENTICATION = new TestingAuthenticationToken(null, null, null);

    private RedisPubsubBus testBus;

    @Before
    public void setUp() throws Exception {
        testBus = new RedisPubsubBus();
    }

    @After
    public void tearDown() throws Exception {
        testBus.shutdown();
        Thread.sleep(100);
    }

    @Test
    public void publish_nonCommonMessage() throws Exception {
        final MockSubscriber subscriber = new MockSubscriber();

        // method under test
        testBus.subscribe(CHANNEL_NAME, subscriber, AUTHENTICATION, null);

        for (Integer i = 0; i < 3; i++) {
            final JobMessage jobMessage = new JobMessage();
            jobMessage.setEventName("event" + i.toString());
            testBus.publisher(CHANNEL_NAME).publish(jobMessage);
        }

        subscriber.waitForMessageCount(3);

        final List<Message> publishedMessages = subscriber.getMessages();
        for (Integer i = 0; i < 3; i++) {
            assertEquals("job", publishedMessages.get(i).get("jenkins_channel"));
            assertEquals("event" + i.toString(), publishedMessages.get(i).get("jenkins_event"));
        }
    }

    @Test
    public void subscribe_simple() throws Exception {
        final MockSubscriber subscriber = new MockSubscriber();

        // method under test
        testBus.subscribe(CHANNEL_NAME, subscriber, AUTHENTICATION, null);

        for (int i = 0; i < 3; i++) {
            testBus.publisher(CHANNEL_NAME).publish(new SimpleMessage());
        }

        subscriber.waitForMessageCount(3);

        waitForRedisSubscriptionCount(1);
        assertTrue(testBus.getSubscriptions().containsAll(Lists.newArrayList(CHANNEL_NAME)));
    }

    @Test
    public void subscribe_multiple() throws Exception {
        final MockSubscriber subscriber1 = new MockSubscriber();
        final MockSubscriber subscriber2 = new MockSubscriber();

        // method under test
        testBus.subscribe(CHANNEL_NAME, subscriber1, AUTHENTICATION, null);
        testBus.subscribe(CHANNEL_NAME, subscriber2, AUTHENTICATION, null);

        for (int i = 0; i < 3; i++) {
            testBus.publisher(CHANNEL_NAME).publish(new SimpleMessage());
        }

        subscriber1.waitForMessageCount(3);
        subscriber2.waitForMessageCount(3);

        waitForRedisSubscriptionCount(1);
        assertTrue(testBus.getSubscriptions().containsAll(Lists.newArrayList(CHANNEL_NAME)));
    }

    @Test
    public void subscribeUnsubscribe() throws Exception {
        final MockSubscriber subscriber1_channel1 = new MockSubscriber();
        final MockSubscriber subscriber2_channel1 = new MockSubscriber();
        final MockSubscriber subscriber1_channel2 = new MockSubscriber();
        final MockSubscriber subscriber2_channel2 = new MockSubscriber();

        // method under test
        testBus.subscribe("channel1", subscriber1_channel1, AUTHENTICATION, null);
        testBus.subscribe("channel1", subscriber2_channel1, AUTHENTICATION, null);
        testBus.subscribe("channel2", subscriber1_channel2, AUTHENTICATION, null);
        testBus.subscribe("channel2", subscriber2_channel2, AUTHENTICATION, null);

        // only 2 channels are subscribed to, by 4 subscribers
        waitForRedisSubscriptionCount(2);
        assertTrue(testBus.getSubscriptions().containsAll(Lists.newArrayList("channel1", "channel2")));

        testBus.unsubscribe("channel2", subscriber1_channel2);

        // redis channel subscription should remain open
        waitForRedisSubscriptionCount(2);
        assertTrue(testBus.getSubscriptions().containsAll(Lists.newArrayList("channel1", "channel2")));

        testBus.unsubscribe("channel2", subscriber2_channel2);

        // now channel 2 sub should be closed
        waitForRedisSubscriptionCount(1);
        assertTrue(testBus.getSubscriptions().containsAll(Lists.newArrayList("channel1")));
    }

    @Test
    public void testChaos() throws Exception {
        final int CHAOS_LEVEL = 10;

        final ExecutorService executorService = Executors.newFixedThreadPool(CHAOS_LEVEL);

        final Random random = new Random();
        final Map<String, Set<ChannelSubscriber>> subscriptionIndex = new ConcurrentHashMap<>();

        final List<ChannelSubscriber> subscribers = new ArrayList<>();
        for (int i = 0; i < CHAOS_LEVEL; i++) {
            subscribers.add(new MockSubscriber());
        }

        final List<String> channelNames = new ArrayList<>();
        for (int i = 0; i < CHAOS_LEVEL; i++) {
            final String channelName = RandomStringUtils.randomAlphabetic(20);
            channelNames.add(channelName);
            subscriptionIndex.put(channelName, Collections.newSetFromMap(new ConcurrentHashMap<ChannelSubscriber, Boolean>()));
        }

        // randomly subscribe each channel name at least once
        for (int i = 0; i < CHAOS_LEVEL; i++) {
            for (final String channelName : channelNames) {
                final ChannelSubscriber subscriber = subscribers.get(random.nextInt(CHAOS_LEVEL));
                testBus.subscribe(channelName, subscriber, AUTHENTICATION, null);
                subscriptionIndex.get(channelName).add(subscriber);
            }
        }
        final List<Callable<Void>> threads = new ArrayList<>();

        for (int i = 0; i < CHAOS_LEVEL; i++) {
            final Callable<Void> chaosThread = new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    for (int i = 0; i < CHAOS_LEVEL; i++) {
                        // randomly subscribe a channel/subscriber
                        if (random.nextBoolean()) {
                            final String channelName = channelNames.get(random.nextInt(CHAOS_LEVEL));
                            final ChannelSubscriber subscriber = subscribers.get(random.nextInt(CHAOS_LEVEL));
                            testBus.subscribe(channelName, subscriber, AUTHENTICATION, null);
                            subscriptionIndex.get(channelName).add(subscriber);

                            waitForRedisSubscriptionCount(subscriptionIndex.size());
                            assertTrue(testBus.getSubscriptions().toString() + " " + subscriptionIndex.keySet().toString(), testBus.getSubscriptions().containsAll(subscriptionIndex.keySet()));
                        }

                        // randomly unsubscribe a channel/subscriber
                        if (random.nextBoolean()) {
                            final List<String> names = new ArrayList<>(subscriptionIndex.keySet());
                            Collections.shuffle(names);
                            final String randomName = names.get(0);

                            if (subscriptionIndex.get(randomName).size() > 0) {
                                final List<ChannelSubscriber> channelSubscribers = new ArrayList<>(subscriptionIndex.get(randomName));
                                Collections.shuffle(channelSubscribers);
                                final ChannelSubscriber randomSubscriber = channelSubscribers.get(0);

                                testBus.unsubscribe(randomName, randomSubscriber);
                                subscriptionIndex.get(randomName).remove(randomSubscriber);

                                waitForRedisSubscriptionCount(subscriptionIndex.size());
                                assertTrue(testBus.getSubscriptions().toString() + " " + subscriptionIndex.keySet().toString(), testBus.getSubscriptions().containsAll(subscriptionIndex.keySet()));
                            }
                        }

                        // randomly send some messages
                        if (random.nextBoolean()) {
                            final List<String> names = new ArrayList<>(subscriptionIndex.keySet());
                            Collections.shuffle(names);
                            final String randomName = names.get(0);

                            for (int j = 0; j < 3; j++) {
                                testBus.publisher(randomName).publish(new SimpleMessage());
                            }

                            final Set<ChannelSubscriber> channelSubscribers = subscriptionIndex.get(randomName);
                            for (final ChannelSubscriber channelSubscriber : channelSubscribers) {
                                ((MockSubscriber) channelSubscriber).waitForMessageCount(3);
                            }
                        }
                    }
                    return null;
                }
            };
            threads.add(chaosThread);
        }

        final List<Future<Void>> futures = executorService.invokeAll(threads);
        for (final Future<Void> future : futures) {
            future.get();
        }
    }

    private void waitForRedisSubscriptionCount(final int expectedCount) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (testBus.getSubscriptions().size() != expectedCount) {
            Thread.sleep(1000);
            if (System.currentTimeMillis() > start + 100000) {
                fail("Timed out waiting on subscription count to reach " + expectedCount);
            }
        }
    }
}
