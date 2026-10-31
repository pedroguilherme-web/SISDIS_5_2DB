package aisafe.shared.application;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Intercetor AOP que regista a execução, argumentos e tempo de resposta dos UseCases.
 */
@Aspect
@Component
public class UseCaseLoggingAdvice {

    private static final Logger logger = LoggerFactory.getLogger(UseCaseLoggingAdvice.class);

    @Around("@within(aisafe.shared.application.UseCase) || @annotation(aisafe.shared.application.UseCase)")
    public Object logExecutionTimeAndArgs(ProceedingJoinPoint joinPoint) throws Throwable {
        Class<?> targetClass = joinPoint.getTarget().getClass();
        String methodName = joinPoint.getSignature().toShortString();

        // Verifica se a anotação @SuppressArgLogging está presente na classe para ocultar credenciais
        boolean suppressArgs = targetClass.isAnnotationPresent(SuppressArgLogging.class);
        String args = suppressArgs ? "[SUPPRESSED]" : Arrays.toString(joinPoint.getArgs());

        logger.info("Executing {} with args: {}", methodName, args);

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Completed {} in {}ms", methodName, duration);
            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Failed {} after {}ms with exception: {}", methodName, duration, e.getMessage());
            throw e; // Lançar novamente para o GlobalExceptionHandler apanhar
        }
    }
}