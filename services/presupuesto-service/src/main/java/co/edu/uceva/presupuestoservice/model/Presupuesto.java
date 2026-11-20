package co.edu.uceva.presupuestoservice.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "presupuestos")
public class Presupuesto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "codigo", length = 40, nullable = false, unique = true)
    private String codigo;

    @Column(name = "nombre", length = 200, nullable = false)
    private String nombre;

    @Column(name = "cliente", length = 150, nullable = false)
    private String cliente;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoPresupuesto estado = EstadoPresupuesto.BORRADOR;

    @Column(name = "porcentaje_administracion", precision = 7, scale = 4, nullable = false)
    private BigDecimal porcentajeAdministracion = new BigDecimal("8.0000");

    @Column(name = "porcentaje_imprevistos", precision = 7, scale = 4, nullable = false)
    private BigDecimal porcentajeImprevistos = new BigDecimal("3.0000");

    @Column(name = "porcentaje_utilidad", precision = 7, scale = 4, nullable = false)
    private BigDecimal porcentajeUtilidad = new BigDecimal("5.0000");

    @Column(name = "costo_fijo", precision = 19, scale = 2)
    private BigDecimal costoFijo = new BigDecimal("0.00");

    @Column(name = "precio_venta_unitario", precision = 19, scale = 2)
    private BigDecimal precioVentaUnitario;

    @Column(name = "costo_variable_unitario", precision = 19, scale = 2)
    private BigDecimal costoVariableUnitario;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "presupuesto_base_id")
    private UUID presupuestoBaseId;

    @Column(name = "creado_por", length = 100, nullable = false)
    private String creadoPor;

    @Column(name = "fecha_creacion")
    private OffsetDateTime fechaCreacion;

    @Column(name = "enviado_revision_por", length = 100)
    private String enviadoRevisionPor;

    @Column(name = "fecha_envio_revision")
    private OffsetDateTime fechaEnvioRevision;

    @Column(name = "aprobado_por", length = 100)
    private String aprobadoPor;

    @Column(name = "fecha_aprobacion")
    private OffsetDateTime fechaAprobacion;

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    @Column(name = "rechazado_por", length = 100)
    private String rechazadoPor;

    @Column(name = "fecha_rechazo")
    private OffsetDateTime fechaRechazo;

    @OneToMany(mappedBy = "presupuesto", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<Capitulo> capitulos = new ArrayList<>();

    public Presupuesto() {
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

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCliente() {
        return cliente;
    }

    public void setCliente(String cliente) {
        this.cliente = cliente;
    }

    public EstadoPresupuesto getEstado() {
        return estado;
    }

    public void setEstado(EstadoPresupuesto estado) {
        this.estado = estado;
    }

    public BigDecimal getPorcentajeAdministracion() {
        return porcentajeAdministracion;
    }

    public void setPorcentajeAdministracion(BigDecimal porcentajeAdministracion) {
        this.porcentajeAdministracion = porcentajeAdministracion;
    }

    public BigDecimal getPorcentajeImprevistos() {
        return porcentajeImprevistos;
    }

    public void setPorcentajeImprevistos(BigDecimal porcentajeImprevistos) {
        this.porcentajeImprevistos = porcentajeImprevistos;
    }

    public BigDecimal getPorcentajeUtilidad() {
        return porcentajeUtilidad;
    }

    public void setPorcentajeUtilidad(BigDecimal porcentajeUtilidad) {
        this.porcentajeUtilidad = porcentajeUtilidad;
    }

    public BigDecimal getCostoFijo() {
        return costoFijo;
    }

    public void setCostoFijo(BigDecimal costoFijo) {
        this.costoFijo = costoFijo;
    }

    public BigDecimal getPrecioVentaUnitario() {
        return precioVentaUnitario;
    }

    public void setPrecioVentaUnitario(BigDecimal precioVentaUnitario) {
        this.precioVentaUnitario = precioVentaUnitario;
    }

    public BigDecimal getCostoVariableUnitario() {
        return costoVariableUnitario;
    }

    public void setCostoVariableUnitario(BigDecimal costoVariableUnitario) {
        this.costoVariableUnitario = costoVariableUnitario;
    }

    public UUID getPresupuestoBaseId() {
        return presupuestoBaseId;
    }

    public void setPresupuestoBaseId(UUID presupuestoBaseId) {
        this.presupuestoBaseId = presupuestoBaseId;
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

    public String getAprobadoPor() {
        return aprobadoPor;
    }

    public void setAprobadoPor(String aprobadoPor) {
        this.aprobadoPor = aprobadoPor;
    }

    public OffsetDateTime getFechaAprobacion() {
        return fechaAprobacion;
    }

    public void setFechaAprobacion(OffsetDateTime fechaAprobacion) {
        this.fechaAprobacion = fechaAprobacion;
    }

    public List<Capitulo> getCapitulos() {
        return capitulos;
    }

    public void setCapitulos(List<Capitulo> capitulos) {
        this.capitulos = capitulos;
    }

    public String getEnviadoRevisionPor() {
        return enviadoRevisionPor;
    }

    public void setEnviadoRevisionPor(String enviadoRevisionPor) {
        this.enviadoRevisionPor = enviadoRevisionPor;
    }

    public OffsetDateTime getFechaEnvioRevision() {
        return fechaEnvioRevision;
    }

    public void setFechaEnvioRevision(OffsetDateTime fechaEnvioRevision) {
        this.fechaEnvioRevision = fechaEnvioRevision;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    public String getRechazadoPor() {
        return rechazadoPor;
    }

    public void setRechazadoPor(String rechazadoPor) {
        this.rechazadoPor = rechazadoPor;
    }

    public OffsetDateTime getFechaRechazo() {
        return fechaRechazo;
    }

    public void setFechaRechazo(OffsetDateTime fechaRechazo) {
        this.fechaRechazo = fechaRechazo;
    }
}
