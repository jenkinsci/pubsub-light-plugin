package org.jenkins.pubsub;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
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
            bus.subscribe(Events.JobChannel.NAME, aliceSubs, alice, null);
            bus.subscribe(Events.JobChannel.NAME, bobSubs, bob, null);

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

            aliceSubs.waitForMessageCount(3);
            
            // Security check ...
            // alice should have received the run messages, but not bob.
            assertEquals(3, aliceSubs.messages.size());
            assertEquals(0, bobSubs.messages.size());

            JobMessage queueMessage = (JobMessage) aliceSubs.messages.get(0);
            RunMessage runMessage = (RunMessage) aliceSubs.messages.get(1);

            // The domain model object instances should not be on the messages...
            assertNull(queueMessage.job);
            assertNull(runMessage.job);
            assertNull(runMessage.run);
            // But calling the getter methods should result in them being looked up...
            queueMessage.getJob();
            runMessage.getRun();
            assertNotNull(queueMessage.job);
            assertNotNull(runMessage.job);
            assertNotNull(runMessage.run);
            assertEquals(queueMessage.job, runMessage.job);
            
            // And check that the queue Ids match
            assertEquals(queueMessage.get(EventProps.Job.job_run_queueId), runMessage.get(EventProps.Job.job_run_queueId));
                    
        } finally {
            bus.shutdown();
        }
    }
}