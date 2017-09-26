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
import org.jenkinsci.plugins.pubsub.message.JenkinsMessage;
import org.jenkinsci.plugins.pubsub.message.SimpleJenkinsMessage;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class MessageOriginIdentityTest {
    
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
    
    private GuavaPubsubBus bus;
    
    @Before
    public void setupRealm() {
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
    }

    @Before
    public void startBus() {
        bus = new GuavaPubsubBus();
    }

    @After
    public void stop() {
        bus.shutdown();
    }

    @Test
    public void test() throws IOException {
        User alice = User.get("alice");

        ChannelPublisher publisher = bus.publisher("jenkins.job");
        MockSubscriber subscriber = new MockSubscriber();

        // Subscribers ...
        bus.subscribe("jenkins.job", subscriber, alice.impersonate(), null);
        
        // Publish ...
        publisher.publish(new SimpleJenkinsMessage().set("joba", "joba"));
        subscriber.waitForMessageCount(1);
        
        // Checks ...
        
        JenkinsMessage message = subscriber.messages.get(0);

        Assert.assertEquals(jenkins.getURL().toString(), message.getJenkinsInstanceUrl());
        
        // Identity should not be on it by default.
        Assert.assertEquals(null, message.getJenkinsInstanceId());

        // Set the identity and make sure it's the same as what's coming out of
        // PageDecoratorImpl.java in the instance-identity-module.
        message.setJenkinsInstanceId();
        Assert.assertEquals(getInstanceIdFromPageDecoratorImpl(), message.getJenkinsInstanceId());
    }

    private String getInstanceIdFromPageDecoratorImpl() {
        ExtensionList<PageDecoratorImpl> pageDecorators = ExtensionList.lookup(PageDecoratorImpl.class);
        return pageDecorators.get(0).getEncodedPublicKey();
    }
}