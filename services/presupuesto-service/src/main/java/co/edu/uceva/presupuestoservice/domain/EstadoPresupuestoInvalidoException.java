package co.edu.uceva.presupuestoservice.domain;

public class EstadoPresupuestoInvalidoException extends RuntimeException {
    public EstadoPresupuestoInvalidoException(String message) {
        super(message);
    }
}
