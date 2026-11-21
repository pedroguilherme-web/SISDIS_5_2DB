package aisafe.unicorns.application;

import java.time.Instant;

public record AirplaneMoodResponse(String registration, String mood, Instant updatedAt) {}
