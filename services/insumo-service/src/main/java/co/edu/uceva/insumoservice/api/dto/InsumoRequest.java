package co.edu.uceva.insumoservice.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Datos de entrada para crear un insumo del catalogo.
 *
 * <p>El precio unitario se recibe como {@link BigDecimal} y admite tanto un
 * numero JSON como una cadena decimal (Jackson convierte ambos casos). La
 * restriccion {@code @Digits(integer = 16, fraction = 3)} impide que entren
 * mas de 3 decimales: asi se evita el redondeo silencioso que Postgres haria
 * al truncar el valor en la columna {@code NUMERIC(19,3)}.</p>
 */
public class InsumoRequest {

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 40, message = "El código no puede superar los 40 caracteres")
    private String codigo;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(max = 255, message = "La descripción no puede superar los 255 caracteres")
    private String descripcion;

    @NotBlank(message = "La unidad de medida es obligatoria")
    private String unidadMedida;

    @NotNull(message = "El precio unitario es obligatorio")
    @DecimalMin(value = "0", message = "El precio unitario no puede ser negativo")
    @Digits(integer = 16, fraction = 3,
            message = "El precio unitario admite como máximo 16 dígitos enteros y 3 decimales")
    private BigDecimal precioUnitario;

    public InsumoRequest() {
        // Constructor vacio requerido por Jackson
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public void setUnidadMedida(String unidadMedida) {
        this.unidadMedida = unidadMedida;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public void setPrecioUnitario(BigDecimal precioUnitario) {
        this.precioUnitario = precioUnitario;
    }
}
