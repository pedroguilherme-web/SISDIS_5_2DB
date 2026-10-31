package aisafe.shared.infrastructure;

import aisafe.security.infrastructure.persistence.jpa.AuditLogJpaEntity;
import aisafe.security.infrastructure.persistence.jpa.SpringDataAuditLogRepository;
import aisafe.shared.application.SuppressArgLogging;
import aisafe.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

/**
 * AOP aspect that logs the execution of every public method in every
 * {@link UseCase}-annotated
 * class.
 *
 * <p>
 * For each use-case invocation the advice logs:
 * </p>
 * <ul>
 * <li>The class and method name together with the arguments, on entry</li>
 * <li>The class and method name together with the elapsed time (ms), on
 * exit</li>
 * </ul>
 *
 * <p>
 * This is a cross-cutting concern: no use case class needs a single log
 * statement -- the
 * logging is woven in at runtime by Spring AOP. {@code @Order(1)} ensures this
 * advice runs
 * before any other advice applied to the same pointcut.
 * </p>
 */
@Slf4j
@Component
@Aspect
@Order(1)
@RequiredArgsConstructor
public class UseCaseLoggingAdvice {

    private final SpringDataAuditLogRepository auditLogRepository;

    /** Matches any class annotated with {@link UseCase}. */
    @Pointcut("within(@aisafe.shared.application.UseCase *)")
    public void useCase() {
    }

    /** Matches any public method. */
    @Pointcut("execution(public * *(..))")
    public void publicMethod() {
    }

    /** Matches public methods inside a {@link UseCase}-annotated class. */
    @Pointcut("publicMethod() && useCase()")
    public void publicMethodInsideAUseCase() {
    }

    /**
     * Logs the use-case method name, parameters, and execution duration.
     *
     * @param pjp the join point representing the intercepted method call
     * @return the value returned by the intercepted method
     * @throws Throwable any exception thrown by the intercepted method is re-thrown
     *                   unchanged
     */
    @Around("publicMethodInsideAUseCase()")
    public Object aroundServiceMethodAdvice(final ProceedingJoinPoint pjp) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        Class<?> targetClass = pjp.getTarget().getClass();
        String actionName = targetClass.getSimpleName() + "#" + pjp.getSignature().getName();

        boolean suppress = targetClass.isAnnotationPresent(SuppressArgLogging.class);
        String args = suppress ? "[SUPPRESSED]" : Arrays.toString(pjp.getArgs());
        if (args.length() > 1950) {
            args = args.substring(0, 1950) + "...";
        }

        String username = resolveCurrentUsername();

        log.info("Executing use case: {} by user '{}' with parameters: {}", actionName, username, args);
        stopWatch.start();

        try {
            Object result = pjp.proceed();
            saveAuditLog(username, actionName, args, "SUCCESS", null);
            return result;
        } catch (Throwable ex) {
            String errorMsg = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            if (errorMsg.length() > 950) {
                errorMsg = errorMsg.substring(0, 950) + "...";
            }
            saveAuditLog(username, actionName, args, "FAILED", errorMsg);
            throw ex;
        } finally {
            stopWatch.stop();
            log.info("Finished executing use case {} in {}ms", actionName, stopWatch.getTotalTimeMillis());
        }
    }

    private String resolveCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null) {
            return auth.getName();
        }
        return "ANONYMOUS";
    }

    private void saveAuditLog(String username, String action, String args, String status, String errorMsg) {
        try {
            AuditLogJpaEntity logEntry = new AuditLogJpaEntity(
                    OffsetDateTime.now(ZoneOffset.UTC),
                    username,
                    action,
                    args,
                    status,
                    errorMsg
            );
            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Could not persist audit log entry for {}: {}", action, e.getMessage());
        }
    }
}
