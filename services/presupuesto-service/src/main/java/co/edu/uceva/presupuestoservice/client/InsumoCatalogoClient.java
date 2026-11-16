package co.edu.uceva.presupuestoservice.client;

import co.edu.uceva.presupuestoservice.domain.RecursoNoEncontradoException;
import co.edu.uceva.presupuestoservice.dto.InsumoCatalogoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Component
public class InsumoCatalogoClient {

    private final RestClient restClient;

    public InsumoCatalogoClient(
            RestClient.Builder builder,
            @Value("${servicios.insumo-url:http://localhost:8081}") String insumoUrl) {
        this.restClient = builder.baseUrl(insumoUrl).build();
    }

    public InsumoCatalogoDto obtenerInsumo(UUID insumoId) {
        String auth = obtenerBearerHeader();
        try {
            RestClient.RequestHeadersSpec<?> req = restClient.get()
                    .uri("/api/v1/insumos/{id}", insumoId);
            if (auth != null && !auth.isBlank()) {
                req = req.header(HttpHeaders.AUTHORIZATION, auth);
            }
            return req.retrieve().body(InsumoCatalogoDto.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new RecursoNoEncontradoException("Insumo no encontrado en el catálogo: " + insumoId);
        }
    }

    private static String obtenerBearerHeader() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
    }
}
