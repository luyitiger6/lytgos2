package io.lytgos.core.agent;

import java.util.List;

/**
 * Agent 的运行时配置。
 *
 * <p>本节由 {@code AgentProfileLoader} 从 {@code .lytgos/profiles/*.yaml} 加载；后续 Agent 课程再迁移到 {@code
 * AGENT.md} frontmatter。列表字段在构造时做 null 转换和防御性复制，避免调用方修改内部集合。
 *
 * @param name AgentProfile 名称，内存索引的唯一键
 * @param description 面向人的描述
 * @param identity Agent 身份信息
 * @param provider 本次使用的 provider、model 和 temperature
 * @param tools 可用 Tool 名称
 * @param skills 课件兼容字段：Skill 名称
 * @param mcpServers MCP server 名称
 * @param channels 可用渠道
 * @param notifyChannels 课件兼容字段：通知渠道名称；本节不消费
 * @param schedules 定时任务定义
 * @param bootstrap Bootstrap 文件列表
 * @param settings 运行时设置
 * @author LytgOS2 Team
 */
public record AgentProfile(
    String name,
    String description,
    Identity identity,
    ProviderRef provider,
    List<String> tools,
    List<String> skills,
    List<String> mcpServers,
    List<String> channels,
    List<String> notifyChannels,
    List<ScheduleSpec> schedules,
    List<String> bootstrap,
    Settings settings) {

  /** 采样温度缺省值。 */
  private static final double DEFAULT_TEMPERATURE = 0.7D;

  /** ReAct 最大轮数缺省值。 */
  private static final int DEFAULT_MAX_ITERATIONS = 10;

  /** 历史轮数上限缺省值。 */
  private static final int DEFAULT_MAX_HISTORY_TURNS = 20;

  public AgentProfile {
    // 列表字段统一做 null 转换和不可变复制，保证 record 内部状态稳定。
    tools = copyOf(tools);
    skills = copyOf(skills);
    mcpServers = copyOf(mcpServers);
    channels = copyOf(channels);
    notifyChannels = copyOf(notifyChannels);
    schedules = schedules == null ? List.of() : List.copyOf(schedules);
    bootstrap = copyOf(bootstrap);
    // 缺少身份信息时使用空身份，避免调用方处理 null。
    if (identity == null) {
      identity = new Identity(null, null);
    }
    // provider 缺失时保留空引用，由 AgentProfileLoader 负责校验并报错。
    if (provider == null) {
      provider = new ProviderRef(null, null, DEFAULT_TEMPERATURE);
    } else if (provider.temperature() == null) {
      // 温度缺省为 0.7，与课件示例保持一致。
      provider = new ProviderRef(provider.name(), provider.model(), DEFAULT_TEMPERATURE);
    }
    // settings 缺失时使用课件默认值。
    if (settings == null) {
      settings = new Settings(DEFAULT_MAX_ITERATIONS, DEFAULT_MAX_HISTORY_TURNS);
    } else {
      // maxIterations 是最终使用的最大轮数，缺失时取默认值。
      Integer maxIterations =
          settings.maxIterations() == null
              ? Integer.valueOf(DEFAULT_MAX_ITERATIONS)
              : settings.maxIterations();
      // maxHistoryTurns 是最终使用的历史轮数，缺失时取默认值。
      Integer maxHistoryTurns =
          settings.maxHistoryTurns() == null
              ? Integer.valueOf(DEFAULT_MAX_HISTORY_TURNS)
              : settings.maxHistoryTurns();
      settings = new Settings(maxIterations, maxHistoryTurns);
    }
  }

  /**
   * 复制字符串列表；null 转为空列表。
   *
   * @param values 原始列表
   * @return 不可变列表
   */
  private static List<String> copyOf(List<String> values) {
    return values == null ? List.of() : List.copyOf(values);
  }

  /**
   * Agent 身份信息。
   *
   * @param agentName Agent 展示名称
   * @param prompt Agent 身份提示
   * @author LytgOS2 Team
   */
  public record Identity(String agentName, String prompt) {}

  /**
   * AgentProfile 对 provider 的选择。
   *
   * @param name provider 名称，必须存在于全局 provider 配置
   * @param model 本次调用的模型名称
   * @param temperature 采样温度
   * @author LytgOS2 Team
   */
  public record ProviderRef(String name, String model, Double temperature) {}

  /**
   * 定时任务定义。
   *
   * @param cron cron 表达式
   * @param zone 时区
   * @param message 触发时发送给 Agent 的消息
   * @author LytgOS2 Team
   */
  public record ScheduleSpec(String cron, String zone, String message) {}

  /**
   * 运行时设置。
   *
   * @param maxIterations ReAct 最大轮数
   * @param maxHistoryTurns 历史轮数上限
   * @author LytgOS2 Team
   */
  public record Settings(Integer maxIterations, Integer maxHistoryTurns) {}
}
