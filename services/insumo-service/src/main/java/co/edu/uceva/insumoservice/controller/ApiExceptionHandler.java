package co.edu.uceva.insumoservice.controller;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import co.edu.uceva.insumoservice.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(this::validationMessage)
                .distinct()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        return error(HttpStatus.BAD_REQUEST, "Los parámetros enviados no son válidos");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        Throwable cause = exception.getMostSpecificCause();
        if (cause instanceof InvalidFormatException invalid
                && invalid.getTargetType().isEnum()) {
            String validValues = Arrays.stream(invalid.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            return error(HttpStatus.BAD_REQUEST,
                    "Unidad de medida inválida. Valores permitidos: " + validValues);
        }
        return error(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud contiene un campo desconocido o un formato no válido");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return error(status, exception.getReason() == null ? "Solicitud no válida" : exception.getReason());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(DataIntegrityViolationException exception) {
        return error(HttpStatus.CONFLICT, "La operación entra en conflicto con los datos existentes");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        LOGGER.error("Error interno al procesar la solicitud", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno al procesar la solicitud");
    }

    private String validationMessage(FieldError error) {
        return switch (error.getField()) {
            case "descripcion" -> "La descripción es obligatoria y admite hasta 255 caracteres";
            case "unidadMedida" -> "La unidad de medida es obligatoria";
            case "precioUnitario" -> "El precio unitario debe ser no negativo y tener máximo 3 decimales";
            case "activo" -> "El campo activo es obligatorio";
            default -> "El campo " + error.getField() + " no es válido";
        };
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status, message));
    }
}
