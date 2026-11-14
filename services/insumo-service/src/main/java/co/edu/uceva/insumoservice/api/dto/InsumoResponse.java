package co.edu.uceva.insumoservice.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Representacion de salida de un insumo del catalogo.
 *
 * <p>El precio unitario se serializa como cadena decimal exacta (por ejemplo
 * {@code "12.345"}) mediante {@code @JsonFormat(shape = STRING)}, cumpliendo
 * la regla de representacion del dinero sin notacion cientifica. La
 * serializacion global de Jackson no se modifica.</p>
 */
public class InsumoResponse {

    private UUID id;
    private String codigo;
    private String descripcion;

    /** Codigo del enum {@code UnidadMedida}, por ejemplo {@code "M2"}. */
    private String unidadMedida;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal precioUnitario;

    private boolean activo;
    private OffsetDateTime updatedAt;

    public InsumoResponse() {
        // Constructor vacio requerido por Jackson
    }

    public InsumoResponse(UUID id, String codigo, String descripcion, String unidadMedida,
                          BigDecimal precioUnitario, boolean activo, OffsetDateTime updatedAt) {
        this.id = id;
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.unidadMedida = unidadMedida;
        this.precioUnitario = precioUnitario;
        this.activo = activo;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
