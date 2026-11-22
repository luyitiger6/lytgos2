package io.lytgos.core.llm;

import java.util.List;
import java.util.Objects;

/**
 * 会话中的一条消息。
 *
 * @param role 消息角色
 * @param content 消息文本；工具消息为工具结果文本
 * @param toolName 工具消息的工具名，可空
 * @param toolCallId 工具消息关联的模型调用 ID，可空
 * @param toolCalls Agent 回复携带的工具调用
 * @author LytgOS2 Team
 */
public record AgentMessage(
    AgentRole role,
    String content,
    String toolName,
    String toolCallId,
    List<AgentToolCall> toolCalls) {

  public AgentMessage {
    // 角色决定消息语义，不能为空。
    role = Objects.requireNonNull(role, "role");
    // 文本缺失时使用空字符串，统一下游处理。
    content = content == null ? "" : content;
    // 工具调用列表做防御性复制，避免外部修改内部历史。
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
  }

  /**
   * 创建系统消息。
   *
   * @param content 系统指令
   * @return 系统消息
   */
  public static AgentMessage system(String content) {
    return new AgentMessage(AgentRole.SYSTEM, content, null, null, List.of());
  }

  /**
   * 创建用户消息。
   *
   * @param content 用户文本
   * @return 用户消息
   */
  public static AgentMessage user(String content) {
    return new AgentMessage(AgentRole.USER, content, null, null, List.of());
  }

  /**
   * 创建 Agent 回复消息。
   *
   * @param content 回复文本
   * @param toolCalls 回复携带的工具调用
   * @return Agent 回复消息
   */
  public static AgentMessage assistant(String content, List<AgentToolCall> toolCalls) {
    return new AgentMessage(AgentRole.ASSISTANT, content, null, null, toolCalls);
  }

  /**
   * 创建工具结果消息。
   *
   * @param toolName 工具名称
   * @param toolCallId 关联的模型调用 ID
   * @param content 工具结果文本
   * @return 工具结果消息
   */
  public static AgentMessage tool(String toolName, String toolCallId, String content) {
    return new AgentMessage(AgentRole.TOOL, content, toolName, toolCallId, List.of());
  }
}
