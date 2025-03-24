package org.jenkinsci.plugins.pubsub;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventFilterTest {

    @Test
    void test_no_default_props() {
        // A Message should have default props set on it.
        assertFalse(new SimpleMessage().isEmpty());
        // But an EventFilter should not.
        assertTrue(new EventFilter().isEmpty());
    }
}
