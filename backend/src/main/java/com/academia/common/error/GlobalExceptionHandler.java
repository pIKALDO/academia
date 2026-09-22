package com.academia.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Traduce cada excepción a {@code ProblemDetail} (RFC 7807), tal y como exige
 * docs/diseno-api.md sección 7. Regla firme: ningún mensaje expone detalles internos
 * (traza, nombre de tabla, SQL); esos detalles van al log con un {@code traceId}, y la
 * respuesta solo lleva ese identificador (regla no negociable nº6).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_TYPE_BASE = "https://api.academia.example/errors/";

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return problemDetail(HttpStatus.NOT_FOUND, "not-found", "Recurso no encontrado", ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex, HttpServletRequest request) {
        return problemDetail(HttpStatus.CONFLICT, "conflict", "Conflicto con el estado actual", ex.getMessage(), request);
    }

    @ExceptionHandler(UnprocessableEntityException.class)
    public ProblemDetail handleUnprocessable(UnprocessableEntityException ex, HttpServletRequest request) {
        return problemDetail(HttpStatus.UNPROCESSABLE_CONTENT, "unprocessable", "Solicitud no procesable",
                ex.getMessage(), request);
    }

    // No hay @ExceptionHandler aquí para AuthenticationException ni AccessDeniedException a
    // propósito: Spring Security registra su propio HandlerExceptionResolver, con prioridad
    // sobre este @RestControllerAdvice, que intercepta esos dos tipos vengan de donde vengan
    // (un filtro, o un @PreAuthorize disparado dentro de un controlador o servicio) y los
    // reenvía a ExceptionTranslationFilter. Un @ExceptionHandler para ellos aquí nunca se
    // ejecutaría: es SecurityConfig quien construye esas dos respuestas
    // (authenticationEntryPoint / accessDeniedHandler), con el mismo formato ProblemDetail.

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return problemDetail(HttpStatus.CONTENT_TOO_LARGE, "payload-too-large", "Fichero demasiado grande",
                "El fichero supera el tamaño máximo permitido.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldValidationError(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return validationProblemDetail(errors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldValidationError> errors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldValidationError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return validationProblemDetail(errors, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Error no controlado. traceId={} method={} path={}", traceId, request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail problemDetail = problemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Error interno",
                "Se ha producido un error inesperado. Consulta el registro con este identificador.", request);
        problemDetail.setProperty("traceId", traceId);
        return problemDetail;
    }

    private ProblemDetail validationProblemDetail(List<FieldValidationError> errors, HttpServletRequest request) {
        ProblemDetail problemDetail = problemDetail(HttpStatus.UNPROCESSABLE_CONTENT, "validation",
                "Datos de entrada no válidos", "Uno o más campos no cumplen las reglas de validación.", request);
        problemDetail.setProperty("errors", errors);
        return problemDetail;
    }

    private ProblemDetail problemDetail(HttpStatus status, String typeSlug, String title, String detail,
            HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setType(URI.create(ERROR_TYPE_BASE + typeSlug));
        problemDetail.setTitle(title);
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
