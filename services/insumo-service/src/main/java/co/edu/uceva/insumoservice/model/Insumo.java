package co.edu.uceva.insumoservice.model;

import co.edu.uceva.insumoservice.domain.UnidadMedida;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Insumo del catalogo maestro.
 *
 * <p>Mapea la tabla {@code insumos} de la base de datos {@code insumo_db}.
 * El precio unitario se representa con {@link BigDecimal} y una escala exacta
 * de 3 decimales (NUMERIC(19,3)) para evitar cualquier perdida de precision
 * propia de {@code float}/{@code double}.</p>
 */
@Entity
@Table(name = "insumos")
public class Insumo {

    /** Identificador unico del insumo (columna uuid de Postgres). */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Codigo unico del insumo dentro del catalogo (varchar 40). */
    @Column(name = "codigo", length = 40, nullable = false, unique = true)
    private String codigo;

    /** Descripcion legible del insumo (varchar 255). */
    @Column(name = "descripcion", length = 255, nullable = false)
    private String descripcion;

    /** Unidad de medida; se persiste como texto para respetar el CHECK de la BD. */
    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_medida", length = 15, nullable = false)
    private UnidadMedida unidadMedida;

    /**
     * Precio unitario exacto con escala 3 (NUMERIC(19,3)).
     * No usar {@code double}: 12.345 debe almacenarse de forma exacta.
     */
    @Column(name = "precio_unitario", precision = 19, scale = 3, nullable = false)
    private BigDecimal precioUnitario;

    /** Indica si el insumo esta activo en el catalogo. */
    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    /** Usuario que realizo la ultima modificacion (varchar 100). */
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /** Marca temporal de la ultima modificacion (timestamp with time zone). */
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public Insumo() {
        // Constructor requerido por JPA
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

    public UnidadMedida getUnidadMedida() {
        return unidadMedida;
    }

    public void setUnidadMedida(UnidadMedida unidadMedida) {
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

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
