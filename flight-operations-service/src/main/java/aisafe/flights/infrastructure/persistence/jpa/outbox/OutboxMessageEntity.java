package aisafe.flights.infrastructure.persistence.jpa.outbox;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "outbox_message", indexes = @Index(name = "idx_outbox_pending", columnList = "publishedAt, sequenceNumber"))
@Getter
public class OutboxMessageEntity {
    @Id
    @Column(length = 80)
    private String messageId;

    @Column(nullable = false, unique = true)
    private long sequenceNumber;

    @Column(nullable = false)
    private String exchangeName;

    @Column(nullable = false)
    private String routingKey;

    @Column(nullable = false)
    private String messageType;

    @Lob
    @Column(nullable = false)
    private byte[] payload;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    private int attempts;

    @Column(length = 500)
    private String lastError;

    protected OutboxMessageEntity() {
    }

    public OutboxMessageEntity(String messageId, long sequenceNumber, String exchangeName, String routingKey,
                               String messageType, byte[] payload) {
        this.messageId = messageId;
        this.sequenceNumber = sequenceNumber;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.messageType = messageType;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }
}
