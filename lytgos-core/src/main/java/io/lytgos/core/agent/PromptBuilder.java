package io.lytgos.core.agent;

import io.lytgos.core.llm.AgentMessage;
import io.lytgos.core.llm.AgentPrompt;
import io.lytgos.core.session.Session;
import io.lytgos.core.tool.LytgTool;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 组装一轮模型请求的 system prompt、历史消息与可用工具。
 *
 * <p>每次 build 都重新读取 Bootstrap/Skill，不缓存；system prompt 末尾附当前日期时间。
 *
 * @author LytgOS2 Team
 */
public final class PromptBuilder {

  /** 历史轮数缺省值。 */
  private static final int DEFAULT_MAX_HISTORY_TURNS = 20;

  /** 日期时间格式，使用带偏移的 ISO-8601 形式。 */
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

  /** 多语言对话策略；不把模型限制在中文或英文。 */
  private static final String LANGUAGE_POLICY =
      "Language policy: always answer in the primary language of the user's most recent message. "
          + "If the user asks to switch languages, switch immediately. "
          + "For mixed-language input, use the dominant language. "
          + "Keep code, commands, file paths, URLs, and identifiers unchanged. "
          + "Do not default to Chinese or English when another language is clearly used.";

  /** 工具名称到工具实现的只读映射。 */
  private final Map<String, LytgTool> tools;

  /** Bootstrap/Skill 上下文加载器。 */
  private final ContextLoader contextLoader;

  /** 可注入时钟，测试可固定当前时间。 */
  private final Clock clock;

  /**
   * 创建 PromptBuilder。
   *
   * @param tools 工具名称到工具实现的映射
   * @param contextLoader 上下文加载器
   * @param clock 可注入时钟；为空时使用系统默认时区
   */
  public PromptBuilder(Map<String, LytgTool> tools, ContextLoader contextLoader, Clock clock) {
    // 复制工具映射，避免外部修改影响 prompt 内容。
    this.tools = tools == null ? Map.of() : Map.copyOf(tools);
    // 上下文加载器与时钟不能为空。
    this.contextLoader = Objects.requireNonNull(contextLoader, "contextLoader");
    this.clock = clock == null ? Clock.systemDefaultZone() : clock;
  }

  /**
   * 组装一轮请求。
   *
   * @param session 当前会话
   * @param agentProfile 当前 AgentProfile
   * @return core 请求对象
   */
  public AgentPrompt build(Session session, AgentProfile agentProfile) {
    Objects.requireNonNull(session, "session");
    Objects.requireNonNull(agentProfile, "agentProfile");
    // systemPrompt 是已按顺序拼接完成的 system 文本。
    String systemPrompt = buildSystemPrompt(agentProfile);
    // history 是最近 N 个完整轮次的消息。
    List<AgentMessage> history = session.recentMessages(effectiveHistoryTurns(agentProfile));
    // availableTools 是当前 AgentProfile 允许且已注册的工具。
    List<LytgTool> availableTools = availableTools(agentProfile);
    return new AgentPrompt(systemPrompt, history, availableTools);
  }

  /**
   * 拼接 system prompt。
   *
   * @param agentProfile 当前 AgentProfile
   * @return 按身份、上下文、当前时间顺序拼接的文本
   */
  private String buildSystemPrompt(AgentProfile agentProfile) {
    // builder 保存 system prompt 内容。
    StringBuilder builder = new StringBuilder();
    // identityPrompt 是 Agent 身份指令。
    String identityPrompt =
        agentProfile.identity() == null ? null : agentProfile.identity().prompt();
    appendSection(builder, identityPrompt);
    // 语言策略放在身份之后，确保模型按用户当前语言回复。
    appendSection(builder, LANGUAGE_POLICY);
    // context 是 Bootstrap 与 Skill 元数据。
    String context = contextLoader.buildContext(agentProfile);
    appendSection(builder, context);
    // currentTime 是注入时钟的当前时间，放在 system prompt 末尾。
    String currentTime = TIME_FORMATTER.format(ZonedDateTime.now(clock));
    appendSection(builder, "当前时间: " + currentTime);
    return builder.toString();
  }

  /**
   * 追加一个非空段落。
   *
   * @param builder 目标构建器
   * @param section 段落文本
   */
  private void appendSection(StringBuilder builder, String section) {
    if (section == null || section.isBlank()) {
      return;
    }
    if (builder.length() > 0) {
      builder.append("\n\n");
    }
    builder.append(section);
  }

  /**
   * 计算有效历史轮数。
   *
   * @param agentProfile 当前 AgentProfile
   * @return 历史轮数；缺省或非正数时返回 20
   */
  private int effectiveHistoryTurns(AgentProfile agentProfile) {
    // configuredTurns 是 AgentProfile 中配置的历史轮数。
    Integer configuredTurns =
        agentProfile.settings() == null ? null : agentProfile.settings().maxHistoryTurns();
    return configuredTurns == null || configuredTurns <= 0
        ? DEFAULT_MAX_HISTORY_TURNS
        : configuredTurns;
  }

  /**
   * 按 AgentProfile 的工具名称过滤已注册工具。
   *
   * @param agentProfile 当前 AgentProfile
   * @return 可用工具列表
   */
  private List<LytgTool> availableTools(AgentProfile agentProfile) {
    // result 保存按配置顺序过滤后的工具。
    List<LytgTool> result = new ArrayList<>();
    for (String toolName : agentProfile.tools()) {
      // tool 是已注册的工具；未注册时跳过。
      LytgTool tool = tools.get(toolName);
      if (tool != null) {
        result.add(tool);
      }
    }
    return List.copyOf(result);
  }
}
