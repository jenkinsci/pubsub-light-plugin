package org.jenkinsci.plugins.pubsub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
class MessageTest {

    @Test
    void test_item_Message() {
        Message message = new ItemMessage(new MockItem("a"));
        assertEquals("a", message.getObjectName());
    }

    @Test
    void test_containsAll() {
        Message message = new SimpleMessage();

        message.setProperty("a", "a");
        message.setProperty("b", "b");
        message.setProperty("c", "c");

        EventFilter filter = new EventFilter();

        assertTrue(message.containsAll(filter));

        filter.setProperty("a", "a");
        assertTrue(message.containsAll(filter));
        filter.setProperty("b", "b");
        assertTrue(message.containsAll(filter));
        filter.setProperty("c", "c");
        assertTrue(message.containsAll(filter));

        // Set a diff value for a ... should cause it to not match
        filter.setProperty("c", "--");
        assertFalse(message.containsAll(filter));
    }

    @Test
    void test_toJSON() {
        Message message = new SimpleMessage().set("a", "aVal");

        // Check that the message has a UUID. Then remove it
        // so we can do a check on the rest of the content.
        assertNotNull(message.getEventUUID());
        message.remove(EventProps.Jenkins.jenkins_event_uuid.name());
        // Same for timestamp.
        assertTrue(message.getTimestampMillis() > 0);
        message.remove(EventProps.Jenkins.jenkins_event_timestamp.name());

        assertEquals("{\"a\":\"aVal\"}", message.toJSON());
        assertEquals("{\"a\":\"aVal\"}", message.toString());
    }
}
