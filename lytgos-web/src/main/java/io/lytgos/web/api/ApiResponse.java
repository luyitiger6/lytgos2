package io.lytgos.web.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Unified response envelope for the LytgOS2 REST API.
 *
 * <p>Success responses use {@code code=0}. Error responses use the HTTP status as {@code code} and
 * set {@code data} to {@code null}.
 */
@Schema(description = "Unified API response envelope")
public record ApiResponse<T>(
    @Schema(description = "0 for success; HTTP status for errors") int code,
    @Schema(description = "Human-readable message") String message,
    @Schema(description = "Business payload; null on error") T data,
    @Schema(description = "Server time in ISO-8601 format") Instant timestamp) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(0, "OK", data, Instant.now());
  }

  public static <T> ApiResponse<T> error(int code, String message) {
    return new ApiResponse<>(code, message, null, Instant.now());
  }
}
