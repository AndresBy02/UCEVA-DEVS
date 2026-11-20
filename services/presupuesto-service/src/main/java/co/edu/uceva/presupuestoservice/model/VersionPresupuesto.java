package co.edu.uceva.presupuestoservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Snapshot inmutable de un presupuesto enviado a revisión (RF-10 / UC-38).
 * Todas las columnas son de solo lectura después de insertar: las versiones
 * nunca se modifican y sirven para demostrar qué se envió exactamente.
 */
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

    @Column(name = "numero", nullable = false, updatable = false)
    private Integer numero;

    @Column(name = "codigo_presupuesto", length = 40, nullable = false, updatable = false)
    private String codigoPresupuesto;

    @Column(name = "nombre_presupuesto", length = 200, nullable = false, updatable = false)
    private String nombrePresupuesto;

    @Column(name = "estado", length = 20, nullable = false, updatable = false)
    private String estado = "ENVIADA";

    @Column(name = "creado_por", length = 100, nullable = false, updatable = false)
    private String creadoPor;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "destinatario_sub", length = 100, nullable = false, updatable = false)
    private String destinatarioSub;

    @Column(name = "fecha_notificacion", nullable = false, updatable = false)
    private OffsetDateTime fechaNotificacion;

    @Column(name = "mensaje", length = 500, updatable = false)
    private String mensaje;

    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "snapshot_json", nullable = false, updatable = false)
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

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
    }

    public String getCodigoPresupuesto() {
        return codigoPresupuesto;
    }

    public void setCodigoPresupuesto(String codigoPresupuesto) {
        this.codigoPresupuesto = codigoPresupuesto;
    }

    public String getNombrePresupuesto() {
        return nombrePresupuesto;
    }

    public void setNombrePresupuesto(String nombrePresupuesto) {
        this.nombrePresupuesto = nombrePresupuesto;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
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

    public String getDestinatarioSub() {
        return destinatarioSub;
    }

    public void setDestinatarioSub(String destinatarioSub) {
        this.destinatarioSub = destinatarioSub;
    }

    public OffsetDateTime getFechaNotificacion() {
        return fechaNotificacion;
    }

    public void setFechaNotificacion(OffsetDateTime fechaNotificacion) {
        this.fechaNotificacion = fechaNotificacion;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }
}
