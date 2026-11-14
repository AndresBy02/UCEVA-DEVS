package co.edu.uceva.insumoservice.api;

import co.edu.uceva.insumoservice.domain.CodigoDuplicadoException;
import co.edu.uceva.insumoservice.domain.InsumoNoEncontradoException;
import co.edu.uceva.insumoservice.domain.UnidadMedidaInvalidaException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

/**
 * Manejador global de errores de la API, basado en {@link ProblemDetail}
 * (RFC 7807) y disponible por defecto en Spring Boot 3.4.
 *
 * <p>Traduce las excepciones de dominio y de validacion a respuestas HTTP
 * consistentes:
 * <ul>
 *   <li>datos invalidos (validacion, unidad de medida, cuerpo ilegible) -&gt; 400;</li>
 *   <li>codigo de insumo duplicado -&gt; 409;</li>
 *   <li>insumo inexistente o {@link ResponseStatusException} acorde -&gt; 404;</li>
 *   <li>cualquier fallo no controlado -&gt; 500 sin exponer el stacktrace.</li>
 * </ul>
 * El formato del cuerpo es {@code application/problem+json} con los campos
 * {@code type}, {@code title}, {@code status}, {@code detail} y, cuando aplica,
 * la propiedad {@code errores} con el detalle por campo.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Titulo usado en los errores de solicitud mal formada. */
    private static final String TITULO_SOLICITUD_INVALIDA = "Solicitud inválida";

    /**
     * Errores de validacion del cuerpo ({@code @Valid @RequestBody}): devuelve
     * 400 incluyendo el mensaje de cada campo invalido.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail manejarArgumentoNoValido(MethodArgumentNotValidException ex) {
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, TITULO_SOLICITUD_INVALIDA,
                "La solicitud contiene datos inválidos.");

        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "campo", error.getField(),
                        "mensaje", String.valueOf(error.getDefaultMessage())))
                .toList();
        problema.setProperty("errores", errores);

        return problema;
    }

    /**
     * Errores de validacion de parametros de metodo (por ejemplo
     * {@code @Min} sobre {@code page} o {@code size}): devuelve 400 con el
     * detalle de cada parametro invalido.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail manejarValidacionDeMetodo(HandlerMethodValidationException ex) {
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, TITULO_SOLICITUD_INVALIDA,
                "La solicitud contiene parámetros inválidos.");

        List<Map<String, String>> errores = ex.getParameterValidationResults().stream()
                .flatMap(resultado -> resultado.getResolvableErrors().stream()
                        .map(error -> Map.of(
                                "campo", nombreDelParametro(resultado.getMethodParameter()),
                                "mensaje", String.valueOf(error.getDefaultMessage()))))
                .toList();
        problema.setProperty("errores", errores);

        return problema;
    }

    /**
     * Errores de validacion de restricciones (por ejemplo, invocaciones
     * validadas fuera del ciclo de binding): devuelve 400.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail manejarViolacionDeRestriccion(ConstraintViolationException ex) {
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, TITULO_SOLICITUD_INVALIDA,
                "La solicitud contiene datos inválidos.");

        List<Map<String, String>> errores = ex.getConstraintViolations().stream()
                .map(violacion -> Map.of(
                        "campo", violacion.getPropertyPath().toString(),
                        "mensaje", String.valueOf(violacion.getMessage())))
                .toList();
        problema.setProperty("errores", errores);

        return problema;
    }

    /**
     * Unidad de medida no reconocida por {@code UnidadMedidaParser}: 400 con el
     * listado de unidades validas.
     */
    @ExceptionHandler(UnidadMedidaInvalidaException.class)
    public ProblemDetail manejarUnidadInvalida(UnidadMedidaInvalidaException ex) {
        return problema(HttpStatus.BAD_REQUEST, TITULO_SOLICITUD_INVALIDA, ex.getMessage());
    }

    /**
     * Codigo de insumo duplicado: 409 Conflict mencionando el codigo.
     */
    @ExceptionHandler(CodigoDuplicadoException.class)
    public ProblemDetail manejarCodigoDuplicado(CodigoDuplicadoException ex) {
        return problema(HttpStatus.CONFLICT, "Conflicto", ex.getMessage());
    }

    /**
     * Insumo inexistente: 404 Not Found.
     */
    @ExceptionHandler(InsumoNoEncontradoException.class)
    public ProblemDetail manejarInsumoNoEncontrado(InsumoNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
    }

    /**
     * Cualquier {@link ResponseStatusException} conserva su estado HTTP y su
     * motivo, por ejemplo un 404 originado en la capa web.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail manejarEstadoDeRespuesta(ResponseStatusException ex) {
        HttpStatusCode estado = ex.getStatusCode();
        String detalle = ex.getReason() != null ? ex.getReason() : "La solicitud no pudo ser procesada.";
        return problema(estado, TITULO_SOLICITUD_INVALIDA, detalle);
    }

    /**
     * Cuerpo JSON ilegible o con tipos incompatibles (por ejemplo un precio no
     * numerico): 400 sin revelar detalles internos.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail manejarCuerpoIlegible(HttpMessageNotReadableException ex) {
        return problema(HttpStatus.BAD_REQUEST, TITULO_SOLICITUD_INVALIDA,
                "El cuerpo de la solicitud no es un JSON válido o contiene valores con un formato incorrecto.");
    }

    /**
     * Parametro de ruta o de consulta con un tipo incompatible (por ejemplo un
     * identificador que no es UUID): 400.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail manejarTipoIncompatible(MethodArgumentTypeMismatchException ex) {
        return problema(HttpStatus.BAD_REQUEST, TITULO_SOLICITUD_INVALIDA,
                "El parámetro \"" + ex.getName() + "\" tiene un formato inválido.");
    }

    /**
     * Ruta inexistente: 404 en lugar de dejar que el fallback la convierta en
     * un error 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail manejarRutaNoEncontrada(NoResourceFoundException ex) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado",
                "La ruta solicitada no existe en este servicio.");
    }

    /**
     * Metodo HTTP no soportado para la ruta solicitada: 405.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail manejarMetodoNoSoportado(HttpRequestMethodNotSupportedException ex) {
        return problema(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido",
                "El método HTTP \"" + ex.getMethod() + "\" no está soportado para esta ruta.");
    }

    /**
     * Fallback para cualquier error no controlado: 500 sin filtrar el
     * stacktrace ni detalles internos al cliente.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarErrorInesperado(Exception ex) {
        log.error("Error no controlado en la API de insumos", ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor",
                "Ocurrió un error inesperado. Por favor contacte al administrador.");
    }

    /**
     * Construye un {@link ProblemDetail} con estado, titulo y detalle.
     */
    private static ProblemDetail problema(HttpStatusCode estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }

    /**
     * Nombre legible de un parametro de metodo para el detalle del error.
     */
    private static String nombreDelParametro(org.springframework.core.MethodParameter parametro) {
        String nombre = parametro.getParameterName();
        return nombre != null ? nombre : "parámetro";
    }
}
