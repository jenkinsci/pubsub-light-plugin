package org.jenkinsci.plugins.pubsub;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class MessageTest {
    @Test
    public void test_containsAll() {
        Message message = new BasicMessage();
        
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
    public void test_toJSON() {
        Message message = new BasicMessage().set("a", "aVal");

        // Check that the message has a UUID. Then remove it
        // so we can do a check on the rest of the content.
        assertTrue(message.getEventUUID() != null);
        message.remove(CommonEventProps.event_uuid.name());
        // Same for timestamp.
        assertTrue(message.getTimestampMillis() > 0);
        message.remove(CommonEventProps.event_timestamp.name());

        assertEquals("{\"a\":\"aVal\"}", message.toJSON());
        assertEquals("{\"a\":\"aVal\"}", message.toString());
    }
}