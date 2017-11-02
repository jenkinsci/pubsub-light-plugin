package org.jenkinsci.plugins.pubsub;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class RedisPubsubBusTest {
    @Test
    public void getBus() throws Exception {
        assertTrue(PubsubBus.getBus() instanceof RedisPubsubBus);
    }
}