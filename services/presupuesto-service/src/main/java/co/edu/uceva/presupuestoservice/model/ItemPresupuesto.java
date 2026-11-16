package co.edu.uceva.presupuestoservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "items_presupuesto")
public class ItemPresupuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "capitulo_id", nullable = false)
    private Capitulo capitulo;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "insumo_id", nullable = false)
    private UUID insumoId;

    @Column(name = "codigo_insumo", length = 40, nullable = false)
    private String codigoInsumo;

    @Column(name = "descripcion", length = 255, nullable = false)
    private String descripcion;

    @Column(name = "unidad_medida", length = 15, nullable = false)
    private String unidadMedida;

    @Column(name = "cantidad", precision = 19, scale = 3, nullable = false)
    private BigDecimal cantidad;

    // Copia del precio al momento de incorporar o sincronizar el item (snapshot)
    @Column(name = "precio_unitario_copia", precision = 19, scale = 3, nullable = false)
    private BigDecimal precioUnitarioCopia;

    public ItemPresupuesto() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Capitulo getCapitulo() {
        return capitulo;
    }

    public void setCapitulo(Capitulo capitulo) {
        this.capitulo = capitulo;
    }

    public Integer getOrden() {
        return orden;
    }

    public void setOrden(Integer orden) {
        this.orden = orden;
    }

    public UUID getInsumoId() {
        return insumoId;
    }

    public void setInsumoId(UUID insumoId) {
        this.insumoId = insumoId;
    }

    public String getCodigoInsumo() {
        return codigoInsumo;
    }

    public void setCodigoInsumo(String codigoInsumo) {
        this.codigoInsumo = codigoInsumo;
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

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public void setCantidad(BigDecimal cantidad) {
        this.cantidad = cantidad;
    }

    public BigDecimal getPrecioUnitarioCopia() {
        return precioUnitarioCopia;
    }

    public void setPrecioUnitarioCopia(BigDecimal precioUnitarioCopia) {
        this.precioUnitarioCopia = precioUnitarioCopia;
    }
}
