package com.academia.security;

import com.academia.documents.DocumentNotFoundException;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.MethodAuthorizationDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Análogo a {@link HideStudentWhenNotVisible} para documentos: traduce una denegación
 * {@link StudentAccessDecision#HIDDEN} (resuelta a través del estudiante propietario, ver
 * {@link AccessService#canViewDocument}) en {@link DocumentNotFoundException}, mismo tipo y
 * mensaje que un id inexistente.
 */
@Component
public class HideDocumentWhenNotVisible implements MethodAuthorizationDeniedHandler {

    @Override
    public Object handleDeniedInvocation(MethodInvocation invocation, AuthorizationResult result) {
        if (result instanceof StudentAccessDecision decision && decision.isHidden()) {
            throw new DocumentNotFoundException();
        }
        throw new AuthorizationDeniedException("Access Denied", result);
    }
}
