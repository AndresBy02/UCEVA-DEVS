package co.edu.uceva.insumoservice.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Conversor de texto libre a {@link UnidadMedida}.
 *
 * <p>Acepta, sin distinguir mayusculas/minusculas ni tildes, tanto los codigos
 * del enum ({@code M}, {@code M2}, {@code M3}, {@code KG}, {@code BULTO},
 * {@code UNIDAD}, {@code DIA}) como las formas amigables de la HU-01
 * ({@code m}, {@code m²}, {@code m³}, {@code kg}, {@code bulto},
 * {@code unidad}, {@code día}).</p>
 *
 * <p>El mapeo concreto de las formas amigables es: {@code m -> M},
 * {@code m² -> M2}, {@code m³ -> M3} y {@code día -> DIA}.</p>
 */
public final class UnidadMedidaParser {

    /** Marcas diacriticas combinantes que se eliminan al normalizar el texto. */
    private static final Pattern DIACRITICOS = Pattern.compile("\\p{M}+");

    private UnidadMedidaParser() {
        // Clase utiliaria: no se instancia
    }

    /**
     * Resuelve la unidad de medida a partir del texto recibido.
     *
     * @param valor texto enviado por el cliente (codigo del enum o forma amigable)
     * @return la {@link UnidadMedida} correspondiente
     * @throws UnidadMedidaInvalidaException si el texto no coincide con ninguna unidad valida
     */
    public static UnidadMedida parse(String valor) {
        String normalizado = normalizar(valor);

        return switch (normalizado) {
            case "m" -> UnidadMedida.M;
            case "m2", "m²" -> UnidadMedida.M2;
            case "m3", "m³" -> UnidadMedida.M3;
            case "kg" -> UnidadMedida.KG;
            case "bulto" -> UnidadMedida.BULTO;
            case "unidad" -> UnidadMedida.UNIDAD;
            case "dia" -> UnidadMedida.DIA;
            default -> throw new UnidadMedidaInvalidaException(valor);
        };
    }

    /**
     * Normaliza el texto: quita tildes/diacriticos, recorta espacios y pasa a
     * minusculas. Los simbolos {@code ²} y {@code ³} no son diacriticos y se
     * conservan para poder distinguirlos de {@code m2}/{@code m3}.
     */
    private static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String sinTildes = DIACRITICOS
                .matcher(Normalizer.normalize(valor, Normalizer.Form.NFD))
                .replaceAll("");
        return sinTildes.trim().toLowerCase(Locale.ROOT);
    }
}
