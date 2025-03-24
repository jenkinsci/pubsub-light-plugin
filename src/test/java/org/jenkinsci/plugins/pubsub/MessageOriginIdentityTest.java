/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

import hudson.ExtensionList;
import hudson.model.User;
import org.jenkinsci.main.modules.instance_identity.PageDecoratorImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@WithJenkins
class MessageOriginIdentityTest {

    private JenkinsRule jenkins;

    private GuavaPubsubBus bus;

    @BeforeEach
    void setUp(JenkinsRule j) {
        jenkins = j;
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
        bus = new GuavaPubsubBus();
    }

    @AfterEach
    void stop() {
        bus.shutdown();
    }

    @Test
    void test() throws IOException {
        User alice = User.get("alice");

        ChannelPublisher publisher = bus.publisher("jenkins.job");
        MockSubscriber subscriber = new MockSubscriber();

        // Subscribers ...
        bus.subscribe2("jenkins.job", subscriber, alice.impersonate2(), null);

        // Publish ...
        publisher.publish(new SimpleMessage().set("joba", "joba"));
        subscriber.waitForMessageCount(1);

        // Checks ...

        Message message = subscriber.messages.get(0);

        assertEquals(jenkins.getURL().toString(), message.getJenkinsInstanceUrl());

        // Identity should not be on it by default.
        assertNull(message.getJenkinsInstanceId());

        // Set the identity and make sure it's the same as what's coming out of
        // PageDecoratorImpl.java in the instance-identity-module.
        message.setJenkinsInstanceId();
        assertEquals(getInstanceIdFromPageDecoratorImpl(), message.getJenkinsInstanceId());
    }

    private String getInstanceIdFromPageDecoratorImpl() {
        ExtensionList<PageDecoratorImpl> pageDecorators = ExtensionList.lookup(PageDecoratorImpl.class);
        return pageDecorators.get(0).getEncodedPublicKey();
    }
}
