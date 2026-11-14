package co.edu.uceva.insumoservice.domain;

/**
 * Excepcion de dominio lanzada cuando el texto recibido no corresponde a
 * ninguna unidad de medida permitida.
 *
 * <p>El manejador global de errores la traduce a una respuesta HTTP 400
 * (Bad Request) con el mensaje de las unidades validas.</p>
 */
public class UnidadMedidaInvalidaException extends RuntimeException {

    /**
     * Mensaje unico de error: incluye el valor recibido y el catalogo de
     * unidades aceptadas (codigos del enum y formas amigables de la HU-01).
     */
    private static final String MENSAJE =
            "Unidad de medida inválida: \"%s\". Las unidades válidas son: "
                    + "M, M2, M3, KG, BULTO, UNIDAD, DIA "
                    + "(o sus formas amigables: m, m², m³, kg, bulto, unidad, día)";

    public UnidadMedidaInvalidaException(String valor) {
        super(String.format(MENSAJE, valor));
    }
}
