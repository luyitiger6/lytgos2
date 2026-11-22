package io.lytgos.core.llm;

import io.lytgos.core.tool.LytgTool;
import java.util.List;

/**
 * 一轮模型请求的 core 领域输入。
 *
 * @param systemPrompt 已拼接完成的 system 文本
 * @param messages 最近若干轮会话历史
 * @param tools 当前 AgentProfile 允许的工具
 * @author LytgOS2 Team
 */
public record AgentPrompt(String systemPrompt, List<AgentMessage> messages, List<LytgTool> tools) {

  public AgentPrompt {
    // system 文本缺失时使用空字符串，由调用方保证非空语义。
    systemPrompt = systemPrompt == null ? "" : systemPrompt;
    // 消息列表做防御性复制，保证请求构造后不被外部修改。
    messages = messages == null ? List.of() : List.copyOf(messages);
    // 工具列表做防御性复制，避免请求构造后工具集合变化。
    tools = tools == null ? List.of() : List.copyOf(tools);
  }
}
