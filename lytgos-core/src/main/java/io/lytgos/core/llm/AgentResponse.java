package io.lytgos.core.llm;

import java.util.List;

/**
 * 模型一轮响应的 core 领域表示。
 *
 * @param text 模型文本
 * @param toolCalls 模型请求的工具调用
 * @author LytgOS2 Team
 */
public record AgentResponse(String text, List<AgentToolCall> toolCalls) {

  public AgentResponse {
    // 文本缺失时使用空字符串，兼容只有工具调用的响应。
    text = text == null ? "" : text;
    // 工具调用列表做防御性复制，保证响应构造后不被外部修改。
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
  }

  /**
   * 是否包含工具调用。
   *
   * @return 包含工具调用时返回 true
   */
  public boolean hasToolCalls() {
    return !toolCalls.isEmpty();
  }
}
