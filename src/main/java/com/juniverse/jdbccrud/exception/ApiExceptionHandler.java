package com.juniverse.jdbccrud.exception;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Errores de la API en formato Problem Details (RFC 9457). Spring JDBC traduce los errores de la base (códigos SQLSTATE)
 * a una jerarquía propia ({@code DuplicateKeyException}, {@code DataIntegrityViolationException}...) sin depender de
 * PostgreSQL, y aquí se convierten en respuestas HTTP.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * 404.
     *
     * @param e excepción
     * @return problema
     */
    @ExceptionHandler({ProductNotFoundException.class, CategoryNotFoundException.class})
    public ProblemDetail notFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, "No encontrado", e.getMessage());
    }

    /**
     * 409 por conflicto de negocio.
     *
     * @param e excepción
     * @return problema
     */
    @ExceptionHandler({VersionConflictException.class, InsufficientStockException.class})
    public ProblemDetail conflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, "Conflicto", e.getMessage());
    }

    /**
     * 409 por un valor único repetido (SKU o nombre de categoría).
     *
     * @param e excepción traducida por Spring a partir del error de PostgreSQL
     * @return problema
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail duplicate(DuplicateKeyException e) {
        return problem(HttpStatus.CONFLICT, "Valor duplicado", "Ya existe un registro con ese valor único (SKU o nombre)");
    }

    /**
     * 409 por otra restricción de la base (llave foránea, CHECK).
     *
     * @param e excepción
     * @return problema
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integrity(DataIntegrityViolationException e) {
        LOG.warn("Restricción de base de datos violada: {}", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Restricción violada", "La operación viola una restricción de integridad de la base de datos");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage()))).toList();
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Error de validación", "Uno o más campos no son válidos");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://api.juniverse.com/errors/" + status.value()));
        return problem;
    }
}
