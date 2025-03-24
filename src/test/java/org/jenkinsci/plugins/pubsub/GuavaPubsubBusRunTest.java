package org.jenkinsci.plugins.pubsub;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.User;
import hudson.model.queue.QueueTaskFuture;
import hudson.security.ACL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@WithJenkins
class GuavaPubsubBusRunTest {

    private JenkinsRule jenkins;

    @BeforeEach
    void setUp(JenkinsRule j) {
        jenkins = j;
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
        jenkins.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Job.READ, Job.CREATE).everywhere().to("alice"));
    }

    @Test
    void test_Run() {
        final PubsubBus bus = PubsubBus.getBus();
        try {
            User alice = User.get("alice");
            User bob = User.get("bob");

            MockSubscriber aliceSubs = new MockSubscriber();
            MockSubscriber bobSubs = new MockSubscriber();

            // alice and bob both subscribe to job event messages ...
            bus.subscribe2(Events.JobChannel.NAME, aliceSubs, alice.impersonate2(), null);
            bus.subscribe2(Events.JobChannel.NAME, bobSubs, bob.impersonate2(), null);

            // Create a job as Alice and restrict it to her
            // bob etc should not be able to see it.
            try (var ignored = ACL.as(alice)) {
                assertDoesNotThrow(() -> {
                    FreeStyleProject job = jenkins.createFreeStyleProject("a-job");
                    QueueTaskFuture<FreeStyleBuild> future = job.scheduleBuild2(0);
                    jenkins.assertBuildStatusSuccess(future);
                });
            }

            aliceSubs.waitForMessageCount(4);

            // Security check ...
            // alice should have received the run messages, but not bob.
            assertFalse(aliceSubs.messages.isEmpty());
            assertTrue(bobSubs.messages.isEmpty());

            assertEquals(Events.JobChannel.job_crud_created.name(), aliceSubs.messages.get(0).getEventName());
            assertEquals(Events.JobChannel.job_run_queue_enter.name(), aliceSubs.messages.get(1).getEventName());

            // Check make sure message enrichment happened.
            // https://issues.jenkins-ci.org/browse/JENKINS-36218
            assertEquals("nice one", aliceSubs.messages.get(0).get(NoddyMessageEnricher.NODDY_MESSAGE_ENRICHER_PROP));

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
            assertEquals(queueMessage.get(EventProps.Job.job_run_queueId), runMessage.get(EventProps.Job.job_run_queueId));
        } finally {
            bus.shutdown();
        }
    }
}
