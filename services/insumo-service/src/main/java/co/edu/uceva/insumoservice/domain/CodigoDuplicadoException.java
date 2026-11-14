package co.edu.uceva.insumoservice.domain;

/**
 * Excepcion de dominio lanzada cuando se intenta registrar un insumo cuyo
 * {@code codigo} ya existe en el catalogo.
 *
 * <p>El manejador global de errores la traduce a una respuesta HTTP 409
 * (Conflict) cuyo detalle menciona el codigo duplicado.</p>
 */
public class CodigoDuplicadoException extends RuntimeException {

    private static final String MENSAJE =
            "Ya existe un insumo con el código \"%s\". El código debe ser único en el catálogo.";

    public CodigoDuplicadoException(String codigo) {
        super(String.format(MENSAJE, codigo));
    }
}
