package aisafe.flights.infrastructure.persistence.jpa.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, String> {
    List<OutboxMessageEntity> findTop100ByPublishedAtIsNullOrderBySequenceNumberAsc();

    long countByPublishedAtIsNull();

    @Query("SELECT COALESCE(MAX(o.sequenceNumber), 0) FROM OutboxMessageEntity o")
    long maxSequenceNumber();

    @Modifying
    @Query("DELETE FROM OutboxMessageEntity o WHERE o.publishedAt IS NOT NULL AND o.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);
}
