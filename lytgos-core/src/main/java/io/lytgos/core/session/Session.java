package io.lytgos.core.session;

import io.lytgos.core.llm.AgentMessage;
import io.lytgos.core.llm.AgentResponse;
import io.lytgos.core.llm.AgentRole;
import io.lytgos.core.llm.AgentToolCall;
import io.lytgos.core.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 一次对话的上下文。
 *
 * <p>本节只维护内存消息历史；第 18 节再由 SessionManager 持久化到 SQLite。
 *
 * @author LytgOS2 Team
 */
public final class Session {

  /** 会话标识。 */
  private final String sessionIdentifier;

  /** 所属 AgentProfile 名称。 */
  private final String agentProfileName;

  /** 接入渠道。 */
  private final String channelName;

  /** 用户标识。 */
  private final String userIdentifier;

  /** 按时间顺序累积的对话消息。 */
  private final List<AgentMessage> history = new ArrayList<>();

  /**
   * 创建会话。
   *
   * @param sessionId 会话标识
   * @param profileName 所属 AgentProfile 名称
   * @param channel 接入渠道
   * @param userId 用户标识
   */
  public Session(String sessionId, String profileName, String channel, String userId) {
    // 会话标识与 AgentProfile 名称是后续处理的关键键。
    this.sessionIdentifier = Objects.requireNonNull(sessionId, "sessionId");
    this.agentProfileName = Objects.requireNonNull(profileName, "profileName");
    // 渠道与用户标识缺失时使用空字符串，避免调用方处理 null。
    this.channelName = channel == null ? "" : channel;
    this.userIdentifier = userId == null ? "" : userId;
  }

  /**
   * 追加一条原始消息。
   *
   * @param message 待追加消息
   */
  public void append(AgentMessage message) {
    // 消息本身不能为空。
    history.add(Objects.requireNonNull(message, "message"));
  }

  /**
   * 追加用户消息。
   *
   * @param content 用户文本
   */
  public void appendUser(String content) {
    history.add(AgentMessage.user(content));
  }

  /**
   * 追加 Agent 回复。
   *
   * @param response 模型响应
   */
  public void appendAssistant(AgentResponse response) {
    // 响应不能为空，工具调用列表由 AgentResponse 自己保证。
    Objects.requireNonNull(response, "response");
    history.add(AgentMessage.assistant(response.text(), response.toolCalls()));
  }

  /**
   * 追加工具执行结果。
   *
   * @param call 对应的工具调用
   * @param result 工具执行结果
   */
  public void appendToolResult(AgentToolCall call, ToolResult result) {
    // 工具调用与结果都必须存在，才能形成可审计的完整链路。
    Objects.requireNonNull(call, "call");
    Objects.requireNonNull(result, "result");
    // content 是回填给模型的工具结果文本；失败时优先使用错误原因。
    String content =
        result.success() ? result.content() : (result.error() == null ? "工具执行失败" : result.error());
    history.add(AgentMessage.tool(call.name(), call.id(), content));
  }

  /**
   * 返回消息历史快照。
   *
   * @return 不可变消息列表
   */
  public List<AgentMessage> messages() {
    return List.copyOf(history);
  }

  /**
   * 返回最近 N 个完整轮次。
   *
   * <p>每轮从一条用户消息开始，包含其后的 Agent 回复与工具结果；不会拆散同一轮的工具调用链。
   *
   * @param maxTurns 最大轮数
   * @return 最近 N 轮消息；maxTurns 非正数时返回空列表
   */
  public List<AgentMessage> recentMessages(int maxTurns) {
    if (maxTurns <= 0) {
      return List.of();
    }
    // snapshot 是当前历史的不可变快照。
    List<AgentMessage> snapshot = List.copyOf(history);
    // userTurns 记录从后向前遇到的用户消息数量。
    int userTurns = 0;
    // startIndex 是截断后的起始下标。
    int startIndex = 0;
    for (int index = snapshot.size() - 1; index >= 0; index--) {
      if (snapshot.get(index).role() == AgentRole.USER) {
        userTurns++;
        if (userTurns == maxTurns) {
          startIndex = index;
          break;
        }
      }
    }
    return List.copyOf(snapshot.subList(startIndex, snapshot.size()));
  }

  /**
   * 返回消息数量。
   *
   * @return 消息数量
   */
  public int messageCount() {
    return history.size();
  }

  /**
   * 返回会话标识。
   *
   * @return 会话标识
   */
  public String sessionId() {
    return sessionIdentifier;
  }

  /**
   * 返回所属 AgentProfile 名称。
   *
   * @return AgentProfile 名称
   */
  public String profileName() {
    return agentProfileName;
  }

  /**
   * 返回接入渠道。
   *
   * @return 接入渠道
   */
  public String channel() {
    return channelName;
  }

  /**
   * 返回用户标识。
   *
   * @return 用户标识
   */
  public String userId() {
    return userIdentifier;
  }
}
