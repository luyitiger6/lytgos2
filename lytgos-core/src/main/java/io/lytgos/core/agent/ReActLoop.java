package io.lytgos.core.agent;

import io.lytgos.core.llm.AgentPrompt;
import io.lytgos.core.llm.AgentResponse;
import io.lytgos.core.llm.AgentToolCall;
import io.lytgos.core.session.Session;
import io.lytgos.core.tool.ToolExecutor;
import io.lytgos.core.tool.ToolResult;
import java.util.Objects;

/**
 * 自实现的 ReAct 循环：只负责调度、停止判断与消息累积。
 *
 * <p>Prompt 组装交给 PromptBuilder，模型调用交给 LlmGateway，工具执行交给 ToolExecutor。
 *
 * @author LytgOS2 Team
 */
public final class ReActLoop {

  /** 最大轮数缺省值。 */
  private static final int DEFAULT_MAX_ITERATIONS = 10;

  /** 达到最大轮数时的固定返回文本。 */
  private static final String MAX_ITERATIONS_MESSAGE = "达到最大轮数，已停止";

  /** Prompt 组装器。 */
  private final PromptBuilder promptBuilder;

  /** 模型调用端口。 */
  private final LlmGateway llmGateway;

  /** 工具执行器。 */
  private final ToolExecutor toolExecutor;

  /**
   * 创建 ReAct 循环。
   *
   * @param promptBuilder Prompt 组装器
   * @param llmGateway 模型调用端口
   * @param toolExecutor 工具执行器
   */
  public ReActLoop(PromptBuilder promptBuilder, LlmGateway llmGateway, ToolExecutor toolExecutor) {
    // 三个协作者都不能为空。
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
    this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway");
    this.toolExecutor = Objects.requireNonNull(toolExecutor, "toolExecutor");
  }

  /**
   * 运行一次 ReAct 循环。
   *
   * @param session 当前会话
   * @param userMessage 用户消息
   * @param agentProfile 当前 AgentProfile
   * @return 最终文本或强制结束消息
   */
  public String run(Session session, String userMessage, AgentProfile agentProfile) {
    Objects.requireNonNull(session, "session");
    Objects.requireNonNull(agentProfile, "agentProfile");
    // 用户消息先进入会话历史，后续每轮 prompt 都会带上。
    session.appendUser(userMessage);
    // maxIterations 是本轮允许的最大模型调用次数。
    int maxIterations = effectiveMaxIterations(agentProfile);
    for (int iteration = 0; iteration < maxIterations; iteration++) {
      // prompt 是当前轮的完整请求。
      AgentPrompt prompt = promptBuilder.build(session, agentProfile);
      // response 是模型本轮响应。
      AgentResponse response = llmGateway.chat(session.sessionId(), agentProfile, prompt);
      // 模型响应先进入历史，保证每轮都可审计。
      session.appendAssistant(response);
      if (!response.hasToolCalls()) {
        return response.text();
      }
      for (AgentToolCall call : response.toolCalls()) {
        // result 是本次工具执行结果；失败结果同样回填给模型。
        ToolResult result = toolExecutor.execute(session.sessionId(), call);
        session.appendToolResult(call, result);
      }
    }
    return MAX_ITERATIONS_MESSAGE;
  }

  /**
   * 计算有效最大轮数。
   *
   * @param agentProfile 当前 AgentProfile
   * @return 最大轮数；缺省或非正数时返回 10
   */
  private int effectiveMaxIterations(AgentProfile agentProfile) {
    // configuredIterations 是 AgentProfile 中配置的最大轮数。
    Integer configuredIterations =
        agentProfile.settings() == null ? null : agentProfile.settings().maxIterations();
    return configuredIterations == null || configuredIterations <= 0
        ? DEFAULT_MAX_ITERATIONS
        : configuredIterations;
  }
}
