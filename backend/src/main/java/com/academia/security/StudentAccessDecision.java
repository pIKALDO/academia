package com.academia.security;

import org.springframework.security.authorization.AuthorizationDecision;

/**
 * Resultado de {@link AccessService#canViewStudent}: además de "sí/no", dice cómo hay que
 * negar (docs/diseno-api.md sección 3.1).
 *
 * <ul>
 *   <li>{@link #HIDDEN}: el estudiante no es del usuario. Se responde 404, idéntico a un id
 *       inexistente, para no confirmar que existe (regla no negociable nº1).</li>
 *   <li>{@link #FORBIDDEN}: el rol entero no tiene acceso al módulo, sin mirar el id
 *       (STUDENT en fase 1). 403: la respuesta no depende de ningún id, así que no filtra
 *       nada.</li>
 * </ul>
 *
 * <p>Por qué un tipo propio y no un {@code boolean}: {@code @PreAuthorize} admite que la
 * expresión devuelva un {@code AuthorizationResult}, y {@link HideStudentWhenNotVisible}
 * lo recibe al denegar. Así la decisión "ocultar o prohibir" se toma aquí, en
 * {@code AccessService} (regla no negociable nº2), y el manejador solo la traduce.
 */
public final class StudentAccessDecision extends AuthorizationDecision {

    public static final StudentAccessDecision GRANTED = new StudentAccessDecision(true, false);
    public static final StudentAccessDecision HIDDEN = new StudentAccessDecision(false, true);
    public static final StudentAccessDecision FORBIDDEN = new StudentAccessDecision(false, false);

    private final boolean hidden;

    private StudentAccessDecision(boolean granted, boolean hidden) {
        super(granted);
        this.hidden = hidden;
    }

    public boolean isHidden() {
        return hidden;
    }

    @Override
    public String toString() {
        return isGranted() ? "GRANTED" : hidden ? "HIDDEN" : "FORBIDDEN";
    }
}
