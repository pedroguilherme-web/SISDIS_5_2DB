
package aisafe.p2p;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistributedSearchRequestTest {

    @Test
    void shouldCreateRequestWithUniqueId() {

        DistributedSearchRequest request1 =
                DistributedSearchRequest.create(
                        "/api/airports", 2
                );

        DistributedSearchRequest request2 =
                DistributedSearchRequest.create(
                        "/api/airports", 2
                );

        assertNotNull(request1.getRequestId());
        assertNotEquals(
                request1.getRequestId(),
                request2.getRequestId()
        );

        assertEquals("/api/airports", request1.getPath());
        assertEquals(2, request1.getTtl());
    }

    @Test
    void shouldDecreaseTtlOnNextHop() {

        DistributedSearchRequest request =
                DistributedSearchRequest.create(
                        "/api/airports", 2
                );

        DistributedSearchRequest next = request.nextHop();

        assertEquals(1, next.getTtl());
        assertEquals(
                request.getRequestId(),
                next.getRequestId()
        );

        assertEquals(2, request.getTtl());
    }

    @Test
    void shouldNotForwardWhenTtlIsZero() {

        DistributedSearchRequest request =
                DistributedSearchRequest.create(
                        "/api/airports", 0
                );

        assertThrows(
                IllegalStateException.class,
                request::nextHop
        );
    }
}
