package com.academia.support;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador que solo existe en el classpath de test, para poder ejercitar
 * {@code SecurityConfig} (401 sin autenticar) antes de que existan controladores de negocio.
 *
 * {@code @Hidden}: {@code docs/openapi.json} se exporta desde un test de integración
 * ({@code OpenApiSpecificationIT}), con este controlador cargado. Sin la anotación, una ruta
 * que no existe en producción aparecería en el contrato público.
 */
@Hidden
@RestController
class ProtectedTestController {

    @GetMapping("/test/protected")
    String protegido() {
        return "ok";
    }
}
