package io.lytgos.provider;

import io.lytgos.core.agent.AgentProfile;
import io.lytgos.core.agent.LlmGateway;
import io.lytgos.core.audit.LlmCallAuditor;
import io.lytgos.core.audit.LlmCallAuditor.LlmCallRecord;
import io.lytgos.core.llm.AgentMessage;
import io.lytgos.core.llm.AgentPrompt;
import io.lytgos.core.llm.AgentResponse;
import io.lytgos.core.llm.AgentRole;
import io.lytgos.core.llm.AgentToolCall;
import io.lytgos.provider.tool.ToolSchemaAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Provider 薄封装：按 AgentProfile 显式选择模型，完成一次模型调用并写审计。
 *
 * <p>本节的具体 provider 为 OpenAI 兼容模型；SAA 的 DashScope 模型属于未来扩展，接入时需要 provider-specific options
 * 适配，不在本节实现。工具 schema 由调用方放入 Prompt options， ProviderService 只负责关闭自动执行并透传回调。
 *
 * @author LytgOS2 Team
 */
public final class ProviderService implements LlmGateway {

  /** 显式的 provider 名称到模型映射。 */
  private final Map<String, ChatModel> providerModels;

  /** LLM 调用审计端口。 */
  private final LlmCallAuditor auditor;

  /**
   * 创建 ProviderService。
   *
   * @param providerModels 显式 provider 名称到模型的映射
   * @param auditor 调用审计端口
   */
  public ProviderService(Map<String, ChatModel> providerModels, LlmCallAuditor auditor) {
    // 复制映射，避免外部修改影响 Provider 的选择结果。
    this.providerModels = providerModels == null ? Map.of() : Map.copyOf(providerModels);
    // 审计端口不能为空。
    this.auditor = Objects.requireNonNull(auditor, "auditor");
  }

  /**
   * 按 AgentProfile 发起一次模型调用。
   *
   * @param sessionId 会话标识，可空
   * @param agentProfile 本次调用的 AgentProfile
   * @param prompt 待发送的消息与可选工具回调
   * @return 模型原始响应
   */
  public ChatResponse chat(String sessionId, AgentProfile agentProfile, Prompt prompt) {
    Objects.requireNonNull(agentProfile, "agentProfile");
    Objects.requireNonNull(prompt, "prompt");
    // providerName 是显式映射的键，缺失时在调用模型前报错。
    String providerName = agentProfile.provider() == null ? null : agentProfile.provider().name();
    if (providerName == null || providerName.isBlank()) {
      throw new IllegalArgumentException("AgentProfile 未指定 provider");
    }
    // model 是本次调用实际使用的底层模型。
    ChatModel model = providerModels.get(providerName);
    if (model == null) {
      throw new IllegalArgumentException("未知或不可用的 provider: " + providerName);
    }
    // toolCallbacks 来自调用方 Prompt options；这里只透传，不执行。
    List<ToolCallback> toolCallbacks = extractToolCallbacks(prompt.getOptions());
    // options 覆盖 AgentProfile 的 model/temperature，并强制关闭自动工具执行。
    OpenAiChatOptions options =
        OpenAiChatOptions.builder()
            .model(agentProfile.provider().model())
            .temperature(agentProfile.provider().temperature())
            .toolCallbacks(toolCallbacks)
            .internalToolExecutionEnabled(false)
            .build();
    // request 保留原始消息，只替换本次调用 options。
    Prompt request = new Prompt(prompt.getInstructions(), options);
    // startedAtNanos 用于计算调用耗时。
    long startedAtNanos = System.nanoTime();
    ChatResponse response;
    try {
      // 一次 chat 只调用一次底层模型。
      response = model.call(request);
    } catch (RuntimeException exception) {
      // 失败先审计，再把原始异常上抛给调用方。
      recordFailure(sessionId, agentProfile, providerName, startedAtNanos, exception);
      throw exception;
    }
    // 成功审计独立于模型调用；审计失败不会被误记为模型失败。
    recordSuccess(sessionId, agentProfile, providerName, startedAtNanos, response);
    return response;
  }

  @Override
  public AgentResponse chat(String sessionId, AgentProfile agentProfile, AgentPrompt agentPrompt) {
    Objects.requireNonNull(agentPrompt, "agentPrompt");
    // springPrompt 是 core 请求转换后的 Spring AI 请求。
    Prompt springPrompt = toSpringPrompt(agentPrompt);
    // response 是底层模型的原始响应，审计仍由上面的 chat 入口完成。
    ChatResponse response = chat(sessionId, agentProfile, springPrompt);
    return toAgentResponse(response);
  }

  /**
   * 把 core 请求转换为 Spring AI 请求。
   *
   * @param agentPrompt core 请求
   * @return Spring AI 请求
   */
  private Prompt toSpringPrompt(AgentPrompt agentPrompt) {
    // messages 保存转换后的 system/会话消息。
    List<Message> messages = new ArrayList<>();
    if (!agentPrompt.systemPrompt().isBlank()) {
      messages.add(new SystemMessage(agentPrompt.systemPrompt()));
    }
    for (AgentMessage message : agentPrompt.messages()) {
      // springMessage 是单条 core 消息的 Spring AI 表示。
      Message springMessage = toSpringMessage(message);
      if (springMessage != null) {
        messages.add(springMessage);
      }
    }
    // callbacks 来自 core 工具定义的 schema，不包含执行逻辑。
    List<ToolCallback> callbacks = new ToolSchemaAdapter().toToolCallbacks(agentPrompt.tools());
    // options 只放工具回调；model/temperature 由 AgentProfile 在 chat 中覆盖。
    OpenAiChatOptions options = OpenAiChatOptions.builder().toolCallbacks(callbacks).build();
    return new Prompt(messages, options);
  }

