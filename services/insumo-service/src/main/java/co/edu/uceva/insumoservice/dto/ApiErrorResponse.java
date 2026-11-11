package co.edu.uceva.insumoservice.dto;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;

public record ApiErrorResponse(OffsetDateTime timestamp, int status, String error, String mensaje) {
    public static ApiErrorResponse of(HttpStatus status, String message) {
        return new ApiErrorResponse(OffsetDateTime.now(), status.value(), errorTitle(status), message);
    }

    private static String errorTitle(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Solicitud incorrecta";
            case UNAUTHORIZED -> "No autenticado";
            case FORBIDDEN -> "Acceso denegado";
            case NOT_FOUND -> "Recurso no encontrado";
            case CONFLICT -> "Conflicto";
            case BAD_GATEWAY -> "Servicio dependiente no disponible";
            default -> "Error interno";
        };
    }
}
