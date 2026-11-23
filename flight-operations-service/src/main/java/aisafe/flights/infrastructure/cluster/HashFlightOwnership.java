package aisafe.flights.infrastructure.cluster;

import aisafe.flights.application.ports.FlightOwnership;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class HashFlightOwnership implements FlightOwnership {
    private final String selfUrl;
    private final List<String> members;

    public HashFlightOwnership(@Value("${aisafe.cluster.self-url:}") String selfUrl,
                               @Value("${peers.urls:}") List<String> peerUrls) {
        this.selfUrl = normalize(selfUrl);
        this.members = this.selfUrl.isEmpty()
                ? List.of()
                : Stream.concat(Stream.of(this.selfUrl), peerUrls.stream().map(HashFlightOwnership::normalize))
                        .filter(url -> !url.isEmpty())
                        .distinct()
                        .sorted()
                        .toList();
    }

    @Override
    public Optional<String> ownerOf(String aircraftRegistration) {
        if (members.size() <= 1) {
            return Optional.empty();
        }
        String owner = members.get(Math.floorMod(aircraftRegistration.trim().toUpperCase().hashCode(), members.size()));
        return owner.equals(selfUrl) ? Optional.empty() : Optional.of(owner);
    }

    List<String> members() {
        return members;
    }

    private static String normalize(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
