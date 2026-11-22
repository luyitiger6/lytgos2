package io.lytgos.web.api;

/** HTTP-aligned error codes used by the 1.0 REST API contract. */
public enum ErrorCode {
  BAD_REQUEST(400, "请求参数不合法"),
  NOT_FOUND(404, "资源不存在"),
  CONFLICT(409, "资源状态冲突"),
  INTERNAL_ERROR(500, "内部错误"),
  SERVICE_UNAVAILABLE(503, "服务暂不可用"),
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
