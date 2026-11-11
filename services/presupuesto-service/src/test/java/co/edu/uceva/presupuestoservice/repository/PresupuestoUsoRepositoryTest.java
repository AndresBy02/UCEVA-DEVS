package co.edu.uceva.presupuestoservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class PresupuestoUsoRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("presupuesto_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private PresupuestoUsoRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID insumoRelacionado = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS items_presupuesto CASCADE");
        jdbcTemplate.execute("DROP TABLE IF EXISTS capitulos CASCADE");
        jdbcTemplate.execute("DROP TABLE IF EXISTS presupuestos CASCADE");
        jdbcTemplate.execute("""
                CREATE TABLE presupuestos (
                    id UUID PRIMARY KEY,
                    codigo VARCHAR(40) NOT NULL,
                    nombre VARCHAR(200) NOT NULL,
                    estado VARCHAR(20) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE capitulos (
                    id UUID PRIMARY KEY,
                    presupuesto_id UUID NOT NULL REFERENCES presupuestos(id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE items_presupuesto (
                    id UUID PRIMARY KEY,
                    capitulo_id UUID NOT NULL REFERENCES capitulos(id),
                    insumo_id UUID NOT NULL
                )
                """);

        UUID presupuestoZ = UUID.randomUUID();
        UUID presupuestoA = UUID.randomUUID();
        insertarPresupuesto(presupuestoZ, "PRE-Z", "Zeta");
        insertarPresupuesto(presupuestoA, "PRE-A", "Alfa");
        UUID presupuestoSinRelacion = UUID.randomUUID();
        insertarPresupuesto(presupuestoSinRelacion, "PRE-N", "No relacionado");
        insertarItem(presupuestoZ, UUID.randomUUID(), insumoRelacionado);
        insertarItem(presupuestoZ, UUID.randomUUID(), insumoRelacionado);
        insertarItem(presupuestoA, UUID.randomUUID(), insumoRelacionado);
        insertarItem(presupuestoSinRelacion, UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void devuelveSoloPresupuestosRelacionadosUnaVezYOrdenadosPorCodigo() {
        assertThat(repository.findByInsumoId(insumoRelacionado))
                .extracting(PresupuestoUsoProjection::getCodigo)
                .containsExactly("PRE-A", "PRE-Z");
    }

    @Test
    void devuelveListaVaciaCuandoNoHayPresupuestosRelacionados() {
        assertThat(repository.findByInsumoId(UUID.randomUUID())).isEmpty();
    }

    private void insertarPresupuesto(UUID id, String codigo, String nombre) {
        jdbcTemplate.update(
                "INSERT INTO presupuestos (id, codigo, nombre, estado) VALUES (?, ?, ?, 'BORRADOR')",
                id, codigo, nombre);
    }

    private void insertarItem(UUID presupuestoId, UUID capituloId, UUID insumoId) {
        jdbcTemplate.update("INSERT INTO capitulos (id, presupuesto_id) VALUES (?, ?)",
                capituloId, presupuestoId);
        jdbcTemplate.update("INSERT INTO items_presupuesto (id, capitulo_id, insumo_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), capituloId, insumoId);
    }
}
