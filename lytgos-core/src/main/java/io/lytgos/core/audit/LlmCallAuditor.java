package io.lytgos.core.audit;

/**
 * LLM 调用审计端口。
 *
 * <p>Provider 只依赖这个 core 端口，不知道具体存储实现；storage 模块负责把记录写入 {@code llm_calls}。
 *
 * @author LytgOS2 Team
 */
@FunctionalInterface
public interface LlmCallAuditor {

  /**
   * 记录一次模型调用尝试。
   *
   * @param record 审计记录；成功和失败都必须记录
   */
  void record(LlmCallRecord record);

  /**
   * 与 {@code llm_calls} 表对应的一次模型调用记录。
   *
   * @param sessionId 会话标识，可空
   * @param profileName AgentProfile 名称
   * @param provider provider 名称
   * @param model 模型名称
   * @param promptTokens 输入 token 数，可空
   * @param completionTokens 输出 token 数，可空
   * @param totalTokens 总 token 数，可空
   * @param durationMs 调用耗时，毫秒
   * @param success 是否成功
   * @param errorMessage 失败原因，可空
   * @author LytgOS2 Team
   */
  record LlmCallRecord(
      String sessionId,
      String profileName,
      String provider,
      String model,
      Integer promptTokens,
      Integer completionTokens,
      Integer totalTokens,
      long durationMs,
      boolean success,
      String errorMessage) {}
}
