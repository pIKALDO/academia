package com.academia.common.audit;

import java.lang.reflect.Method;
import java.util.UUID;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Escucha los métodos anotados con {@link Audited} y delega el registro en
 * {@link AuditLogWriter}. Solo se dispara tras una ejecución sin excepción: una operación
 * que falla no es una acción realizada.
 */
@Aspect
@Component
class AuditAspect {

    private final AuditLogWriter writer;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    AuditAspect(AuditLogWriter writer) {
        this.writer = writer;
    }

    @AfterReturning(pointcut = "@annotation(audited)", returning = "result")
    public void record(JoinPoint joinPoint, Audited audited, Object result) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);

        UUID studentId = findUuidParameter(parameterNames, joinPoint.getArgs(), audited.studentIdParam());
        UUID entityId = resolveEntityId(result);
        String ipAddress = currentRequestIp();

        writer.write(audited.action(), audited.entity(), entityId, studentId, ipAddress);
    }

    private UUID findUuidParameter(String[] parameterNames, Object[] args, String paramName) {
        if (paramName.isBlank() || parameterNames == null) {
            return null;
        }
        for (int i = 0; i < parameterNames.length; i++) {
            if (parameterNames[i].equals(paramName) && args[i] instanceof UUID uuid) {
                return uuid;
            }
        }
        return null;
    }

    private UUID resolveEntityId(Object result) {
        if (result == null) {
            return null;
        }
        UUID fromEntityGetter = invokeIfUuid(result, "getId");
        // Las entidades JPA exponen getId(); los DTO son records (CLAUDE.md: "Records para
        // DTOs") y su accesor canónico se llama id(), no getId().
        return fromEntityGetter != null ? fromEntityGetter : invokeIfUuid(result, "id");
    }

    private UUID invokeIfUuid(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private String currentRequestIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest().getRemoteAddr();
    }
}
