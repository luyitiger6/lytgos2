package io.lytgos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.lytgos.core.agent.AgentProfile;
import io.lytgos.core.audit.LlmCallAuditor;
import io.lytgos.core.audit.LlmCallAuditor.LlmCallRecord;
import io.lytgos.core.llm.AgentMessage;
import io.lytgos.core.llm.AgentPrompt;
import io.lytgos.core.llm.AgentResponse;
import io.lytgos.core.llm.AgentToolCall;
import io.lytgos.core.tool.LytgTool;
import io.lytgos.provider.tool.ToolSchemaAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * ProviderService 的路由、审计与工具 schema 行为测试。
 *
 * @author LytgOS2 Team
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class ProviderServiceTest {

  /** 测试用 DeepSeek provider 名称。 */
  private static final String DEEPSEEK_PROVIDER = "deepseek";

  /** 测试用 DeepSeek 模型名称。 */
  private static final String DEEPSEEK_MODEL = "deepseek-chat";

  /** 测试用会话标识。 */
  private static final String SESSION_ID = "session-1";

  /** 测试用用户消息。 */
  private static final String USER_MESSAGE = "hello";

  /** 测试用模型失败消息。 */
  private static final String MODEL_FAILURE_MESSAGE = "boom";

  /** 测试用审计写入失败消息。 */
  private static final String AUDIT_FAILURE_MESSAGE = "audit-write-failed";

  @Test
  @DisplayName("按名称路由时只调用目标 provider，另一个 provider 一次都不调用")
  void routingUsesOnlyTargetProvider() {
    // deepseekModel 是本次调用的目标模型。
    ChatModel deepseekModel = mock(ChatModel.class);
    // kimiModel 是非目标模型，用于验证不串台。
    ChatModel kimiModel = mock(ChatModel.class);
    // response 是目标模型返回的测试响应。
    ChatResponse response = response(10, 20, 30);
    when(deepseekModel.call(any(Prompt.class))).thenReturn(response);
    // auditor 记录本次调用的审计数据。
    RecordingAuditor auditor = new RecordingAuditor();
    // service 是被测对象，使用显式 provider 映射。
    ProviderService service =
        new ProviderService(Map.of(DEEPSEEK_PROVIDER, deepseekModel, "kimi", kimiModel), auditor);

    // actual 是 chat 返回的模型响应。
    ChatResponse actual =
        service.chat(
            SESSION_ID, agentProfile(DEEPSEEK_PROVIDER, DEEPSEEK_MODEL), new Prompt(USER_MESSAGE));

    assertThat(actual).isSameAs(response);
    // promptCaptor 捕获实际发给模型的请求。
    ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    verify(deepseekModel).call(promptCaptor.capture());
    verifyNoInteractions(kimiModel);
    // sentPrompt 是实际发给目标模型的 Prompt。
    Prompt sentPrompt = promptCaptor.getValue();
    assertThat(sentPrompt.getOptions()).isInstanceOf(OpenAiChatOptions.class);
    // sentOptions 是 ProviderService 构造的调用参数。
    OpenAiChatOptions sentOptions = (OpenAiChatOptions) sentPrompt.getOptions();
    assertThat(sentOptions.getModel()).isEqualTo(DEEPSEEK_MODEL);
    assertThat(sentOptions.getTemperature()).isEqualTo(0.7D);
    assertThat(sentOptions.getInternalToolExecutionEnabled()).isFalse();
    assertThat(auditor.records).hasSize(1);
  }

  @Test
  @DisplayName("未知 provider 在模型调用前抛异常，且不产生审计记录")
  void unknownProviderFailsBeforeModelCall() {
    // model 是已配置的 provider 模型。
    ChatModel model = mock(ChatModel.class);
    // auditor 用于确认未写审计。
    RecordingAuditor auditor = new RecordingAuditor();
    // service 只包含 deepseek。
    ProviderService service = new ProviderService(Map.of(DEEPSEEK_PROVIDER, model), auditor);

    assertThatThrownBy(() ->
            service.chat(SESSION_ID, agentProfile("missing", "model-x"), new Prompt(USER_MESSAGE)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing");

    verifyNoInteractions(model);
    assertThat(auditor.records).isEmpty();
  }

  @Test
  @DisplayName("provider 已配置但模型未构建时给出清晰错误")
  void providerWithoutConfiguredModelFailsWithClearMessage() {
    // auditor 用于确认调用前失败不写审计。
    RecordingAuditor auditor = new RecordingAuditor();
    // service 的映射为空，模拟 provider 缺少 key 而未构造模型。
    ProviderService service = new ProviderService(Map.of(), auditor);

    assertThatThrownBy(() ->
            service.chat(
                SESSION_ID,
                agentProfile(DEEPSEEK_PROVIDER, DEEPSEEK_MODEL),
                new Prompt(USER_MESSAGE)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(DEEPSEEK_PROVIDER)
        .hasMessageContaining("不可用");

    assertThat(auditor.records).isEmpty();
  }

  @Test
  @DisplayName("成功调用写入完整审计记录，token 与耗时来自实际响应")
  void successWritesAuditRecord() {
    // model 是返回成功响应的模型。
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenReturn(response(11, 22, 33));
    // auditor 保存成功审计记录。
    RecordingAuditor auditor = new RecordingAuditor();
    // service 是被测对象。
    ProviderService service = new ProviderService(Map.of(DEEPSEEK_PROVIDER, model), auditor);

    service.chat(
        "session-9", agentProfile(DEEPSEEK_PROVIDER, DEEPSEEK_MODEL), new Prompt(USER_MESSAGE));

    assertThat(auditor.records).hasSize(1);
    // record 是成功调用写入的审计数据。
    LlmCallRecord record = auditor.records.get(0);
    assertThat(record.sessionId()).isEqualTo("session-9");
    assertThat(record.profileName()).isEqualTo("test-agent-profile");
    assertThat(record.provider()).isEqualTo(DEEPSEEK_PROVIDER);
    assertThat(record.model()).isEqualTo(DEEPSEEK_MODEL);
    assertThat(record.promptTokens()).isEqualTo(11);
    assertThat(record.completionTokens()).isEqualTo(22);
    assertThat(record.totalTokens()).isEqualTo(33);
    assertThat(record.success()).isTrue();
    assertThat(record.errorMessage()).isNull();
    assertThat(record.durationMs()).isGreaterThanOrEqualTo(0L);
  }

  @Test
  @DisplayName("响应缺少 usage 时成功审计的 token 字段为空")
  void successWithoutUsageWritesNullTokenFields() {
    // model 返回不带 usage 的响应。
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenReturn(responseWithoutUsage());
    // auditor 保存成功审计记录。
    RecordingAuditor auditor = new RecordingAuditor();
    // service 是被测对象。
    ProviderService service = new ProviderService(Map.of(DEEPSEEK_PROVIDER, model), auditor);

    service.chat(
        "session-9", agentProfile(DEEPSEEK_PROVIDER, DEEPSEEK_MODEL), new Prompt(USER_MESSAGE));

    // record 是成功调用写入的审计数据。
    LlmCallRecord record = auditor.records.get(0);
    assertThat(record.success()).isTrue();
    assertThat(record.promptTokens()).isNull();
    assertThat(record.completionTokens()).isNull();
    assertThat(record.totalTokens()).isNull();
  }

  @Test
  @DisplayName("模型调用失败时先落 success=false 与错误原因，再上抛原异常")
  void failureWritesAuditThenRethrowsOriginalException() {
    // model 是抛出业务异常的模型。
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException(MODEL_FAILURE_MESSAGE));
    // auditor 保存失败审计记录。
    RecordingAuditor auditor = new RecordingAuditor();
    // service 是被测对象。
    ProviderService service = new ProviderService(Map.of(DEEPSEEK_PROVIDER, model), auditor);

    assertThatThrownBy(() ->
            service.chat(
                SESSION_ID,
                agentProfile(DEEPSEEK_PROVIDER, DEEPSEEK_MODEL),
                new Prompt(USER_MESSAGE)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(MODEL_FAILURE_MESSAGE);

    assertThat(auditor.records).hasSize(1);
    // record 是失败调用写入的审计数据。
    LlmCallRecord record = auditor.records.get(0);
    assertThat(record.success()).isFalse();
    assertThat(record.errorMessage()).isEqualTo(MODEL_FAILURE_MESSAGE);
    assertThat(record.promptTokens()).isNull();
    assertThat(record.completionTokens()).isNull();
    assertThat(record.totalTokens()).isNull();
  }

  @Test
  @DisplayName("失败审计本身出错时作为 suppressed 保留，原模型异常继续上抛")
  void failureAuditErrorIsSuppressedOnOriginalException() {
    // model 是抛出业务异常的模型。
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException(MODEL_FAILURE_MESSAGE));
    // auditor 模拟审计写入失败。
    RecordingAuditor auditor = new RecordingAuditor(AUDIT_FAILURE_MESSAGE);
    // service 是被测对象。
    ProviderService service = new ProviderService(Map.of(DEEPSEEK_PROVIDER, model), auditor);

    // thrown 是 chat 上抛的异常。
    Throwable thrown =
        catchThrowable(() ->
            service.chat(
                SESSION_ID,
                agentProfile(DEEPSEEK_PROVIDER, DEEPSEEK_MODEL),
                new Prompt(USER_MESSAGE)));

    assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessage(MODEL_FAILURE_MESSAGE);
    assertThat(thrown.getSuppressed()).hasSize(1);
    assertThat(thrown.getSuppressed()[0])
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(AUDIT_FAILURE_MESSAGE);
  }

  @Test
  @DisplayName("成功调用时审计写入失败直接上抛，不静默吞掉")
  void successAuditErrorIsPropagated() {
    // model 返回成功响应。
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenReturn(response(1, 2, 3));
    // auditor 模拟审计写入失败。
    RecordingAuditor auditor = new RecordingAuditor(AUDIT_FAILURE_MESSAGE);
    // service 是被测对象。
    ProviderService service = new ProviderService(Map.of(DEEPSEEK_PROVIDER, model), auditor);

    assertThatThrownBy(() ->
            service.chat(
                SESSION_ID,
                agentProfile(DEEPSEEK_PROVIDER, DEEPSEEK_MODEL),
                new Prompt(USER_MESSAGE)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(AUDIT_FAILURE_MESSAGE);
  }

  @Test
  @DisplayName("带工具调用时透传 schema 回调并关闭自动工具执行")
  void toolSchemaIsForwardedAndAutoExecutionIsDisabled() {
    // callback 是只含 schema 的工具回调。
    ToolCallback callback = toolCallback("weather");
    // prompt 携带工具回调，模拟后续 ReAct 引擎的调用方式。
    Prompt prompt =
        new Prompt(USER_MESSAGE, OpenAiChatOptions.builder().toolCallbacks(callback).build());
    // model 返回成功响应。
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenReturn(response(1, 2, 3));
    // auditor 保存成功审计记录。
    RecordingAuditor auditor = new RecordingAuditor();
    // service 是被测对象。
    ProviderService service = new ProviderService(Map.of(DEEPSEEK_PROVIDER, model), auditor);

    service.chat(SESSION_ID, agentProfile(DEEPSEEK_PROVIDER, DEEPSEEK_MODEL), prompt);

    // promptCaptor 捕获实际发给模型的请求。
    ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    verify(model).call(promptCaptor.capture());
    // sentOptions 是 ProviderService 构造的调用参数。
    OpenAiChatOptions sentOptions = (OpenAiChatOptions) promptCaptor.getValue().getOptions();
    assertThat(sentOptions).isInstanceOf(ToolCallingChatOptions.class);
    assertThat(sentOptions.getToolCallbacks()).hasSize(1);
    assertThat(sentOptions.getToolCallbacks().get(0).getToolDefinition().name())
        .isEqualTo("weather");
    assertThat(sentOptions.getInternalToolExecutionEnabled()).isFalse();
  }

  @Test
  @DisplayName("LlmGateway 适配 core 请求与响应")
  void gatewayChatConvertsAgentPromptAndResponse() {
    // coreCall 是 core 工具调用。
    AgentToolCall coreCall = new AgentToolCall("call-1", "weather", "{}");
    // messages 是 core 会话历史。
    List<AgentMessage> messages =
        List.of(
            AgentMessage.user("hi"),
            AgentMessage.assistant("", List.of(coreCall)),
            AgentMessage.tool("weather", "call-1", "sunny"));
    // tool 是测试工具定义。
    LytgTool tool = mock(LytgTool.class);
    when(tool.name()).thenReturn("weather");
    when(tool.description()).thenReturn("天气查询");
    when(tool.inputSchema()).thenReturn("{\"type\":\"object\"}");
    // agentPrompt 是 core 请求。
    AgentPrompt agentPrompt = new AgentPrompt("system", messages, List.of(tool));
    // springCall 是模型返回的 Spring AI 工具调用。
    AssistantMessage.ToolCall springCall =
        new AssistantMessage.ToolCall("call-1", "function", "weather", "{}");
    // assistant 是模型返回的回复。
    AssistantMessage assistant =
        AssistantMessage.builder().content("带伞").toolCalls(List.of(springCall)).build();
    // response 是模型原始响应。
    ChatResponse response =
        new ChatResponse(
            List.of(new Generation(assistant)), ChatResponseMetadata.builder().build());
    // model 返回测试响应。
    ChatModel model = mock(ChatModel.class);
    when(model.call(any(Prompt.class))).thenReturn(response);
    // auditor 保存成功审计记录。
    RecordingAuditor auditor = new RecordingAuditor();
    // service 是被测对象。
    ProviderService service = new ProviderService(Map.of(DEEPSEEK_PROVIDER, model), auditor);

    // actual 是 core 响应。
    AgentResponse actual =
        service.chat(SESSION_ID, agentProfile(DEEPSEEK_PROVIDER, DEEPSEEK_MODEL), agentPrompt);

    assertThat(actual.text()).isEqualTo("带伞");
    assertThat(actual.toolCalls()).containsExactly(coreCall);
    // promptCaptor 捕获实际发给模型的请求。
    ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
    verify(model).call(promptCaptor.capture());
    // sentPrompt 是转换后的 Spring AI 请求。
    Prompt sentPrompt = promptCaptor.getValue();
    assertThat(sentPrompt.getInstructions()).hasSize(4);
    assertThat(sentPrompt.getInstructions().get(0)).isInstanceOf(SystemMessage.class);
    assertThat(sentPrompt.getInstructions().get(1)).isInstanceOf(UserMessage.class);
    assertThat(sentPrompt.getInstructions().get(2)).isInstanceOf(AssistantMessage.class);
    assertThat(sentPrompt.getInstructions().get(3)).isInstanceOf(ToolResponseMessage.class);
    // sentOptions 是 ProviderService 构造的调用参数。
    OpenAiChatOptions sentOptions = (OpenAiChatOptions) sentPrompt.getOptions();
    assertThat(sentOptions.getToolCallbacks()).hasSize(1);
    assertThat(sentOptions.getInternalToolExecutionEnabled()).isFalse();
    assertThat(auditor.records).hasSize(1);
    assertThat(auditor.records.get(0).success()).isTrue();
  }

  /**
   * 构造测试用 AgentProfile。
   *
   * @param providerName provider 名称
   * @param model 模型名称
   * @return 测试 AgentProfile
   */
  private static AgentProfile agentProfile(String providerName, String model) {
    // providerRef 是测试用 provider 引用。
    AgentProfile.ProviderRef providerRef = new AgentProfile.ProviderRef(providerName, model, 0.7D);
    return new AgentProfile(
        "test-agent-profile",
        "测试 AgentProfile",
        null,
        providerRef,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  /**
   * 构造带 usage 的测试响应。
   *
   * @param promptTokens 输入 token 数
   * @param completionTokens 输出 token 数
   * @param totalTokens 总 token 数
   * @return 测试响应
   */
  private static ChatResponse response(
      Integer promptTokens, Integer completionTokens, Integer totalTokens) {
    // usage 是测试用 token 用量。
    Usage usage = new DefaultUsage(promptTokens, completionTokens, totalTokens);
    // metadata 是带 usage 的响应元数据。
    ChatResponseMetadata metadata = ChatResponseMetadata.builder().usage(usage).build();
    // generation 是测试用生成结果。
    Generation generation = new Generation(new AssistantMessage("ok"));
    return new ChatResponse(List.of(generation), metadata);
  }

  /**
   * 构造不带 usage 的测试响应。
   *
   * @return 测试响应
   */
  private static ChatResponse responseWithoutUsage() {
    // metadata 是不带 usage 的响应元数据。
    ChatResponseMetadata metadata = ChatResponseMetadata.builder().build();
    // generation 是测试用生成结果。
    Generation generation = new Generation(new AssistantMessage("ok"));
    return new ChatResponse(List.of(generation), metadata);
  }

  /**
   * 构造只含 schema 的工具回调。
   *
   * @param name 工具名称
   * @return 工具回调
   */
  private static ToolCallback toolCallback(String name) {
    // tool 是测试用工具定义。
    LytgTool tool = mock(LytgTool.class);
    when(tool.name()).thenReturn(name);
    when(tool.description()).thenReturn("天气查询");
    when(tool.inputSchema()).thenReturn("{\"type\":\"object\"}");
    return new ToolSchemaAdapter().toToolCallbacks(List.of(tool)).get(0);
  }

  /**
   * 记录审计数据的测试替身；可模拟写入失败。
   *
   * @author LytgOS2 Team
   */
  private static final class RecordingAuditor implements LlmCallAuditor {

    /** 已记录的审计数据。 */
    private final List<LlmCallRecord> records = new ArrayList<>();

    /** 非空时表示每次 record 都抛出带该消息的异常。 */
    private final String failureMessage;

    /** 创建正常工作的审计替身。 */
    private RecordingAuditor() {
      this(null);
    }

    /**
     * 创建可模拟写入失败的审计替身。
     *
     * @param failureMessage 审计失败消息；为 null 表示正常记录
     */
    private RecordingAuditor(String failureMessage) {
      this.failureMessage = failureMessage;
    }

    @Override
    public void record(LlmCallRecord record) {
      // failureMessage 非空表示模拟审计写入失败。
      if (failureMessage != null) {
        throw new IllegalStateException(failureMessage);
      }
      records.add(record);
    }
  }
}
