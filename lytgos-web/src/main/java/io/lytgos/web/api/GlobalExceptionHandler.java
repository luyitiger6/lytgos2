package io.lytgos.web.api;

import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps uncaught exceptions to the unified API envelope without leaking internals. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining("; "));
    return response(ErrorCode.BAD_REQUEST, message);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(
      HttpMessageNotReadableException ex) {
    return response(ErrorCode.BAD_REQUEST, "请求体不是合法的 JSON");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingResource(NoResourceFoundException ex) {
    return response(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage());
  }

  @ExceptionHandler(TimeoutException.class)
  public ResponseEntity<ApiResponse<Void>> handleTimeout(TimeoutException ex) {
    return response(ErrorCode.GATEWAY_TIMEOUT, ErrorCode.GATEWAY_TIMEOUT.defaultMessage());
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
    int status = ex.getStatusCode().value();
    String message = ex.getReason() == null ? "请求失败" : ex.getReason();
    return ResponseEntity.status(status).body(ApiResponse.error(status, message));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
    LOG.error("Unhandled request error", ex);
    return response(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
  }

  private ResponseEntity<ApiResponse<Void>> response(ErrorCode errorCode, String message) {
    return ResponseEntity.status(errorCode.httpStatus())
        .body(ApiResponse.error(errorCode.httpStatus(), message));
  }
}
