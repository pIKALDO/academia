package com.academia.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador que solo existe en el classpath de test, para poder ejercitar
 * {@code SecurityConfig} (401 sin autenticar) antes de que existan controladores de negocio.
 */
@RestController
class ProtectedTestController {

    @GetMapping("/test/protected")
    String protegido() {
        return "ok";
    }
}
