package com.academia.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un método de servicio para que {@link AuditAspect} registre su ejecución en
 * {@code audit_log} (docs/modelo-datos.md sección 6).
 *
 * @param action         nombre corto de la acción, p. ej. {@code "STUDENT_CREATED"}
 * @param entity         tipo de entidad afectada, p. ej. {@code "STUDENT"}
 * @param studentIdParam nombre del parámetro del método que contiene el {@code studentId}
 *                       a denormalizar en el registro; vacío si la acción no cuelga de un
 *                       estudiante (p. ej. gestión de usuarios)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {

    String action();

    String entity();

    String studentIdParam() default "";
}
