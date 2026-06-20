package com.myplans.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PF-018 — Ver historial de eventos (GET /api/v1/historial/tag/{id})
 * PF-019 — Historial muestra eventos con observaciones (origen IA se registra en obs)
 * PI-010  — Audit rechaza POST sin X-Internal-Token
 * PI-011  — Audit no acepta PUT/DELETE/PATCH (inmutable)
 *
 * Complementa HistorialIntegrationTest.java (ya existente).
 * Nota: GET /api/v1/historial no existe — el endpoint es GET /api/v1/historial/tag/{idTag}.
 * Solo ADMIN y SUPERVISOR pueden consultar; POST requiere CORE_SERVICE (X-Internal-Token).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HistorialPaginadoIntegrationTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void setUpHttpClient() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    private HttpHeaders headersInterno() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Internal-Token", INTERNAL_TOKEN);
        return h;
    }

    private HttpHeaders headersJwt(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private Integer insertarEvento(Integer idTag, Integer idUsuario,
                                   String estadoNuevo, String observaciones) {
        String body = String.format("""
                {
                  "idTag": %d,
                  "idUsuario": %d,
                  "estadoAnterior": "PENDIENTE",
                  "estadoNuevo": "%s",
                  "observaciones": "%s"
                }
                """, idTag, idUsuario, estadoNuevo, observaciones);
        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "/api/v1/historial", new HttpEntity<>(body, headersInterno()), Map.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode(),
                "Error insertando evento: " + resp.getBody());
        return ((Number) resp.getBody().get("idHistorial")).intValue();
    }

    // PF-018: GET /api/v1/historial/tag/{id} — lista eventos de un tag

    @Test
    void givenAdminToken_whenGetByTag_thenReturnListOfEvents() {
        Integer idTag = 10;
        insertarEvento(idTag, 1, "APROBADO", "Verificado en terreno");
        insertarEvento(idTag, 1, "OBSERVADO", "Falla detectada");

        String token = TestJwtHelper.tokenFor("admin@test.com", 1, List.of("ROLE_ADMIN"));
        ResponseEntity<List> resp = restTemplate.exchange(
                "/api/v1/historial/tag/" + idTag,
                HttpMethod.GET,
                new HttpEntity<>(headersJwt(token)),
                List.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(2, resp.getBody().size(),
                "Deben estar los 2 eventos del tag");
    }

    @Test
    void givenSupervisorToken_whenGetByTag_thenReturnListOfEvents() {
        Integer idTag = 11;
        insertarEvento(idTag, 1, "APROBADO", "OK supervisor");

        String token = TestJwtHelper.tokenFor("sup@test.com", 2, List.of("ROLE_SUPERVISOR"));
        ResponseEntity<List> resp = restTemplate.exchange(
                "/api/v1/historial/tag/" + idTag,
                HttpMethod.GET,
                new HttpEntity<>(headersJwt(token)),
                List.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertFalse(resp.getBody().isEmpty());
    }

    @Test
    void givenUserToken_whenGetByTag_thenReturn403() {
        String token = TestJwtHelper.tokenFor("op@test.com", 2, List.of("ROLE_USER"));
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/historial/tag/1",
                HttpMethod.GET,
                new HttpEntity<>(headersJwt(token)),
                Map.class);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void givenNoToken_whenGetByTag_thenReturn401() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/historial/tag/1",
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertNotNull(resp.getBody().get("message"));
    }

    // PF-019: Eventos con observación "origen=IA" visibles en historial

    @Test
    void givenEventWithIAObservation_whenGetByTag_thenObservacionIsVisible() {
        Integer idTag = 30;
        insertarEvento(idTag, 1, "APROBADO", "origen=IA aplicado automáticamente");
        insertarEvento(idTag, 1, "OBSERVADO", "Revisión manual");

        String token = TestJwtHelper.tokenFor("admin@test.com", 1, List.of("ROLE_ADMIN"));
        ResponseEntity<List> resp = restTemplate.exchange(
                "/api/v1/historial/tag/" + idTag,
                HttpMethod.GET,
                new HttpEntity<>(headersJwt(token)),
                List.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<Map<?, ?>> eventos = (List<Map<?, ?>>) resp.getBody();

        boolean hayEventoIA = eventos.stream()
                .anyMatch(e -> e.get("observaciones") != null
                        && e.get("observaciones").toString().toLowerCase().contains("ia"));
        assertTrue(hayEventoIA,
                "Debe existir al menos un evento con observaciones relacionadas a IA");
    }

    @Test
    void givenEventsByTag_whenGetByTag_thenReturnOnlyTagEvents() {
        Integer idTagPropio = 40;
        insertarEvento(idTagPropio, 1, "APROBADO", "Evento tag propio");
        insertarEvento(41, 2, "OBSERVADO", "Evento otro tag");

        String token = TestJwtHelper.tokenFor("admin@test.com", 1, List.of("ROLE_ADMIN"));
        ResponseEntity<List> resp = restTemplate.exchange(
                "/api/v1/historial/tag/" + idTagPropio,
                HttpMethod.GET,
                new HttpEntity<>(headersJwt(token)),
                List.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        List<Map<?, ?>> eventos = (List<Map<?, ?>>) resp.getBody();
        assertTrue(eventos.stream().allMatch(e -> idTagPropio.equals(e.get("idTag"))),
                "Solo deben aparecer eventos del TAG solicitado");
    }

    // PI-010: Audit rechaza POST sin X-Internal-Token

    @Test
    void givenNoInternalToken_whenPost_thenReturn401() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"idTag\":1,\"idUsuario\":1,\"estadoNuevo\":\"APROBADO\"}";

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "/api/v1/historial", new HttpEntity<>(body, h), Map.class);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode(),
                "Sin X-Internal-Token debe retornar 401");
    }

    @Test
    void givenJwtInsteadOfInternalToken_whenPost_thenReturn403() {
        String token = TestJwtHelper.tokenFor("aud@test.com", 1, List.of("ROLE_ADMIN"));
        String body = "{\"idTag\":1,\"idUsuario\":1,\"estadoNuevo\":\"APROBADO\"}";

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "/api/v1/historial", new HttpEntity<>(body, h), Map.class);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode(),
                "Un JWT de usuario (no CORE_SERVICE) debe recibir 403");
    }

    // PI-011: Audit no acepta PUT/DELETE/PATCH — append-only

    @Test
    void whenPutToHistorial_thenReturn405WithMessage() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/historial/tag/1",
                HttpMethod.PUT,
                new HttpEntity<>("{}", headersInterno()),
                Map.class);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode());
        String msg = (String) resp.getBody().get("message");
        assertNotNull(msg);
        assertTrue(msg.toLowerCase().contains("inmutable") || msg.toLowerCase().contains("no se permiten"),
                "El mensaje debe explicar la inmutabilidad, fue: " + msg);
    }

    @Test
    void whenDeleteToHistorial_thenReturn405WithMessage() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/historial/tag/1",
                HttpMethod.DELETE,
                new HttpEntity<>(headersInterno()),
                Map.class);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode());
        String msg = (String) resp.getBody().get("message");
        assertTrue(msg.toLowerCase().contains("inmutable") || msg.toLowerCase().contains("no se permiten"),
                "El mensaje debe explicar la inmutabilidad, fue: " + msg);
    }

    @Test
    void whenPatchToHistorial_thenReturn405() {
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/historial/tag/1",
                HttpMethod.PATCH,
                new HttpEntity<>("{}", headersInterno()),
                Map.class);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode());
    }

    // Conteo y validaciones adicionales

    @Test
    void givenMultipleEventsForTag_whenCountByTag_thenReturnCorrectTotal() {
        Integer idTag = 50;
        insertarEvento(idTag, 1, "APROBADO", "evento 1");
        insertarEvento(idTag, 1, "OBSERVADO", "evento 2");
        insertarEvento(idTag, 1, "APROBADO", "evento 3");

        String token = TestJwtHelper.tokenFor("admin@test.com", 1, List.of("ROLE_ADMIN"));
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/historial/tag/" + idTag + "/count",
                HttpMethod.GET,
                new HttpEntity<>(headersJwt(token)),
                Map.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(3, ((Number) resp.getBody().get("total")).intValue());
    }

    @Test
    void givenEventoCreado_whenGetByTag_thenFieldsArePresent() {
        Integer idTag = 60;
        insertarEvento(idTag, 1, "APROBADO", "Verificación OK");

        String token = TestJwtHelper.tokenFor("admin@test.com", 1, List.of("ROLE_ADMIN"));
        ResponseEntity<List> resp = restTemplate.exchange(
                "/api/v1/historial/tag/" + idTag,
                HttpMethod.GET,
                new HttpEntity<>(headersJwt(token)),
                List.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<?, ?> evento = (Map<?, ?>) resp.getBody().get(0);
        assertNotNull(evento.get("idHistorial"), "El evento debe tener idHistorial");
        assertNotNull(evento.get("fechaActualizado"), "El evento debe tener fechaActualizado");
        assertEquals("APROBADO", evento.get("estadoNuevo"));
        assertEquals("PENDIENTE", evento.get("estadoAnterior"));
    }
}
