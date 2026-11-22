package io.lytgos.core.audit;

/**
 * 工具调用审计端口。
 *
 * <p>ToolExecutor 只依赖这个 core 端口，不知道具体存储实现；storage 模块负责写入 {@code tool_invocations}。
 *
 * @author LytgOS2 Team
 */
@FunctionalInterface
public interface ToolInvocationAuditor {

  /**
   * 记录一次工具调用尝试。
   *
   * @param record 审计记录；成功和失败都必须记录
   */
  void record(ToolInvocationRecord record);
}
