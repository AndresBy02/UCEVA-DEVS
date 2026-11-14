package co.edu.uceva.insumoservice.domain;

import java.util.UUID;

/**
 * Excepcion de dominio lanzada cuando se solicita un insumo que no existe en
 * el catalogo.
 *
 * <p>El manejador global de errores la traduce a una respuesta HTTP 404
 * (Not Found).</p>
 */
public class InsumoNoEncontradoException extends RuntimeException {

    private static final String MENSAJE = "No existe un insumo con el id \"%s\".";

    public InsumoNoEncontradoException(UUID id) {
        super(String.format(MENSAJE, id));
    }
}
