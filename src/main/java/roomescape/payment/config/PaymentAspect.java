package roomescape.payment.config;

import java.util.UUID;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
public class PaymentAspect {

    @Pointcut("execution(* roomescape.reservation.controller.*.*(..))")
    public void controllerAdvice() {
    }

    @Before("controllerAdvice()")
    public void requestLogging(JoinPoint joinPoint) {
        MDC.put("traceId", UUID.randomUUID().toString());
        log.info("REQUEST TRACING_ID -> {} / INVOKE METHOD ARGS -> {}", MDC.get("traceId"), joinPoint.getArgs());
    }

    @AfterReturning(pointcut = "controllerAdvice()", returning = "returnValue")
    public void requestLogging(JoinPoint joinPoint, Object returnValue) {
        log.info("RESPONSE TRACING_ID -> {} / RESULT -> {}", MDC.get("traceId"), returnValue);
        MDC.clear();
    }
}
