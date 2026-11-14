package co.edu.uceva.insumoservice.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias de {@link UnidadMedidaParser} (criterio P09b de la HU-01).
 *
 * <p>Verifica que se aceptan tanto los codigos del enum como las formas
 * amigables, sin distinguir mayusculas/minusculas ni tildes, y que cualquier
 * otro texto se rechaza con un mensaje que incluye el valor invalido y la lista
 * completa de unidades validas.</p>
 */
class UnidadMedidaParserTest {

    /** Catalogo de unidades validas tal como aparece en el mensaje de error. */
    private static final String LISTA_UNIDADES_VALIDAS = "M, M2, M3, KG, BULTO, UNIDAD, DIA";

    /**
     * Formas aceptadas: codigos del enum y alias amigables, en mayusculas y
     * minusculas (la comparacion es insensible a mayusculas y tildes).
     */
    @ParameterizedTest(name = "[{index}] \"{0}\" se interpreta como {1}")
    @CsvSource({
            "m, M",
            "M, M",
            "m2, M2",
            "M2, M2",
            "m², M2",
            "m3, M3",
            "M3, M3",
            "m³, M3",
            "kg, KG",
            "KG, KG",
            "Kg, KG",
            "bulto, BULTO",
            "BULTO, BULTO",
            "Bulto, BULTO",
            "unidad, UNIDAD",
            "UNIDAD, UNIDAD",
            "dia, DIA",
            "DIA, DIA",
            "día, DIA"
    })
    @DisplayName("parse acepta codigos del enum y formas amigables")
    void parseAceptaCodigosYFormasAmigables(String entrada, UnidadMedida esperada) {
        assertThat(UnidadMedidaParser.parse(entrada)).isEqualTo(esperada);
    }

    /**
     * Los espacios accidentales alrededor del texto no impiden resolver la
     * unidad de medida.
     */
    @Test
    @DisplayName("parse ignora los espacios alrededor del valor")
    void parseIgnoraEspaciosAlrededor() {
        assertThat(UnidadMedidaParser.parse("  kg  ")).isEqualTo(UnidadMedida.KG);
        assertThat(UnidadMedidaParser.parse("\tm² ")).isEqualTo(UnidadMedida.M2);
    }

    /**
     * P09b: textos desconocidos se rechazan con el mensaje que enumera el
     * catalogo valido e incluye el valor recibido.
     */
    @ParameterizedTest(name = "[{index}] \"{0}\" se rechaza")
    @ValueSource(strings = {"metros", "cm", "mt", "unidades", "m4", "kilos"})
    @DisplayName("parse rechaza unidades desconocidas con el catalogo valido en el mensaje")
    void parseRechazaUnidadesDesconocidas(String entrada) {
        assertThatThrownBy(() -> UnidadMedidaParser.parse(entrada))
                .isInstanceOf(UnidadMedidaInvalidaException.class)
                .hasMessageContaining("\"" + entrada + "\"")
                .hasMessageContaining(LISTA_UNIDADES_VALIDAS);
    }

    /**
     * El texto vacio no es una unidad valida y el mensaje lo refleja.
     */
    @Test
    @DisplayName("parse rechaza la cadena vacía")
    void parseRechazaCadenaVacia() {
        assertThatThrownBy(() -> UnidadMedidaParser.parse(""))
                .isInstanceOf(UnidadMedidaInvalidaException.class)
                .hasMessageContaining("\"\"")
                .hasMessageContaining(LISTA_UNIDADES_VALIDAS);
    }

    /**
     * Un valor {@code null} tampoco es valido y no produce un
     * {@link NullPointerException}, sino la excepcion de dominio.
     */
    @Test
    @DisplayName("parse rechaza null con la excepción de dominio")
    void parseRechazaNull() {
        assertThatThrownBy(() -> UnidadMedidaParser.parse(null))
                .isInstanceOf(UnidadMedidaInvalidaException.class)
                .hasMessageContaining("\"null\"")
                .hasMessageContaining(LISTA_UNIDADES_VALIDAS);
    }

    /**
     * El mensaje de error cumple P09b: incluye el valor invalido y todas las
     * unidades permitidas, tanto en codigo como en forma amigable.
     */
    @Test
    @DisplayName("el mensaje de error lista todas las unidades válidas y el valor recibido")
    void mensajeDeErrorListaTodasLasUnidades() {
        Throwable error = org.assertj.core.api.Assertions.catchException(
                () -> UnidadMedidaParser.parse("metros"));

        assertThat(error).isInstanceOf(UnidadMedidaInvalidaException.class);
        assertThat(error.getMessage())
                .contains("\"metros\"")
                .contains("M, M2, M3, KG, BULTO, UNIDAD, DIA")
                .contains("m²", "m³", "día");
    }
}
