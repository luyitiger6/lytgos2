package io.lytgos.core.agent;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AgentProfile 的内存索引。
 *
 * <p>本节只提供启动扫描后的注册路径；后续 Agent 课程可以在这里增加运行时注册/移除。
 *
 * @author LytgOS2 Team
 */
public final class AgentProfileRegistry {

  /** 日志记录器。 */
  private static final Logger LOG = LoggerFactory.getLogger(AgentProfileRegistry.class);

  /** 按 AgentProfile 名称索引的并发 Map。 */
  private final Map<String, AgentProfile> profiles = new ConcurrentHashMap<>();

  /**
   * 注册一个 AgentProfile；重复名称保留首个并记录错误。
   *
   * @param agentProfile 待注册的 AgentProfile
   */
  public void register(AgentProfile agentProfile) {
    // 参数校验放在写入索引之前，避免空名称污染索引。
    Objects.requireNonNull(agentProfile, "agentProfile");
    Objects.requireNonNull(agentProfile.name(), "agentProfile.name");
    // previous 非空说明该名称已经注册过。
    AgentProfile previous = profiles.putIfAbsent(agentProfile.name(), agentProfile);
    if (previous != null) {
      // 保留首个 AgentProfile，避免重复文件静默覆盖已加载配置。
      if (LOG.isErrorEnabled()) {
        LOG.error("AgentProfile 名称重复，保留首个并忽略后续项: {}", agentProfile.name());
      }
    }
  }

  /**
   * 按名称查找 AgentProfile。
   *
   * @param name AgentProfile 名称
   * @return 找到的 AgentProfile，不存在时为空
   */
  public Optional<AgentProfile> find(String name) {
    return Optional.ofNullable(profiles.get(name));
  }

  /**
   * 返回当前索引大小。
   *
   * @return AgentProfile 数量
   */
  public int size() {
    return profiles.size();
  }
}
