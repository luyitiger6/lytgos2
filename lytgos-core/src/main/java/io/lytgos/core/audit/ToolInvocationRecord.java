package io.lytgos.core.audit;

import java.util.Objects;

/**
 * 一次工具调用尝试的审计记录。
 *
 * @param sessionId 会话标识，可空
 * @param toolName 工具名称
 * @param inputJson 工具输入 JSON，可空
 * @param success 是否成功
 * @param errorMessage 失败原因，可空
 * @param durationMs 执行耗时，毫秒
 * @author LytgOS2 Team
 */
public record ToolInvocationRecord(
    String sessionId,
    String toolName,
    String inputJson,
    boolean success,
    String errorMessage,
    long durationMs) {

  public ToolInvocationRecord {
    // 工具名是审计的必填维度，不能为空。
    toolName = Objects.requireNonNull(toolName, "toolName");
    // 耗时不允许为负数。
    durationMs = Math.max(durationMs, 0L);
  }
}
