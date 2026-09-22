package com.academia.common.error;

import com.academia.config.ProblemDetailSecurityHandlers;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /**
     * Ruta que no corresponde a ningún controlador. Spring MVC la pasa al manejador de
     * recursos estáticos, que lanza esta excepción; sin este método caía en el catch-all y
     * salía como 500. El {@code detail} no repite la ruta: ya va en {@code instance}.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return problemDetail(HttpStatus.NOT_FOUND, "not-found", "Recurso no encontrado",
                "La ruta solicitada no existe.", request);
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

    // AuthenticationException/AccessDeniedException lanzadas desde un filtro (petición sin
    // sesión, o AuthorizationFilter al final de la cadena) nunca llegan aquí: las captura
    // ExceptionTranslationFilter y las resuelve con authenticationEntryPoint/accessDeniedHandler
    // (SecurityConfig). Pero las que lanza código que se ejecuta DENTRO de la invocación del
    // controlador —el authenticationManager.authenticate() manual de AuthController.login, o
    // un @PreAuthorize de un método de servicio llamado desde un controlador— sí caen aquí:
    // Spring MVC las resuelve con sus HandlerExceptionResolver antes de que puedan propagarse
    // de vuelta al filtro. Sin estos dos @ExceptionHandler, ambas acababan en el catch-all de
    // más abajo y salían como 500 en vez de 401/403. Reutilizan la misma construcción de
    // ProblemDetail que ProblemDetailSecurityHandlers para que el formato no dependa de por
    // qué camino haya venido la excepción.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetailSecurityHandlers.authenticationProblemDetail(ex, request);
        if (problemDetail == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problemDetail);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(HttpServletRequest request) {
        return ProblemDetailSecurityHandlers.accessDeniedProblemDetail(request);
    }

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
