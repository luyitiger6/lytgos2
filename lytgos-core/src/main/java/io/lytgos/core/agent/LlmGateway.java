package io.lytgos.core.agent;

import io.lytgos.core.llm.AgentPrompt;
import io.lytgos.core.llm.AgentResponse;

/**
 * Agent 引擎调用模型的端口。
 *
 * <p>ReActLoop 只依赖这个 core 端口；具体 Provider 适配器负责与模型 SDK 协议转换。
 *
 * @author LytgOS2 Team
 */
@FunctionalInterface
public interface LlmGateway {

  /**
   * 发起一次模型调用。
   *
   * @param sessionId 会话标识，可空
   * @param agentProfile 本次调用的 AgentProfile
   * @param agentPrompt 已组装的 core 请求
   * @return 模型响应的 core 表示
   */
  AgentResponse chat(String sessionId, AgentProfile agentProfile, AgentPrompt agentPrompt);
}
