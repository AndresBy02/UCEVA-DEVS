package co.edu.uceva.presupuestoservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "versiones_presupuesto")
public class VersionPresupuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "presupuesto_id", nullable = false, updatable = false)
    private UUID presupuestoId;

    @Column(name = "numero_version", nullable = false, updatable = false)
    private Integer numeroVersion;

    @Column(name = "etiqueta", length = 120, nullable = false, updatable = false)
    private String etiqueta;

    @Column(name = "comentario_envio", length = 500, updatable = false)
    private String comentarioEnvio;

    @Column(name = "estado_en_momento", length = 20, nullable = false, updatable = false)
    private String estadoEnMomento;

    @Column(name = "creado_por", length = 100, nullable = false, updatable = false)
    private String creadoPor;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "costo_directo", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal costoDirecto;

    @Column(name = "valor_administracion", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal valorAdministracion;

    @Column(name = "valor_imprevistos", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal valorImprevistos;

    @Column(name = "valor_utilidad", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal valorUtilidad;

    @Column(name = "precio_venta", precision = 19, scale = 2, nullable = false, updatable = false)
    private BigDecimal precioVenta;

    @Column(name = "snapshot_json", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String snapshotJson;

    public VersionPresupuesto() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPresupuestoId() {
        return presupuestoId;
    }

    public void setPresupuestoId(UUID presupuestoId) {
        this.presupuestoId = presupuestoId;
    }

    public Integer getNumeroVersion() {
        return numeroVersion;
    }

    public void setNumeroVersion(Integer numeroVersion) {
        this.numeroVersion = numeroVersion;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public void setEtiqueta(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getComentarioEnvio() {
        return comentarioEnvio;
    }

    public void setComentarioEnvio(String comentarioEnvio) {
        this.comentarioEnvio = comentarioEnvio;
    }

    public String getEstadoEnMomento() {
        return estadoEnMomento;
    }

    public void setEstadoEnMomento(String estadoEnMomento) {
        this.estadoEnMomento = estadoEnMomento;
    }

    public String getCreadoPor() {
        return creadoPor;
    }

    public void setCreadoPor(String creadoPor) {
        this.creadoPor = creadoPor;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(OffsetDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public BigDecimal getCostoDirecto() {
        return costoDirecto;
    }

    public void setCostoDirecto(BigDecimal costoDirecto) {
        this.costoDirecto = costoDirecto;
    }

    public BigDecimal getValorAdministracion() {
        return valorAdministracion;
    }

    public void setValorAdministracion(BigDecimal valorAdministracion) {
        this.valorAdministracion = valorAdministracion;
    }

    public BigDecimal getValorImprevistos() {
        return valorImprevistos;
    }

    public void setValorImprevistos(BigDecimal valorImprevistos) {
        this.valorImprevistos = valorImprevistos;
    }

    public BigDecimal getValorUtilidad() {
        return valorUtilidad;
    }

    public void setValorUtilidad(BigDecimal valorUtilidad) {
        this.valorUtilidad = valorUtilidad;
    }

    public BigDecimal getPrecioVenta() {
        return precioVenta;
    }

    public void setPrecioVenta(BigDecimal precioVenta) {
        this.precioVenta = precioVenta;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }
}
