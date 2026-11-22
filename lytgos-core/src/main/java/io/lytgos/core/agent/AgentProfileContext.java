package io.lytgos.core.agent;

/**
 * 当前请求的 AgentProfile 上下文。
 *
 * <p>工具执行时通过它读取当前 Agent 配置；虚拟线程下每个请求独立，处理结束必须清理。
 *
 * @author LytgOS2 Team
 */
public final class AgentProfileContext {

  /** 当前线程的 AgentProfile。 */
  private static final ThreadLocal<AgentProfile> PROFILE_HOLDER = new ThreadLocal<>();

  /** 工具类不允许实例化。 */
  private AgentProfileContext() {}

  /**
   * 设置当前 AgentProfile。
   *
   * @param agentProfile 当前请求的 AgentProfile
   */
  public static void set(AgentProfile agentProfile) {
    PROFILE_HOLDER.set(agentProfile);
  }

  /**
   * 读取当前 AgentProfile。
   *
   * @return 当前 AgentProfile；未设置时返回 null
   */
  public static AgentProfile current() {
    return PROFILE_HOLDER.get();
  }

  /** 清理当前 AgentProfile。 */
  public static void clear() {
    PROFILE_HOLDER.remove();
  }
}
