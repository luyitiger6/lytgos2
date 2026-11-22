package io.lytgos.core.agent;

import io.lytgos.core.session.Session;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 三种触发源共用的 Agent 处理入口。
 *
 * <p>负责查找 AgentProfile、设置请求级上下文、调用 ReAct 循环、保存会话，并在 finally 清理上下文。
 *
 * @author LytgOS2 Team
 */
public final class AgentService {

  /** AgentProfile 内存索引。 */
  private final AgentProfileRegistry registry;

  /** ReAct 循环。 */
  private final ReActLoop reActLoop;

  /** 会话保存钩子；第 18 节替换为 SessionManager。 */
  private final Consumer<Session> sessionSaver;

  /**
   * 创建 AgentService。
   *
   * @param registry AgentProfile 内存索引
   * @param reActLoop ReAct 循环
   * @param sessionSaver 会话保存钩子
   */
  public AgentService(
      AgentProfileRegistry registry, ReActLoop reActLoop, Consumer<Session> sessionSaver) {
    // 三个协作者都不能为空。
    this.registry = Objects.requireNonNull(registry, "registry");
    this.reActLoop = Objects.requireNonNull(reActLoop, "reActLoop");
    this.sessionSaver = Objects.requireNonNull(sessionSaver, "sessionSaver");
  }

  /**
   * 处理一条用户消息。
   *
   * @param session 当前会话
   * @param userMessage 用户消息
   * @return Agent 最终答复
   */
  public String process(Session session, String userMessage) {
    Objects.requireNonNull(session, "session");
    // agentProfile 是本次请求使用的 Agent 配置。
    AgentProfile agentProfile =
        registry
            .find(session.profileName())
            .orElseThrow(
                () -> new IllegalArgumentException("AgentProfile 不存在: " + session.profileName()));
    // 请求期间工具通过上下文读取当前 AgentProfile。
    AgentProfileContext.set(agentProfile);
    try {
      // reply 是 ReAct 循环的最终答复。
      String reply = reActLoop.run(session, userMessage, agentProfile);
      // 保存钩子失败时异常向上抛，不静默丢会话。
      sessionSaver.accept(session);
      return reply;
    } finally {
      // 无论成功或异常都必须清理请求级上下文。
      AgentProfileContext.clear();
    }
  }
}
