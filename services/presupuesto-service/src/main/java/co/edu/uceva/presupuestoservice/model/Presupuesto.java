package co.edu.uceva.presupuestoservice.model;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "presupuestos")
public class Presupuesto {
    @Id
    private UUID id;

    protected Presupuesto() {
    }
}
