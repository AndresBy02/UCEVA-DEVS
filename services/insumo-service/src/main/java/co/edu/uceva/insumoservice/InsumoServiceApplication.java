package co.edu.uceva.insumoservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio de Catalogo Maestro de Insumos.
 *
 * <p>Expone el catalogo de insumos y sus precios unitarios con precision exacta
 * NUMERIC(19,3). La base de datos es la fuente de verdad del esquema y se
 * inicializa con el script de infraestructura de Postgres.</p>
 */
@SpringBootApplication
public class InsumoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsumoServiceApplication.class, args);
    }
}
