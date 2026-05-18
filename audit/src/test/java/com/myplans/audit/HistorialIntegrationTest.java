package com.myplans.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HistorialIntegrationTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void setUpHttpClient() {
        restTemplate.getRestTemplate()
                .setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    private HttpHeaders headersWithInternalToken() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Internal-Token", INTERNAL_TOKEN);
        return h;
    }

    private HttpHeaders headersWithJwt(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return h;
    }

    private String supervisorToken() {
        return TestJwtHelper.tokenFor("sup@test.com", 2, List.of("ROLE_SUPERVISOR"));
    }

    private String operadorToken() {
        return TestJwtHelper.tokenFor("op@test.com", 3, List.of("ROLE_OPERADOR"));
    }

    @Test
    void givenNoAuth_whenPost_thenReturn401() {
        String body = "{\"idTag\":1,\"idUsuario\":1,\"estadoNuevo\":\"APROBADO\"}";
        HttpEntity<String> req = new HttpEntity<>(body, new HttpHeaders() {
            {
                setContentType(MediaType.APPLICATION_JSON);
            }
        });
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/historial", req, Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertNotNull(resp.getBody().get("message"));
    }

    @Test
    void givenNoAuth_whenGet_thenReturn401() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/api/v1/historial/tag/1", Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void givenSupervisorJwt_whenPost_thenReturn403() {
        String body = "{\"idTag\":1,\"idUsuario\":1,\"estadoNuevo\":\"APROBADO\"}";
        HttpEntity<String> req = new HttpEntity<>(body, headersWithJwt(supervisorToken()));
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/historial", req, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertNotNull(resp.getBody().get("message"));
    }

    @Test
    void givenOperadorJwt_whenGet_thenReturn403() {
        HttpEntity<Void> req = new HttpEntity<>(headersWithJwt(operadorToken()));
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/historial/tag/1", HttpMethod.GET, req, Map.class);
        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void givenInternalToken_whenPost_thenReturn201() {
        String body = """
                {
                  "idTag": 42,
                  "idUsuario": 7,
                  "estadoAnterior": "PENDIENTE",
                  "estadoNuevo": "APROBADO",
                  "observaciones": "Validado en terreno"
                }
                """;
        HttpEntity<String> req = new HttpEntity<>(body, headersWithInternalToken());
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/historial", req, Map.class);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(42, resp.getBody().get("idTag"));
        assertEquals(7, resp.getBody().get("idUsuario"));
        assertEquals("APROBADO", resp.getBody().get("estadoNuevo"));
        assertNotNull(resp.getBody().get("idHistorial"));
        assertNotNull(resp.getBody().get("fechaActualizado"));
    }

    @Test
    void whenPostThenGetByTag_thenReturnAllEvents() {
        for (int i = 0; i < 3; i++) {
            String body = String.format("""
                    {"idTag":100,"idUsuario":1,"estadoAnterior":"PENDIENTE",
                     "estadoNuevo":"APROBADO","observaciones":"evento %d"}
                    """, i);
            HttpEntity<String> req = new HttpEntity<>(body, headersWithInternalToken());
            ResponseEntity<Map> r = restTemplate.postForEntity("/api/v1/historial", req, Map.class);
            assertEquals(HttpStatus.CREATED, r.getStatusCode());
        }

        HttpEntity<Void> req = new HttpEntity<>(headersWithJwt(supervisorToken()));
        ResponseEntity<List> resp = restTemplate.exchange(
                "/api/v1/historial/tag/100", HttpMethod.GET, req, List.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(3, resp.getBody().size());
    }

    @Test
    void whenCountByTag_thenReturnCorrectCount() {
        for (int i = 0; i < 2; i++) {
            String body = """
                    {"idTag":200,"idUsuario":1,"estadoNuevo":"APROBADO"}
                    """;
            HttpEntity<String> req = new HttpEntity<>(body, headersWithInternalToken());
            restTemplate.postForEntity("/api/v1/historial", req, Map.class);
        }

        HttpEntity<Void> req = new HttpEntity<>(headersWithJwt(supervisorToken()));
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/historial/tag/200/count", HttpMethod.GET, req, Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(2, ((Number) resp.getBody().get("total")).intValue());
    }

    @Test
    void whenPut_thenReturn405WithImmutableMessage() {
        HttpEntity<String> req = new HttpEntity<>("{}", headersWithInternalToken());
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/historial/tag/1", HttpMethod.PUT, req, Map.class);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode());
        String msg = (String) resp.getBody().get("message");
        assertNotNull(msg);
        assertTrue(msg.toLowerCase().contains("inmutable") || msg.toLowerCase().contains("no se permiten"),
                "El mensaje debe explicar la inmutabilidad: " + msg);
    }

    @Test
    void whenDelete_thenReturn405WithImmutableMessage() {
        HttpEntity<Void> req = new HttpEntity<>(headersWithInternalToken());
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/historial/tag/1", HttpMethod.DELETE, req, Map.class);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode());
        String msg = (String) resp.getBody().get("message");
        assertTrue(msg.toLowerCase().contains("inmutable") || msg.toLowerCase().contains("no se permiten"));
    }

    @Test
    void whenPatch_thenReturn405WithImmutableMessage() {
        HttpEntity<String> req = new HttpEntity<>("{}", headersWithInternalToken());
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/historial/tag/1", HttpMethod.PATCH, req, Map.class);
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, resp.getStatusCode());
    }

    @Test
    void givenMissingEstadoNuevo_whenPost_thenReturn400() {
        String body = "{\"idTag\":1,\"idUsuario\":1}";
        HttpEntity<String> req = new HttpEntity<>(body, headersWithInternalToken());
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/historial", req, Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        String msg = (String) resp.getBody().get("message");
        assertTrue(msg.toLowerCase().contains("obligatorio"));
    }

    @Test
    void givenEmptyBody_whenPost_thenReturn400() {
        HttpEntity<String> req = new HttpEntity<>("", headersWithInternalToken());
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/historial", req, Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }
}