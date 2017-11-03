package org.jenkinsci.plugins.pubsub;

import hudson.ExtensionList;
import jenkins.model.Jenkins;
import org.junit.*;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

public class PubsubBusWithJenkinsTest {
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void getBus_installedExtension() throws Exception {
        final PubsubBus bus1 = PubsubBus.getBus();

        final ExtensionList<PubsubBus> extensionList = Jenkins.getInstanceOrNull().getExtensionList(PubsubBus.class);
        extensionList.add(bus1);

        NullerPubsubBus.nullPubsubBus();

        final PubsubBus bus2 = PubsubBus.getBus();

        assertTrue(bus1 == bus2);
    }
}

class NullerPubsubBus extends GuavaPubsubBus {
    static void nullPubsubBus() {
        pubsubBus = null;
    }
}