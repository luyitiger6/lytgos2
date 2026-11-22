package io.lytgos.core.tool;

import io.lytgos.core.audit.ToolInvocationAuditor;
import io.lytgos.core.audit.ToolInvocationRecord;
import io.lytgos.core.llm.AgentToolCall;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 工具执行器：ReAct 循环中唯一的工具执行入口。
 *
 * <p>执行前保留 Sandbox 接入位，第 24 节接入白名单检查；每次尝试都写 tool_invocations 审计。
 *
 * @author LytgOS2 Team
 */
public final class ToolExecutor {

  /** 工具名称到工具实现的只读映射。 */
  private final Map<String, LytgTool> tools;

  /** 工具调用审计端口。 */
  private final ToolInvocationAuditor auditor;

  /**
   * 创建工具执行器。
   *
   * @param tools 工具名称到工具实现的映射
   * @param auditor 工具调用审计端口
   */
  public ToolExecutor(Map<String, LytgTool> tools, ToolInvocationAuditor auditor) {
    // 复制工具映射，避免外部修改影响执行。
    this.tools = tools == null ? Map.of() : Map.copyOf(tools);
    // 审计端口不能为空。
    this.auditor = Objects.requireNonNull(auditor, "auditor");
  }

  /**
   * 执行一次工具调用。
   *
   * @param sessionId 会话标识，可空
   * @param call 模型请求的工具调用
   * @return 工具执行结果；失败时返回失败结果，不向调用方抛工具异常
   */
  public ToolResult execute(String sessionId, AgentToolCall call) {
    Objects.requireNonNull(call, "call");
    // startedAtNanos 用于计算工具查找与执行的总耗时。
    long startedAtNanos = System.nanoTime();
    // tool 是本次要执行的工具实现；未注册时为 null。
    LytgTool tool = tools.get(call.name());
    if (tool == null) {
      ToolResult missingResult = ToolResult.error("工具不存在: " + call.name());
      recordAudit(sessionId, call, missingResult, startedAtNanos);
      return missingResult;
    }
    // Sandbox.enforce(...) 在 24 节接入；这里是工具执行前唯一的准入点。
    ToolResult result;
    try {
      result = tool.execute(call.arguments());
    } catch (RuntimeException exception) {
      // 工具异常不穿透到调用方；包装成失败结果并写审计。
      result = ToolResult.error("工具执行失败: " + exception.getMessage());
      recordAudit(sessionId, call, result, startedAtNanos);
      return result;
    }
    if (result == null) {
      // 工具返回 null 属于实现错误，按失败处理。
      result = ToolResult.error("工具返回空结果: " + call.name());
    }
    recordAudit(sessionId, call, result, startedAtNanos);
    return result;
  }

  /**
   * 写一条工具调用审计。
   *
   * @param sessionId 会话标识
   * @param call 工具调用
   * @param result 执行结果
   * @param startedAtNanos 开始时间
   */
  private void recordAudit(
      String sessionId, AgentToolCall call, ToolResult result, long startedAtNanos) {
    // durationMs 是工具查找与执行的总耗时。
    long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    // record 是本条审计记录；成功时错误原因为空。
    ToolInvocationRecord record =
        new ToolInvocationRecord(
            sessionId,
            call.name(),
            call.arguments(),
            result.success(),
            result.success() ? null : result.error(),
            durationMs);
    // 审计异常向上抛，不静默吞掉。
    auditor.record(record);
  }
}
