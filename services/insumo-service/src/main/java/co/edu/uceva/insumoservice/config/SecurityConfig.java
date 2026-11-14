package co.edu.uceva.insumoservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Configuracion de seguridad del microservicio.
 *
 * <p>Todo endpoint exige un JWT valido emitido por Keycloak, salvo los
 * endpoints de observabilidad de Actuator ({@code /actuator/health} e
 * {@code /actuator/info}) que se usan para los healthchecks de Docker.</p>
 *
 * <p><strong>Autorizacion por roles (UC-18):</strong> los roles de realm de
 * Keycloak ({@code realm_access.roles}) se convierten en authorities con el
 * prefijo {@code ROLE_}. Se aplica la matriz de autorizacion congelada:
 * <ul>
 *   <li>{@code POST /api/v1/insumos} exige el rol {@code presupuestador}
 *       (un {@code revisor} recibe 403 y un anonimo 401);</li>
 *   <li>{@code GET /api/v1/insumos/**} exige solo estar autenticado, por lo que
 *       tanto {@code presupuestador} como {@code revisor} obtienen 200.</li>
 * </ul>
 * Las PRUEBAS automatizadas de estos escenarios (incluido el 403 del rol
 * {@code revisor}) se implementan en HU-29 / UC-19.</p>
 */
@Configuration
public class SecurityConfig {

    /** Claim del JWT de Keycloak que contiene los roles de realm. */
    private static final String CLAIM_REALM_ACCESS = "realm_access";

    /** Clave interna del claim de roles dentro de {@code realm_access}. */
    private static final String CLAIM_ROLES = "roles";

    /** Prefijo exigido por Spring Security para usar {@code hasRole(...)}. */
    private static final String PREFIJO_ROL = "ROLE_";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info").permitAll()
                        // Matriz de autorizacion congelada (RA-05):
                        // escritura del catalogo solo para presupuestador.
                        .requestMatchers(HttpMethod.POST, "/api/v1/insumos")
                        .hasRole("presupuestador")
                        // Lectura del catalogo para cualquier usuario autenticado.
                        .requestMatchers(HttpMethod.GET, "/api/v1/insumos/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Convierte los roles de realm de Keycloak en authorities {@code ROLE_*}.
     *
     * <p>Se usa el converter por defecto de Spring Security, pero sustituyendo
     * la extraccion de authorities para leer {@code realm_access.roles}. Si el
     * claim no viene en el token, el usuario queda autenticado sin roles.</p>
     */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::extraerRolesDelRealm);
        return converter;
    }

    /**
     * Extrae los roles de realm del JWT y los transforma en authorities con el
     * prefijo {@link #PREFIJO_ROL}.
     *
     * @param jwt token ya validado criptograficamente
     * @return coleccion de authorities (posiblemente vacia)
     */
    private static Collection<GrantedAuthority> extraerRolesDelRealm(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(CLAIM_REALM_ACCESS);
        if (realmAccess == null) {
            return List.of();
        }

        Object roles = realmAccess.get(CLAIM_ROLES);
        if (!(roles instanceof Collection<?> listaDeRoles)) {
            return List.of();
        }

        return listaDeRoles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority(PREFIJO_ROL + rol))
                .toList();
    }
}
