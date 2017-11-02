package org.jenkinsci.plugins.pubsub;

import com.google.common.collect.Lists;
import org.acegisecurity.providers.TestingAuthenticationToken;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
