
package aisafe.p2p;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchRequestTrackerTest {

    @Test
    void shouldAcceptNewRequest() {

        SearchRequestTracker tracker =
                new SearchRequestTracker();

        assertTrue(tracker.markIfNew("request-1"));
        assertTrue(tracker.hasProcessed("request-1"));
        assertEquals(1, tracker.getProcessedCount());
    }

    @Test
    void shouldRejectDuplicateRequest() {

        SearchRequestTracker tracker =
                new SearchRequestTracker();

        assertTrue(tracker.markIfNew("request-1"));
        assertFalse(tracker.markIfNew("request-1"));

        assertEquals(1, tracker.getProcessedCount());
    }

    @Test
    void shouldRejectInvalidRequestId() {

        SearchRequestTracker tracker =
                new SearchRequestTracker();

        assertThrows(
                IllegalArgumentException.class,
                () -> tracker.markIfNew(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> tracker.markIfNew("")
        );
    }
}
