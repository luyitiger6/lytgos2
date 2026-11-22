package io.lytgos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SystemApiControllerTest {

  @LocalServerPort private int port;

  private final HttpClient client = HttpClient.newHttpClient();

  @Test
  void healthReturnsUnifiedEnvelope() throws Exception {
    HttpResponse<String> response = get("/api/v1/health");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("\"code\":0")
        .contains("\"message\":\"OK\"")
        .contains("\"status\":\"UP\"")
        .contains("\"name\":\"LytgOS2\"");
  }

  @Test
  void infoReturnsRuntimeMetadata() throws Exception {
    HttpResponse<String> response = get("/api/v1/info");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("\"code\":0")
        .contains("\"version\":\"1.0.0\"")
        .contains("\"runtimeMode\":\"serve\"");
  }

  @Test
  void openApiDocumentIsAvailable() throws Exception {
    HttpResponse<String> response = get("/v3/api-docs");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"openapi\"").contains("/api/v1/health");
  }

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
