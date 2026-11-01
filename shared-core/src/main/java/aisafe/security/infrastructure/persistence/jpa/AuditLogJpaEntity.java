package aisafe.security.infrastructure.persistence.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLogJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private OffsetDateTime executionTime;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String action;

    @Column(length = 2000)
    private String arguments;

    @Column(nullable = false)
    private String executionResult;

    @Column(length = 1000)
    private String errorMessage;
    public AuditLogJpaEntity(OffsetDateTime executionTime, String username, String action,
                             String arguments, String executionResult, String errorMessage) {
        this.executionTime = executionTime;
        this.username = username;
        this.action = action;
        this.arguments = arguments;
        this.executionResult = executionResult;
        this.errorMessage = errorMessage;
    }
}