  /**
   * 把单条 core 消息转换为 Spring AI 消息。
   *
   * @param message core 消息
   * @return Spring AI 消息；无需发送时返回 null
   */
  private Message toSpringMessage(AgentMessage message) {
    if (message.role() == AgentRole.SYSTEM) {
      return message.content().isBlank() ? null : new SystemMessage(message.content());
    }
    if (message.role() == AgentRole.USER) {
      return new UserMessage(message.content());
    }
    if (message.role() == AgentRole.ASSISTANT) {
      return AssistantMessage.builder()
          .content(message.content())
          .toolCalls(toSpringToolCalls(message.toolCalls()))
          .build();
    }
    if (message.role() == AgentRole.TOOL) {
      // toolName/toolCallId 缺失时使用空字符串，保证构造合法。
      String toolName = message.toolName() == null ? "" : message.toolName();
      String toolCallId = message.toolCallId() == null ? "" : message.toolCallId();
      return ToolResponseMessage.builder()
          .responses(
              List.of(
                  new ToolResponseMessage.ToolResponse(toolCallId, toolName, message.content())))
          .build();
    }
    return null;
  }

  /**
   * 把 core 工具调用转换为 Spring AI 工具调用。
   *
   * @param toolCalls core 工具调用列表
   * @return Spring AI 工具调用列表
   */
  private List<AssistantMessage.ToolCall> toSpringToolCalls(List<AgentToolCall> toolCalls) {
    // result 保存转换后的工具调用。
    List<AssistantMessage.ToolCall> result = new ArrayList<>();
    for (AgentToolCall toolCall : toolCalls) {
      result.add(
          new AssistantMessage.ToolCall(
              toolCall.id(), "function", toolCall.name(), toolCall.arguments()));
    }
    return List.copyOf(result);
  }

  /**
   * 把 Spring AI 响应转换为 core 响应。
   *
   * @param response Spring AI 响应
   * @return core 响应
   */
  private AgentResponse toAgentResponse(ChatResponse response) {
    // output 是模型生成的 AssistantMessage。
    AssistantMessage output = response.getResult().getOutput();
    // toolCalls 保存转换后的工具调用。
    List<AgentToolCall> toolCalls =
        output.getToolCalls().stream()
            .map(call -> new AgentToolCall(call.id(), call.name(), call.arguments()))
            .toList();
    return new AgentResponse(output.getText(), toolCalls);
  }

  /**
   * 从 Prompt options 提取工具回调。
   *
   * @param options Prompt options
   * @return 工具回调；没有工具时返回空列表
   */
  private List<ToolCallback> extractToolCallbacks(ChatOptions options) {
    if (options instanceof ToolCallingChatOptions) {
      // toolingOptions 是支持工具回调的 options。
      ToolCallingChatOptions toolingOptions = (ToolCallingChatOptions) options;
      // callbacks 由 Spring AI 契约保证非空，这里复制为不可变列表。
      List<ToolCallback> callbacks = toolingOptions.getToolCallbacks();
      return List.copyOf(callbacks);
    }
    return List.of();
  }

  /**
   * 写成功审计记录。
   *
   * @param sessionId 会话标识
   * @param agentProfile 本次 AgentProfile
   * @param providerName provider 名称
   * @param startedAtNanos 开始时间
   * @param response 模型响应
   */
  private void recordSuccess(
      String sessionId,
      AgentProfile agentProfile,
      String providerName,
      long startedAtNanos,
      ChatResponse response) {
    // usage 可能为空，不同 provider 返回能力不同；ChatResponse 元数据契约保证非空。
    Usage usage = response.getMetadata().getUsage();
    // usageAvailable 表示 provider 实际返回了可用的 token 用量。
    boolean usageAvailable = usage != null && !(usage instanceof EmptyUsage);
    // 各 token 字段可空，不能用 0 假设 provider 一定返回。
    Integer promptTokens = usageAvailable ? usage.getPromptTokens() : null;
    Integer completionTokens = usageAvailable ? usage.getCompletionTokens() : null;
    Integer totalTokens = usageAvailable ? usage.getTotalTokens() : null;
    // successRecord 是本条成功调用的审计数据。
    LlmCallRecord successRecord =
        new LlmCallRecord(
            sessionId,
            agentProfile.name(),
            providerName,
            agentProfile.provider().model(),
            promptTokens,
            completionTokens,
            totalTokens,
            durationMillis(startedAtNanos),
            true,
            null);
    // 审计异常直接上抛，不静默吞掉。
    auditor.record(successRecord);
  }

  /**
   * 写失败审计记录，并把审计异常附加到原异常。
   *
   * @param sessionId 会话标识
   * @param agentProfile 本次 AgentProfile
   * @param providerName provider 名称
   * @param startedAtNanos 开始时间
   * @param exception 模型调用异常
   */
  private void recordFailure(
      String sessionId,
      AgentProfile agentProfile,
      String providerName,
      long startedAtNanos,
      RuntimeException exception) {
    // failureRecord 记录失败原因，token 字段为空。
    LlmCallRecord failureRecord =
        new LlmCallRecord(
            sessionId,
            agentProfile.name(),
            providerName,
            agentProfile.provider().model(),
            null,
            null,
            null,
            durationMillis(startedAtNanos),
            false,
            exception.getMessage());
    try {
      // 失败审计必须尽量落账；异常继续上抛。
      auditor.record(failureRecord);
    } catch (RuntimeException auditException) {
      // 审计异常作为 suppressed 保留，原模型异常仍是主异常。
      exception.addSuppressed(auditException);
    }
  }

  /**
   * 计算从开始到现在的毫秒数。
   *
   * @param startedAtNanos 开始时间
   * @return 耗时毫秒
   */
  private long durationMillis(long startedAtNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
  }
}
