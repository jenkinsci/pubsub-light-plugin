package org.jenkinsci.plugins.pubsub;

import org.junit.Test;

import static org.junit.Assert.*;

public class EventFilterTest {

    @Test
    public void test_no_default_props() {
        // A Message should have default props set on it.
        assertFalse(new SimpleMessage().isEmpty());
        // But an EventFilter should not.
        assertTrue(new EventFilter().isEmpty());
    }
}
