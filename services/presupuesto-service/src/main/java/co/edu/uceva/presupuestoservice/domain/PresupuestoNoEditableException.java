package co.edu.uceva.presupuestoservice.domain;

public class PresupuestoNoEditableException extends RuntimeException {
    public PresupuestoNoEditableException(String message) {
        super(message);
    }
}
