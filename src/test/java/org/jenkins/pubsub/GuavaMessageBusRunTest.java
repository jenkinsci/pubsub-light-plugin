package org.jenkins.pubsub;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.User;
import hudson.model.queue.QueueTaskFuture;
import hudson.security.ACL;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.Permission;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class GuavaMessageBusRunTest {
    
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
    
    @Before
    public void setupRealm() {
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy auth = new ProjectMatrixAuthorizationStrategy();
        auth.add(Job.READ, "alice");
        auth.add(Job.CREATE, "alice");
        jenkins.jenkins.setAuthorizationStrategy(auth);
    }
    
    @Test
    public void test_Run() throws Exception {
        final MessageBus bus = MessageBus.getBus();
        User alice = User.get("alice");
        User bob = User.get("bob");

        MockSubscriber aliceSubs = new MockSubscriber();
        MockSubscriber bobSubs = new MockSubscriber();

        // alice and bob both subscribe to job event messages ...
        bus.subscribe(JobMessage.CHANNEL_NAME, aliceSubs, alice, null);
        bus.subscribe(JobMessage.CHANNEL_NAME, bobSubs, bob, null);
        
        // Create a job as Alice and restrict it to her
        // bob etc should not be able to see it.
        ACL.impersonate(alice.impersonate(), new Runnable() {
            @Override
            public void run() {
                try {
                    FreeStyleProject job = jenkins.createFreeStyleProject("a-job");
                    
                    Map<Permission,Set<String>> perms = new HashMap<>();
                    perms.put(Job.CREATE, Collections.singleton("alice"));
                    job.addProperty(new AuthorizationMatrixProperty(perms));
                    
                    QueueTaskFuture<FreeStyleBuild> future = job.scheduleBuild2(0);
                    Run run = jenkins.assertBuildStatusSuccess(future);
                    
                    bus.publish(new RunMessage(run).setEventName("blah"));
                } catch (Exception e) {
                    fail(e.getMessage());
                }
            }
        });

        // alice should have received the run message, but not bob.
        assertEquals(1, aliceSubs.messages.size());
        assertEquals(0, bobSubs.messages.size());
        
        // The Run instance should not be on the message, but calling
        // getAccessControlled() should get the run via the event 
        // properties (job name, build Id etc).
        RunMessage runMessage = (RunMessage) aliceSubs.messages.get(0);
        assertNull(runMessage.messageRun);
        runMessage.getAccessControlled();
        assertNotNull(runMessage.messageRun);
    }
}