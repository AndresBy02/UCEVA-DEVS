package co.edu.uceva.insumoservice.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "historial_precios_insumo")
public class HistorialPrecioInsumo {
    @Id
    private UUID id;

    @Column(name = "insumo_id", nullable = false)
    private UUID insumoId;

    @Column(name = "precio_anterior", precision = 19, scale = 3)
    private BigDecimal precioAnterior;

    @Column(name = "precio_nuevo", nullable = false, precision = 19, scale = 3)
    private BigDecimal precioNuevo;

    @Column(name = "modificado_por", length = 100)
    private String modificadoPor;

    @Column(name = "fecha_cambio", nullable = false)
    private OffsetDateTime fechaCambio;

    protected HistorialPrecioInsumo() {
    }

    public HistorialPrecioInsumo(UUID id, UUID insumoId, BigDecimal precioAnterior,
            BigDecimal precioNuevo, String modificadoPor, OffsetDateTime fechaCambio) {
        this.id = id;
        this.insumoId = insumoId;
        this.precioAnterior = precioAnterior;
        this.precioNuevo = precioNuevo;
        this.modificadoPor = modificadoPor;
        this.fechaCambio = fechaCambio;
    }

    public UUID getId() {
        return id;
    }

    public UUID getInsumoId() {
        return insumoId;
    }

    public BigDecimal getPrecioAnterior() {
        return precioAnterior;
    }

    public BigDecimal getPrecioNuevo() {
        return precioNuevo;
    }

    public String getModificadoPor() {
        return modificadoPor;
    }

    public OffsetDateTime getFechaCambio() {
        return fechaCambio;
    }
}
