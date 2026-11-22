package io.lytgos.core.llm;

/**
 * 会话消息的角色。
 *
 * @author LytgOS2 Team
 */
public enum AgentRole {

  /** 系统指令。 */
  SYSTEM,

  /** 用户消息。 */
  USER,

  /** Agent 回复。 */
  ASSISTANT,

  /** 工具执行结果。 */
  TOOL
}
