package io.lytgos.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.lytgos.core.llm.AgentMessage;
import io.lytgos.core.llm.AgentPrompt;
import io.lytgos.core.llm.AgentResponse;
import io.lytgos.core.llm.AgentRole;
import io.lytgos.core.llm.AgentToolCall;
import io.lytgos.core.session.Session;
import io.lytgos.core.tool.ToolExecutor;
import io.lytgos.core.tool.ToolResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * ReActLoop 的轮次、停止条件、工具回填与消息累积测试。
 *
 * @author LytgOS2 Team
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class ReActLoopTest {

  @Test
  @DisplayName("模型没有工具调用时一轮收尾并返回最终文本")
  void noToolCallFinishesInOneRound() {
    // promptBuilder 返回固定的测试请求。
    PromptBuilder promptBuilder = mock(PromptBuilder.class);
    // llmGateway 返回无工具调用的最终文本。
    LlmGateway llmGateway = mock(LlmGateway.class);
    // toolExecutor 不应被调用。
    ToolExecutor toolExecutor = mock(ToolExecutor.class);
    when(promptBuilder.build(any(), any()))
        .thenReturn(new AgentPrompt("system", List.of(), List.of()));
    when(llmGateway.chat(anyString(), any(AgentProfile.class), any(AgentPrompt.class)))
        .thenReturn(new AgentResponse("done", List.of()));
    // loop 是被测循环。
    ReActLoop loop = new ReActLoop(promptBuilder, llmGateway, toolExecutor);
    // session 是测试会话。
    Session session = session();

    // reply 是循环最终答复。
    String reply = loop.run(session, "hi", agentProfile(10));

    assertThat(reply).isEqualTo("done");
    verify(llmGateway, times(1)).chat(anyString(), any(AgentProfile.class), any(AgentPrompt.class));
    // messages 是会话历史快照。
    List<AgentMessage> messages = session.messages();
    assertThat(messages).hasSize(2);
    assertThat(messages.get(0).role()).isEqualTo(AgentRole.USER);
    assertThat(messages.get(0).content()).isEqualTo("hi");
    assertThat(messages.get(1).role()).isEqualTo(AgentRole.ASSISTANT);
    assertThat(messages.get(1).content()).isEqualTo("done");
  }

  @Test
  @DisplayName("模型返回工具调用时执行工具并把结果回填到下一轮")
  void toolCallIsExecutedAndBackfilled() {
    // promptBuilder 返回固定的测试请求。
    PromptBuilder promptBuilder = mock(PromptBuilder.class);
    // llmGateway 第一轮返回工具调用，第二轮返回最终文本。
    LlmGateway llmGateway = mock(LlmGateway.class);
    // toolExecutor 返回工具结果。
    ToolExecutor toolExecutor = mock(ToolExecutor.class);
    // call 是模型请求的工具调用。
    AgentToolCall call = new AgentToolCall("call-1", "weather", "{}");
    when(promptBuilder.build(any(), any()))
        .thenReturn(new AgentPrompt("system", List.of(), List.of()));
    when(llmGateway.chat(anyString(), any(AgentProfile.class), any(AgentPrompt.class)))
        .thenReturn(new AgentResponse("", List.of(call)), new AgentResponse("带伞", List.of()));
    when(toolExecutor.execute(eq("session-1"), eq(call))).thenReturn(ToolResult.ok("sunny"));
    // loop 是被测循环。
    ReActLoop loop = new ReActLoop(promptBuilder, llmGateway, toolExecutor);
    // session 是测试会话。
    Session session = session();

    // reply 是循环最终答复。
    String reply = loop.run(session, "查天气", agentProfile(10));

    assertThat(reply).isEqualTo("带伞");
    verify(llmGateway, times(2)).chat(anyString(), any(AgentProfile.class), any(AgentPrompt.class));
    verify(toolExecutor, times(1)).execute("session-1", call);
    // messages 是会话历史快照。
    List<AgentMessage> messages = session.messages();
    assertThat(messages).hasSize(4);
    assertThat(messages.get(1).role()).isEqualTo(AgentRole.ASSISTANT);
    assertThat(messages.get(1).toolCalls()).containsExactly(call);
    assertThat(messages.get(2).role()).isEqualTo(AgentRole.TOOL);
    assertThat(messages.get(2).toolName()).isEqualTo("weather");
    assertThat(messages.get(2).content()).isEqualTo("sunny");
    assertThat(messages.get(3).content()).isEqualTo("带伞");
  }

  @Test
  @DisplayName("一轮多个工具调用按顺序执行并逐个回填")
  void multipleToolCallsExecuteSequentially() {
    // promptBuilder 返回固定的测试请求。
    PromptBuilder promptBuilder = mock(PromptBuilder.class);
    // llmGateway 第一轮返回两个工具调用，第二轮返回最终文本。
    LlmGateway llmGateway = mock(LlmGateway.class);
    // toolExecutor 返回工具结果。
    ToolExecutor toolExecutor = mock(ToolExecutor.class);
    // firstCall 是第一个工具调用。
    AgentToolCall firstCall = new AgentToolCall("call-1", "weather", "{}");
    // secondCall 是第二个工具调用。
    AgentToolCall secondCall = new AgentToolCall("call-2", "traffic", "{}");
    when(promptBuilder.build(any(), any()))
        .thenReturn(new AgentPrompt("system", List.of(), List.of()));
    when(llmGateway.chat(anyString(), any(AgentProfile.class), any(AgentPrompt.class)))
        .thenReturn(
            new AgentResponse("", List.of(firstCall, secondCall)),
            new AgentResponse("done", List.of()));
    when(toolExecutor.execute(anyString(), any(AgentToolCall.class)))
        .thenReturn(ToolResult.ok("ok"));
    // loop 是被测循环。
    ReActLoop loop = new ReActLoop(promptBuilder, llmGateway, toolExecutor);
    // session 是测试会话。
    Session session = session();

    loop.run(session, "hi", agentProfile(10));

    // order 验证工具按响应顺序执行。
    InOrder order = inOrder(toolExecutor);
    order.verify(toolExecutor).execute("session-1", firstCall);
    order.verify(toolExecutor).execute("session-1", secondCall);
    assertThat(session.messages()).hasSize(5);
  }

  @Test
  @DisplayName("模型一直要求工具调用时恰好执行最大轮数并强制停止")
  void maxIterationsStopsExactlyAtLimit() {
    // promptBuilder 返回固定的测试请求。
    PromptBuilder promptBuilder = mock(PromptBuilder.class);
    // llmGateway 每轮都返回工具调用。
    LlmGateway llmGateway = mock(LlmGateway.class);
    // toolExecutor 返回成功结果。
    ToolExecutor toolExecutor = mock(ToolExecutor.class);
    // call 是每轮都请求的工具调用。
    AgentToolCall call = new AgentToolCall("call-1", "weather", "{}");
    when(promptBuilder.build(any(), any()))
        .thenReturn(new AgentPrompt("system", List.of(), List.of()));
    when(llmGateway.chat(anyString(), any(AgentProfile.class), any(AgentPrompt.class)))
        .thenReturn(new AgentResponse("", List.of(call)));
    when(toolExecutor.execute(anyString(), any(AgentToolCall.class)))
        .thenReturn(ToolResult.ok("again"));
    // loop 是被测循环。
    ReActLoop loop = new ReActLoop(promptBuilder, llmGateway, toolExecutor);
    // session 是测试会话。
    Session session = session();

    // reply 是强制停止消息。
    String reply = loop.run(session, "hi", agentProfile(10));

    assertThat(reply).isEqualTo("达到最大轮数，已停止");
    verify(llmGateway, times(10))
        .chat(anyString(), any(AgentProfile.class), any(AgentPrompt.class));
    verify(toolExecutor, times(10)).execute(eq("session-1"), eq(call));
    assertThat(session.messages()).hasSize(21);
  }

  /**
   * 创建测试会话。
   *
   * @return 测试会话
   */
  private static Session session() {
    return new Session("session-1", "test-agent", "cli", "user-1");
  }

  /**
   * 创建测试 AgentProfile。
   *
   * @param maxIterations 最大轮数
   * @return 测试 AgentProfile
   */
  private static AgentProfile agentProfile(int maxIterations) {
    // identity 是测试身份。
    AgentProfile.Identity identity = new AgentProfile.Identity("测试助手", "你是测试助手");
    // providerRef 是测试 provider 引用。
    AgentProfile.ProviderRef providerRef =
        new AgentProfile.ProviderRef("deepseek", "deepseek-chat", 0.0D);
    // settings 是测试运行时设置。
    AgentProfile.Settings settings = new AgentProfile.Settings(maxIterations, 20);
    return new AgentProfile(
        "test-agent",
        "测试 Agent",
        identity,
        providerRef,
        List.of("weather", "traffic"),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        settings);
  }
}
