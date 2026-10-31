package aisafe.security.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataAuditLogRepository extends JpaRepository<AuditLogJpaEntity, Long> {
}