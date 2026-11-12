package co.edu.uceva.insumoservice.domain;

/**
 * Unidades de medida permitidas para un insumo.
 *
 * <p>El orden y los nombres deben coincidir con el CHECK de la columna
 * {@code unidad_medida} de la tabla {@code insumos}: M, M2, M3, KG, BULTO,
 * UNIDAD, DIA.</p>
 */
public enum UnidadMedida {
    M,
    M2,
    M3,
    KG,
    BULTO,
    UNIDAD,
    DIA
}
