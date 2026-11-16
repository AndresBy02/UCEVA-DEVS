package co.edu.uceva.presupuestoservice.api;

import co.edu.uceva.presupuestoservice.domain.CodigoDuplicadoException;
import co.edu.uceva.presupuestoservice.domain.PresupuestoAprobadoException;
import co.edu.uceva.presupuestoservice.domain.RecursoNoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail manejarValidacion(MethodArgumentNotValidException ex) {
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                "La solicitud contiene datos inválidos.");
        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "campo", error.getField(),
                        "mensaje", String.valueOf(error.getDefaultMessage())))
                .toList();
        problema.setProperty("errores", errores);
        return problema;
    }

    @ExceptionHandler(PresupuestoAprobadoException.class)
    public ProblemDetail manejarPresupuestoAprobado(PresupuestoAprobadoException ex) {
        return problema(HttpStatus.FORBIDDEN, "Presupuesto aprobado", ex.getMessage());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail manejarNoEncontrado(RecursoNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
    }

    @ExceptionHandler(CodigoDuplicadoException.class)
    public ProblemDetail manejarDuplicado(CodigoDuplicadoException ex) {
        return problema(HttpStatus.CONFLICT, "Conflicto", ex.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail manejarFormatoInvalido(Exception ex) {
        return problema(HttpStatus.BAD_REQUEST, "Solicitud inválida",
                "Formato de datos incorrecto o JSON mal formado.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarErrorInesperado(Exception ex) {
        log.error("Error no controlado en presupuesto-service", ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor",
                "Ocurrió un error inesperado.");
    }

    private static ProblemDetail problema(HttpStatusCode status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
