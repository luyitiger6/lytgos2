package io.lytgos.web;

import io.lytgos.core.Lytgos;
import io.lytgos.web.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * System endpoints defined by the 1.0 REST contract.
 *
 * @author LytgOS2 Team
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "System", description = "Health and runtime information")
public class SystemApiController {

  @GetMapping("/health")
  @Operation(summary = "Health check")
  public ApiResponse<HealthResponse> health() {
    return ApiResponse.ok(new HealthResponse("UP", Lytgos.NAME, Lytgos.VERSION, Instant.now()));
  }

  @GetMapping("/info")
  @Operation(summary = "Runtime information")
  public ApiResponse<InfoResponse> info() {
    return ApiResponse.ok(
        new InfoResponse(Lytgos.NAME, Lytgos.VERSION, 9, "serve", List.of(), 0, 0));
  }

  public record HealthResponse(String status, String name, String version, Instant timestamp) {}

  public record InfoResponse(
      String name,
      String version,
      int modules,
      String runtimeMode,
      List<String> providers,
      int profiles,
      int tools) {

    public InfoResponse {
      providers = providers == null ? List.of() : List.copyOf(providers);
    }
  }
}
