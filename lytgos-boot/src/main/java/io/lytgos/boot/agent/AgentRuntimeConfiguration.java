package io.lytgos.boot.agent;

import io.lytgos.core.agent.AgentProfileRegistry;
import io.lytgos.core.agent.AgentService;
import io.lytgos.core.agent.ContextLoader;
import io.lytgos.core.agent.LlmGateway;
import io.lytgos.core.agent.PromptBuilder;
import io.lytgos.core.agent.ReActLoop;
import io.lytgos.core.audit.ToolInvocationAuditor;
import io.lytgos.core.session.Session;
import io.lytgos.core.tool.ToolExecutor;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ReAct 引擎与 AgentService 的启动装配。
 *
 * <p>本节工具集合为空，等 Tool 课程注册内置工具；会话保存钩子在第 18 节替换为 SessionManager。
 *
 * @author LytgOS2 Team
 */
@Configuration
public class AgentRuntimeConfiguration {

  /**
   * 注册上下文加载器。
   *
   * @param workspace 工作区目录
   * @return ContextLoader
   */
  @Bean
  public ContextLoader contextLoader(@Value("${lytgos.workspace:.lytgos}") String workspace) {
    return new ContextLoader(Path.of(workspace));
  }

  /**
   * 注册 Prompt 组装器。
   *
   * @param contextLoader 上下文加载器
   * @return PromptBuilder
   */
  @Bean
  public PromptBuilder promptBuilder(ContextLoader contextLoader) {
    return new PromptBuilder(Map.of(), contextLoader, Clock.systemDefaultZone());
  }

  /**
   * 注册工具执行器。
   *
   * @param auditor 工具调用审计端口
   * @return ToolExecutor
   */
  @Bean
  public ToolExecutor toolExecutor(ToolInvocationAuditor auditor) {
    return new ToolExecutor(Map.of(), auditor);
  }

  /**
   * 注册 ReAct 循环。
   *
   * @param promptBuilder Prompt 组装器
   * @param llmGateway 模型调用端口
   * @param toolExecutor 工具执行器
   * @return ReActLoop
   */
  @Bean
  public ReActLoop reActLoop(
      PromptBuilder promptBuilder, LlmGateway llmGateway, ToolExecutor toolExecutor) {
    return new ReActLoop(promptBuilder, llmGateway, toolExecutor);
  }

  /**
   * 注册 AgentService。
   *
   * @param registry AgentProfile 内存索引
   * @param reActLoop ReAct 循环
   * @return AgentService
   */
  @Bean
  public AgentService agentService(AgentProfileRegistry registry, ReActLoop reActLoop) {
    // sessionSaver 是第 18 节 SessionManager 的接入点；本节只保留调用契约。
    Consumer<Session> sessionSaver =
        new Consumer<Session>() {
          @Override
          public void accept(Session session) {
            // session：当前处理完成的会话，第 18 节在这里落 SQLite。
          }
        };
    return new AgentService(registry, reActLoop, sessionSaver);
  }
}
