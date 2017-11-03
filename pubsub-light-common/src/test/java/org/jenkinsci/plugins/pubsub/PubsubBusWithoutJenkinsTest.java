package org.jenkinsci.plugins.pubsub;

import org.junit.Test;

public class PubsubBusWithoutJenkinsTest {
    @Test
    public void getBus() throws Exception {
        PubsubBus.getBus();
    }
}