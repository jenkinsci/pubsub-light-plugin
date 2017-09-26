package org.jenkinsci.plugins.pubsub;

import org.jenkinsci.plugins.pubsub.message.ItemMessage;
import org.jenkinsci.plugins.pubsub.message.Message;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class MessageTest {
    @Test
    public void test_item_Message() {
        Message message = new ItemMessage(new MockItem("a"));
        assertEquals("a", message.getObjectName());
    }
}