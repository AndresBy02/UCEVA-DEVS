package co.edu.uceva.insumoservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class RestPresupuestoClientTest {
    @Test
    void traduceLaIndisponibilidadDelServicioDePresupuestosA502() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID insumoId = UUID.randomUUID();
        server.expect(requestTo("http://presupuesto-service/api/v1/presupuestos?insumoId=" + insumoId))
                .andRespond(withServerError());
        RestPresupuestoClient client = new RestPresupuestoClient(builder, "http://presupuesto-service");

        assertThatThrownBy(() -> client.findByInsumo(insumoId, "token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(((ResponseStatusException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));

        server.verify();
    }
}
