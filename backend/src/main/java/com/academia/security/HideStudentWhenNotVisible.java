package com.academia.security;

import com.academia.students.StudentNotFoundException;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.MethodAuthorizationDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Se aplica con {@code @HandleAuthorizationDenied(handlerClass = HideStudentWhenNotVisible.class)}
 * junto a {@code @PreAuthorize("@access.canViewStudent(#id)")}. Traduce una denegación
 * {@link StudentAccessDecision#HIDDEN} en la misma {@link StudentNotFoundException} que lanza
 * el servicio cuando el id no existe: mismo tipo, mismo mensaje, misma respuesta. Cualquier
 * otra denegación sigue su curso normal (403).
 *
 * <p>No decide nada: la decisión ya viene tomada por {@link AccessService}.
 */
@Component
public class HideStudentWhenNotVisible implements MethodAuthorizationDeniedHandler {

    @Override
    public Object handleDeniedInvocation(MethodInvocation invocation, AuthorizationResult result) {
        if (result instanceof StudentAccessDecision decision && decision.isHidden()) {
            throw new StudentNotFoundException();
        }
        throw new AuthorizationDeniedException("Access Denied", result);
    }
}
