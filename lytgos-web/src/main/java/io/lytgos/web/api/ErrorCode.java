package io.lytgos.web.api;

/**
 * HTTP-aligned error codes used by the 1.0 REST API contract.
 *
 * @author LytgOS2 Team
 */
public enum ErrorCode {
  /** 400 Bad Request. */
  BAD_REQUEST(400, "请求参数不合法"),
  /** 404 Not Found. */
  NOT_FOUND(404, "资源不存在"),
  /** 409 Conflict. */
  CONFLICT(409, "资源状态冲突"),
  /** 500 Internal Server Error. */
  INTERNAL_ERROR(500, "内部错误"),
  /** 503 Service Unavailable. */
  SERVICE_UNAVAILABLE(503, "服务暂不可用"),
  /** 504 Gateway Timeout. */
  GATEWAY_TIMEOUT(504, "请求超时");

  private final int status;
  private final String message;

  ErrorCode(int status, String message) {
    this.status = status;
    this.message = message;
  }

  public int httpStatus() {
    return status;
  }

  public String defaultMessage() {
    return message;
  }
}
