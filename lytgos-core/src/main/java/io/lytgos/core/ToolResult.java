package io.lytgos.core;

/** Result of executing an {@link LytgTool}. */
public record ToolResult(boolean success, String content, String error, boolean retryable) {

  public static ToolResult ok(String content) {
    return new ToolResult(true, content, null, false);
  }

  public static ToolResult error(String error) {
    return new ToolResult(false, null, error, false);
  }
}
