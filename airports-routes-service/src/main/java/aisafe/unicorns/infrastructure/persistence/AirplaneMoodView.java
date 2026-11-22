package aisafe.unicorns.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "airplane_mood_read_model")
public class AirplaneMoodView {

    @Id
    @Column(nullable = false, length = 32)
    private String registration;

    @Column(nullable = false, length = 32)
    private String mood;

    @Column(name = "last_event_id", length = 64)
    private String lastEventId;

    @Column(name = "last_correlation_id", length = 64)
    private String lastCorrelationId;

    @Column(nullable = false)
    private Instant updatedAt;

    protected AirplaneMoodView() {}

    public AirplaneMoodView(String registration, String mood, Instant updatedAt) {
        this(registration, mood, updatedAt, null, null);
    }

    public AirplaneMoodView(
            String registration,
            String mood,
            Instant updatedAt,
            String lastEventId,
            String lastCorrelationId) {
        this.registration = registration;
        this.mood = mood;
        this.updatedAt = updatedAt;
        this.lastEventId = lastEventId;
        this.lastCorrelationId = lastCorrelationId;
    }

    public String getRegistration() {
        return registration;
    }

    public String getMood() {
        return mood;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public String getLastCorrelationId() {
        return lastCorrelationId;
    }

    public void applyMood(
            String mood,
            Instant updatedAt,
            String eventId,
            String correlationId) {
        this.mood = mood;
        this.updatedAt = updatedAt;
        this.lastEventId = eventId;
        this.lastCorrelationId = correlationId;
    }
}
