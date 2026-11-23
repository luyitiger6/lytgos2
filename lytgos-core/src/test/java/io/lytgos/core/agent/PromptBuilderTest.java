package io.lytgos.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.lytgos.core.llm.AgentMessage;
import io.lytgos.core.llm.AgentPrompt;
import io.lytgos.core.llm.AgentResponse;
import io.lytgos.core.session.Session;
import io.lytgos.core.tool.LytgTool;
import io.lytgos.core.tool.ToolResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PromptBuilder 的顺序、历史截断、日期注入与工具过滤测试。
 *
 * @author LytgOS2 Team
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class PromptBuilderTest {

  @Test
  @DisplayName("system prompt 按身份、语言策略、Bootstrap、当前时间顺序拼接")
  void systemPromptUsesExpectedOrderAndDate() {
    // contextLoader 返回固定的 Bootstrap 文本。
    ContextLoader contextLoader = mock(ContextLoader.class);
    when(contextLoader.buildContext(any(AgentProfile.class))).thenReturn("## Bootstrap: SOUL.md");
    // echo 是测试工具。
    LytgTool echo = new NamedTool("echo");
    // http 是另一个测试工具，用于验证过滤。
    LytgTool http = new NamedTool("http_get");
    // clock 固定当前时间，保证断言稳定。
    Clock clock = Clock.fixed(Instant.parse("2026-11-23T04:00:00Z"), ZoneOffset.UTC);
    // builder 是被测对象。
    PromptBuilder builder =
        new PromptBuilder(Map.of("echo", echo, "http_get", http), contextLoader, clock);
    // session 是空会话。
    Session session = new Session("session-1", "test-agent", "cli", "user-1");
    // agentProfile 是测试配置，只允许 echo 工具。
    AgentProfile agentProfile = agentProfile(20, List.of("echo"));

    // prompt 是组装结果。
    AgentPrompt prompt = builder.build(session, agentProfile);

    // system 是最终 system prompt。
    String system = prompt.systemPrompt();
    assertThat(system).startsWith("你是测试助手");
    assertThat(system).contains("Language policy:");
    assertThat(system).contains("## Bootstrap: SOUL.md");
    assertThat(system).endsWith("当前时间: 2026-11-23T04:00:00Z");
    assertThat(system.indexOf("你是测试助手")).isLessThan(system.indexOf("Language policy:"));
    assertThat(system.indexOf("Language policy:")).isLessThan(system.indexOf("## Bootstrap"));
    assertThat(system.indexOf("## Bootstrap")).isLessThan(system.indexOf("当前时间"));
    assertThat(prompt.tools()).containsExactly(echo);
  }

  @Test
  @DisplayName("历史超过最近 N 个完整轮次时被截断")
  void historyIsTruncatedToRecentCompleteTurns() {
    // contextLoader 返回固定上下文。
    ContextLoader contextLoader = mock(ContextLoader.class);
    when(contextLoader.buildContext(any(AgentProfile.class))).thenReturn("context");
    // clock 使用固定时间。
    Clock clock = Clock.fixed(Instant.parse("2026-11-23T04:00:00Z"), ZoneOffset.UTC);
    // builder 是被测对象。
    PromptBuilder builder = new PromptBuilder(Map.of(), contextLoader, clock);
    // session 累积三组问答。
    Session session = new Session("session-1", "test-agent", "cli", "user-1");
    session.appendUser("q1");
    session.appendAssistant(new AgentResponse("a1", List.of()));
    session.appendUser("q2");
    session.appendAssistant(new AgentResponse("a2", List.of()));
    session.appendUser("q3");
    session.appendAssistant(new AgentResponse("a3", List.of()));
    // agentProfile 只保留最近两轮。
    AgentProfile agentProfile = agentProfile(2, List.of());

    // prompt 是组装结果。
    AgentPrompt prompt = builder.build(session, agentProfile);

    // messages 是截断后的历史。
    List<AgentMessage> messages = prompt.messages();
    assertThat(messages).hasSize(4);
    assertThat(messages.get(0).content()).isEqualTo("q2");
    assertThat(messages.get(1).content()).isEqualTo("a2");
    assertThat(messages.get(2).content()).isEqualTo("q3");
    assertThat(messages.get(3).content()).isEqualTo("a3");
  }

  @Test
  @DisplayName("工具列表按 AgentProfile 的工具名过滤并保持顺序")
  void toolsAreFilteredByAgentProfile() {
    // contextLoader 返回固定上下文。
    ContextLoader contextLoader = mock(ContextLoader.class);
    when(contextLoader.buildContext(any(AgentProfile.class))).thenReturn("context");
    // clock 使用固定时间。
    Clock clock = Clock.fixed(Instant.parse("2026-11-23T04:00:00Z"), ZoneOffset.UTC);
    // echo 是测试工具。
    LytgTool echo = new NamedTool("echo");
    // http 是未授权工具。
    LytgTool http = new NamedTool("http_get");
    // builder 是被测对象。
    PromptBuilder builder =
        new PromptBuilder(Map.of("echo", echo, "http_get", http), contextLoader, clock);
    // session 是空会话。
    Session session = new Session("session-1", "test-agent", "cli", "user-1");

    // prompt 是组装结果。
    AgentPrompt prompt = builder.build(session, agentProfile(20, List.of("echo")));

    assertThat(prompt.tools()).containsExactly(echo);
  }

  /**
   * 创建测试 AgentProfile。
   *
   * @param maxHistoryTurns 历史轮数
   * @param toolNames 允许的工具名
   * @return 测试 AgentProfile
   */
  private static AgentProfile agentProfile(int maxHistoryTurns, List<String> toolNames) {
    // identity 是测试身份。
    AgentProfile.Identity identity = new AgentProfile.Identity("测试助手", "你是测试助手");
    // providerRef 是测试 provider 引用。
    AgentProfile.ProviderRef providerRef =
        new AgentProfile.ProviderRef("deepseek", "deepseek-chat", 0.0D);
    // settings 是测试运行时设置。
    AgentProfile.Settings settings = new AgentProfile.Settings(10, maxHistoryTurns);
    return new AgentProfile(
        "test-agent",
        "测试 Agent",
        identity,
        providerRef,
        toolNames,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        settings);
  }

  /**
   * 只提供名称的测试工具。
   *
   * @param name 工具名称
   * @author LytgOS2 Team
   */
  private record NamedTool(String name) implements LytgTool {

    @Override
    public String description() {
      return "测试工具";
    }

    @Override
    public String inputSchema() {
      return "{}";
    }

    @Override
    public ToolResult execute(String inputJson) {
      return ToolResult.ok(inputJson);
    }
  }
}
