package com.academia.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/students/018f");

    @Test
    void un_recurso_ajeno_o_inexistente_responde_404() {
        ProblemDetail problem = handler.handleNotFound(new NotFoundException("no existe"), request);

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).isEqualTo("no existe");
    }

    @Test
    void una_ruta_inexistente_responde_404_sin_repetir_la_ruta_en_el_detalle() {
        ProblemDetail problem = handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/no-existe", "api/v1/no-existe"), request);

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).doesNotContain("no-existe");
    }

    @Test
    void un_conflicto_con_el_estado_actual_responde_409() {
        ProblemDetail problem = handler.handleConflict(new ConflictException("el email ya existe"), request);

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).isEqualTo("el email ya existe");
    }

    // El 403 de "operación no permitida" ya no se construye aquí: Spring Security intercepta
    // AccessDeniedException antes de que llegue a este @RestControllerAdvice, vengan de un
    // filtro o de un @PreAuthorize (ver ProblemDetailSecurityHandlers, en el módulo config, y
    // el test de extremo a extremo en UserControllerIT).

    @Test
    void una_subida_demasiado_grande_responde_413() {
        ProblemDetail problem = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(10), request);

        assertThat(problem.getStatus()).isEqualTo(413);
    }

    @Test
    void una_validacion_de_bean_fallida_responde_422_con_los_campos_afectados() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "documento");
        bindingResult.addError(new FieldError("documento", "expiresAt", "debe ser posterior a issuedAt"));
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("metodoDeApoyo", String.class), 0);

        ProblemDetail problem = handler.handleValidation(
                new MethodArgumentNotValidException(parameter, bindingResult), request);

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getProperties()).containsEntry("errors",
                List.of(new FieldValidationError("expiresAt", "debe ser posterior a issuedAt")));
    }

    @Test
    void una_violacion_de_restricciones_responde_422_con_el_campo_afectado() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path propertyPath = mock(Path.class);
        when(propertyPath.toString()).thenReturn("expiresAt");
        when(violation.getPropertyPath()).thenReturn(propertyPath);
        when(violation.getMessage()).thenReturn("debe ser posterior a issuedAt");

        ProblemDetail problem = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of(violation)), request);

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getProperties()).containsEntry("errors",
                List.of(new FieldValidationError("expiresAt", "debe ser posterior a issuedAt")));
    }

    @Test
    void un_error_no_controlado_responde_500_con_traceid_y_sin_detalle_interno() {
        ProblemDetail problem = handler.handleUnexpected(new RuntimeException("SELECT * FROM students falló"), request);

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).doesNotContain("SELECT");
        assertThat(problem.getProperties()).containsKey("traceId");
    }

    @SuppressWarnings("unused")
    private void metodoDeApoyo(String value) {
    }
}
