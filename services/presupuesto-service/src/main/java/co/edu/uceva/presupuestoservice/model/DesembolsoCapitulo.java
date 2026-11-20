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
import java.util.UUID;

@Entity
@Table(name = "desembolsos_capitulo")
public class DesembolsoCapitulo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "presupuesto_id", nullable = false)
    private UUID presupuestoId;

    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "capitulo_id", nullable = false)
    private UUID capituloId;

    @Column(name = "semana", nullable = false)
    private Integer semana;

    @Column(name = "porcentaje_avance", precision = 7, scale = 4, nullable = false)
    private BigDecimal porcentajeAvance;

    public DesembolsoCapitulo() {
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

    public UUID getCapituloId() {
        return capituloId;
    }

    public void setCapituloId(UUID capituloId) {
        this.capituloId = capituloId;
    }

    public Integer getSemana() {
        return semana;
    }

    public void setSemana(Integer semana) {
        this.semana = semana;
    }

    public BigDecimal getPorcentajeAvance() {
        return porcentajeAvance;
    }

    public void setPorcentajeAvance(BigDecimal porcentajeAvance) {
        this.porcentajeAvance = porcentajeAvance;
    }
}
