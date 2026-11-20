package co.edu.uceva.presupuestoservice.client;

import co.edu.uceva.presupuestoservice.dto.CalculoDtos.CalculoPresupuestoRequest;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.CalculoPresupuestoResponse;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.DesembolsosRequest;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.DesembolsosResponse;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.InsumosCriticosRequest;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.InsumosCriticosResponse;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.SimulacionSensibilidadRequest;
import co.edu.uceva.presupuestoservice.dto.CalculoDtos.SimulacionSensibilidadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class CalculoMotorClient {

    private final RestClient restClient;

    public CalculoMotorClient(
            RestClient.Builder builder,
            @Value("${servicios.calculo-url:http://localhost:8083}") String calculoUrl) {
        this.restClient = builder.baseUrl(calculoUrl).build();
    }

    public CalculoPresupuestoResponse calcularPresupuesto(CalculoPresupuestoRequest request) {
        return postJson("/api/v1/calculos/presupuesto", request, CalculoPresupuestoResponse.class);
    }

    public SimulacionSensibilidadResponse simularSensibilidad(SimulacionSensibilidadRequest request) {
        return postJson("/api/v1/calculos/sensibilidad", request, SimulacionSensibilidadResponse.class);
    }

    public InsumosCriticosResponse identificarInsumosCriticos(InsumosCriticosRequest request) {
        return postJson("/api/v1/calculos/insumos-criticos", request, InsumosCriticosResponse.class);
    }

    public DesembolsosResponse calcularDesembolsos(DesembolsosRequest request) {
        try {
            return postJson("/api/v1/calculos/desembolsos", request, DesembolsosResponse.class);
        } catch (HttpClientErrorException.BadRequest ex) {
            throw new IllegalArgumentException(ex.getResponseBodyAsString());
        }
    }

    private <T> T postJson(String uri, Object body, Class<T> responseType) {
        String auth = obtenerBearerHeader();
        RestClient.RequestBodySpec req = restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON);
        if (auth != null && !auth.isBlank()) {
            req = req.header(HttpHeaders.AUTHORIZATION, auth);
        }
        return req.body(body).retrieve().body(responseType);
    }

    private static String obtenerBearerHeader() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
    }
}
