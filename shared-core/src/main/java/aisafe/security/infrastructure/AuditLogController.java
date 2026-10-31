package aisafe.security.infrastructure;

import aisafe.security.infrastructure.persistence.jpa.AuditLogJpaEntity;
import aisafe.security.infrastructure.persistence.jpa.SpringDataAuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Audit Logs", description = "System security and operational audit trail")
@RequiredArgsConstructor
public class AuditLogController {

    private final SpringDataAuditLogRepository auditLogRepository;

    @Operation(summary = "List all audit logs", description = "Returns all recorded audit logs ordered by most recent first.")
    @GetMapping
    public List<AuditLogJpaEntity> getAllAuditLogs() {
        return auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "executionTime"));
    }
}