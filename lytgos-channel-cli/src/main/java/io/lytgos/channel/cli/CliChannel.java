package io.lytgos.channel.cli;

import io.lytgos.core.agent.AgentService;
import io.lytgos.core.session.Session;
import io.lytgos.core.session.SessionManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * CLI 交互渠道。
 *
 * <p>只负责读取一行、转交 {@link AgentService}、打印最终回复；不包含推理、模型调用、工具执行或会话标识拼接。 输入 {@code /quit} 或流结束时退出。
 *
 * @author LytgOS2 Team
 */
public final class CliChannel {

  /** 渠道名称，参与会话标识的三元组。 */
  public static final String CHANNEL_NAME = "cli";

  /** 退出命令。 */
  private static final String QUIT_COMMAND = "/quit";

  /** 本地化文案。 */
  private final CliChannelMessages messages;

  /** Agent 统一处理入口。 */
  private final AgentService agentService;

  /** 会话统一门面。 */
  private final SessionManager sessionManager;

  /** 当前用户标识。 */
  private final String user;

  /** AgentProfile 名称。 */
  private final String profileName;

  /** 输入流。 */
  private final BufferedReader input;

  /** 标准输出流。 */
  private final PrintWriter output;

  /** 错误输出流。 */
  private final PrintWriter errorOutput;

  /**
   * 创建 CLI 渠道。
   *
   * @param agentService Agent 统一处理入口
   * @param sessionManager 会话统一门面
   * @param user 当前用户标识
   * @param profileName AgentProfile 名称
   * @param input 输入流
   * @param output 标准输出流
   * @param errorOutput 错误输出流
   * @param messages 本地化文案
   */
  public CliChannel(
      AgentService agentService,
      SessionManager sessionManager,
      String user,
      String profileName,
      BufferedReader input,
      PrintWriter output,
      PrintWriter errorOutput,
      CliChannelMessages messages) {
    // 所有协作者与流都必须存在，避免运行期空指针。
    this.agentService = Objects.requireNonNull(agentService, "agentService");
    this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
    this.user = Objects.requireNonNull(user, "user");
    this.profileName = Objects.requireNonNull(profileName, "profileName");
    this.input = Objects.requireNonNull(input, "input");
    this.output = Objects.requireNonNull(output, "output");
    this.errorOutput = Objects.requireNonNull(errorOutput, "errorOutput");
    this.messages = Objects.requireNonNull(messages, "messages");
  }

  /**
   * 运行交互循环。
   *
   * @return 正常退出返回 0；发生 Agent 错误返回 1
   */
  public int run() {
    // session 是本次交互唯一使用的会话，多轮共享同一历史。
    Session session = sessionManager.getOrCreate(CHANNEL_NAME, user, profileName);
    // hadError 记录是否出现过处理错误，决定最终退出码。
    boolean hadError = false;
    while (true) {
      output.print("> ");
      output.flush();
      // line 是用户输入的一行；null 表示输入流结束。
      String line;
      try {
        line = input.readLine();
      } catch (IOException exception) {
        errorOutput.println(messages.inputReadError(exception.getMessage()));
        errorOutput.flush();
        return 1;
      }
      if (line == null) {
        break;
      }
      // trimmed 用于判断空行和退出命令，发送给引擎时仍使用原始行。
      String trimmed = line.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (QUIT_COMMAND.equals(trimmed)) {
        break;
      }
      try {
        // reply 是 Agent 本轮最终答复。
        String reply = agentService.process(session, line);
        output.println(reply);
        output.flush();
      } catch (RuntimeException exception) {
        // 单轮失败不终止交互，用户仍可继续输入或退出。
        hadError = true;
        errorOutput.println(messages.agentError(errorMessage(exception)));
        errorOutput.flush();
      }
    }
    return hadError ? 1 : 0;
  }

  /**
   * 提取可读错误信息。
   *
   * @param exception 运行时异常
   * @return 错误信息
   */
  private static String errorMessage(RuntimeException exception) {
    // message 是异常自带描述；缺失时退回异常类名。
    String message = exception.getMessage();
    return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
  }
}
