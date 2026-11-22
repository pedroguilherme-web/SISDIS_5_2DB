package aisafe.flights.infrastructure.cluster;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HashFlightOwnershipTest {
    private final HashFlightOwnership replica1 = new HashFlightOwnership("http://localhost:8071", List.of("http://localhost:8072"));
    private final HashFlightOwnership replica2 = new HashFlightOwnership("http://localhost:8072/", List.of(" http://localhost:8071 "));

    private static String ownerSeenBy(HashFlightOwnership replica, String self, String registration) {
        return replica.ownerOf(registration).orElse(self);
    }

    @Test
    void everyReplicaPicksTheSameOwnerForAnAircraft() {
        for (String registration : List.of("CS-TRP", "JA-HND", "JA-NRT", "CS-TKA", "EC-ABC", "D-AIBC")) {
            assertEquals(ownerSeenBy(replica1, "http://localhost:8071", registration),
                    ownerSeenBy(replica2, "http://localhost:8072", registration), registration);
        }
    }

    @Test
    void aircraftAreSpreadOverAllReplicas() {
        Set<String> owners = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            owners.add(ownerSeenBy(replica1, "http://localhost:8071", "CS-T" + (char) ('A' + i % 26) + (char) ('A' + i / 26)));
        }
        assertEquals(Set.of("http://localhost:8071", "http://localhost:8072"), owners);
    }

    @Test
    void theRegistrationIsNormalised() {
        assertEquals(replica1.ownerOf("CS-TRP"), replica1.ownerOf(" cs-trp "));
    }

    @Test
    void aStandAloneInstanceOwnsEverything() {
        HashFlightOwnership alone = new HashFlightOwnership("", List.of("http://localhost:8072"));
        HashFlightOwnership noPeers = new HashFlightOwnership("http://localhost:8071", List.of());

        assertEquals(Optional.empty(), alone.ownerOf("CS-TRP"));
        assertEquals(Optional.empty(), noPeers.ownerOf("CS-TRP"));
    }
}
