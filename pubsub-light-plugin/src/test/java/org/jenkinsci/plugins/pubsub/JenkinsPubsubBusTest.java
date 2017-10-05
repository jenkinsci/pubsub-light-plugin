package org.jenkinsci.plugins.pubsub;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class JenkinsPubsubBusTest {
    @Test
    public void getBus() throws Exception {
        assertTrue(PubsubBus.getBus() instanceof JenkinsGuavaPubsubBus);
    }

    @Test
    public void getBus_specify() throws Exception {
        assertTrue(PubsubBus.getBus("JenkinsGuavaPubsubBus") instanceof GuavaPubsubBus);
    }
}