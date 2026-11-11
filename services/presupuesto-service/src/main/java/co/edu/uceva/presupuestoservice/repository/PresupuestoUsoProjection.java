package co.edu.uceva.presupuestoservice.repository;

import java.util.UUID;

public interface PresupuestoUsoProjection {
    UUID getId();

    String getCodigo();

    String getNombre();

    String getEstado();
}
