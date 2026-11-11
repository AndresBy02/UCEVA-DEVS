package co.edu.uceva.insumoservice.client;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import co.edu.uceva.insumoservice.dto.PresupuestoUsoResponse;

@Component
public class RestPresupuestoClient implements PresupuestoClient {
    private final RestClient restClient;

    public RestPresupuestoClient(
            RestClient.Builder builder,
            @Value("${services.presupuesto.url}") String presupuestoUrl) {
        this.restClient = builder.baseUrl(presupuestoUrl).build();
    }

    @Override
    public List<PresupuestoUsoResponse> findByInsumo(UUID insumoId, String accessToken) {
        try {
            List<PresupuestoUsoResponse> presupuestos = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/presupuestos")
                            .queryParam("insumoId", insumoId)
                            .build())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            if (presupuestos == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "presupuesto-service devolvio una respuesta sin cuerpo");
            }
            return presupuestos;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No fue posible consultar los presupuestos asociados al insumo", exception);
        }
    }
}
