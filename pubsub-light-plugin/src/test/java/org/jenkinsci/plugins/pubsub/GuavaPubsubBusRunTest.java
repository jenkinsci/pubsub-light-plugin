package org.jenkinsci.plugins.pubsub;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.User;
import hudson.model.queue.QueueTaskFuture;
import hudson.security.ACL;
import hudson.security.AuthorizationMatrixProperty;
import hudson.security.Permission;
import hudson.security.ProjectMatrixAuthorizationStrategy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class GuavaPubsubBusRunTest {
    
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();
    
    @Before
    public void setupRealm() {
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
        ProjectMatrixAuthorizationStrategy auth = new ProjectMatrixAuthorizationStrategy();
        auth.add(Job.READ, "alice");
        auth.add(Job.CREATE, "alice");
        jenkins.jenkins.setAuthorizationStrategy(auth);
        System.setProperty("org.jenkinsci.plugins.pubsub.in.jenkins", "true");
    }

    @After
    public void tearDown() throws Exception {
        System.clearProperty("org.jenkinsci.plugins.pubsub.in.jenkins");
    }

    @Test
    public void test_Run() throws Exception {
        final PubsubBus bus = PubsubBus.getBus();
        try {
            User alice = User.get("alice");
            User bob = User.get("bob");

            MockSubscriber aliceSubs = new MockSubscriber();
            MockSubscriber bobSubs = new MockSubscriber();

            // alice and bob both subscribe to job event messages ...
            bus.subscribe(Events.JobChannel.NAME, aliceSubs, alice.impersonate(), null);
            bus.subscribe(Events.JobChannel.NAME, bobSubs, bob.impersonate(), null);

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
                        jenkins.assertBuildStatusSuccess(future);
                    } catch (Exception e) {
                        fail(e.getMessage());
                    }
                }
            });

            aliceSubs.waitForMessageCount(4);
            
            // Security check ...
            // alice should have received the run messages, but not bob.
            assertFalse(aliceSubs.messages.isEmpty());
            assertTrue(bobSubs.messages.isEmpty());

            assertEquals(Events.JobChannel.job_crud_created.name(), aliceSubs.messages.get(0).getEventName());
            assertEquals(Events.JobChannel.job_run_queue_enter.name(), aliceSubs.messages.get(1).getEventName());
            
            // Check make sure message enrichment happened.
            // https://issues.jenkins-ci.org/browse/JENKINS-36218
            Assert.assertEquals("nice one", aliceSubs.messages.get(0).get(NoddyMessageEnricher.NODDY_MESSAGE_ENRICHER_PROP));

            QueueTaskMessage queueMessage = (QueueTaskMessage) aliceSubs.messages.get(1);
            RunMessage runMessage = (RunMessage) aliceSubs.messages.get(4);

            // The domain model object instances should not be on the messages...
            assertNull(queueMessage.jobChannelItem);
            assertNull(runMessage.jobChannelItem);
            assertNull(runMessage.run);
            // But calling the getter methods should result in them being looked up...
            queueMessage.getJobChannelItem();
            runMessage.getRun();
            assertNotNull(queueMessage.jobChannelItem);
            assertNotNull(runMessage.jobChannelItem);
            assertNotNull(runMessage.run);
            assertEquals(queueMessage.jobChannelItem, runMessage.jobChannelItem);
            
            // And check that the queue Ids match
            Assert.assertEquals(queueMessage.get(EventProps.Job.job_run_queueId), runMessage.get(EventProps.Job.job_run_queueId));
                    
        } finally {
            bus.shutdown();
        }
    }
}