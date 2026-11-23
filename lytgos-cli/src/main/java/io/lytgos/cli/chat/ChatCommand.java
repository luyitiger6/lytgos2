package io.lytgos.cli.chat;

import io.lytgos.channel.cli.CliChannel;
import io.lytgos.channel.cli.CliChannelMessages;
import io.lytgos.cli.i18n.CliMessages;
import io.lytgos.cli.profile.ProfileNames;
import io.lytgos.cli.runtime.SpringRuntime;
import io.lytgos.cli.workspace.WorkspacePaths;
import io.lytgos.core.agent.AgentService;
import io.lytgos.core.session.SessionManager;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.springframework.boot.WebApplicationType;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * 在终端里与 Agent 交互式对话。
 *
 * <p>命令只负责校验 Profile、启动运行时、构造输入输出流和转交 {@link CliChannel}；推理与工具执行都在引擎层。
 *
 * @author LytgOS2 Team
 */
@CommandLine.Command(
    name = "chat",
    mixinStandardHelpOptions = true,
    description = "在终端里和 Agent 交互式对话")
public final class ChatCommand implements Callable<Integer> {

  /** 默认 AgentProfile 名称。 */
  private static final String DEFAULT_PROFILE = "default";

  /** 默认用户标识。 */
  private static final String DEFAULT_USER = "local";

  /** CLI 渠道的本地化文案。 */
  private static final CliChannelMessages CHANNEL_MESSAGES = new CliChannelMessagesAdapter();

  /** 工作区路径。 */
  private final WorkspacePaths paths;

  /** Spring 运行时启动器。 */
  private final SpringRuntime runtime;

  /** AgentProfile 名称。 */
  @CommandLine.Option(
      names = {"-p", "--profile"},
      defaultValue = DEFAULT_PROFILE,
      description = "使用的 AgentProfile 名称",
      descriptionKey = "cli.chat.option.profile")
  private String profileName;

  /** Picocli 注入的命令模型，用于获取输出流。 */
  @CommandLine.Spec private CommandSpec spec;

  /** 按默认工作区创建命令；Picocli 使用此构造器。 */
  public ChatCommand() {
    this(WorkspacePaths.defaultPaths(), new SpringRuntime());
  }

  /**
   * 创建对话命令。
   *
   * @param paths 工作区路径
   * @param runtime Spring 运行时启动器
   */
  public ChatCommand(WorkspacePaths paths, SpringRuntime runtime) {
    this.paths = Objects.requireNonNull(paths, "paths");
    this.runtime = Objects.requireNonNull(runtime, "runtime");
  }

  @Override
  public Integer call() {
    // profileFile 是本次对话使用的 AgentProfile 文件。
    Path profileFile;
    try {
      profileFile = ProfileNames.resolve(paths.profiles(), profileName);
    } catch (IllegalArgumentException exception) {
      return printError(exception.getMessage());
    }
    if (!Files.isRegularFile(profileFile)) {
      return printError(CliMessages.get("cli.chat.profileMissing", profileFile.toAbsolutePath()));
    }
    try {
      // 对话只需要引擎与会话能力，不创建嵌入式 Web 服务器。
      runtime.start(WebApplicationType.NONE);
      // agentService 是 Agent 统一入口。
      AgentService agentService = runtime.getBean(AgentService.class);
      // sessionManager 是会话统一门面。
      SessionManager sessionManager = runtime.getBean(SessionManager.class);
      return runChannel(agentService, sessionManager);
    } catch (RuntimeException exception) {
      return printError(CliMessages.get("cli.chat.startError", errorMessage(exception)));
    } finally {
      // 对话结束后关闭 Spring 上下文，释放 SQLite 连接。
      runtime.close();
    }
  }

  /**
   * 构造内存/终端流并运行 CLI 渠道。
   *
   * @param agentService Agent 统一入口
   * @param sessionManager 会话统一门面
   * @return 渠道退出码
   */
  @SuppressWarnings({"PMD.CloseResource", "PMD.AvoidFileStream"})
  private int runChannel(AgentService agentService, SessionManager sessionManager) {
    // out 是 Picocli 的标准输出。
    PrintWriter out = spec.commandLine().getOut();
    // err 是 Picocli 的错误输出。
    PrintWriter err = spec.commandLine().getErr();
    try (BufferedReader input =
        new BufferedReader(
            new InputStreamReader(
                new FileInputStream(FileDescriptor.in), StandardCharsets.UTF_8))) {
      // channel 是只读、转发、打印的 CLI 渠道。
      CliChannel channel =
          new CliChannel(
              agentService,
              sessionManager,
              currentUser(),
              profileName,
              input,
              out,
              err,
              CHANNEL_MESSAGES);
      return channel.run();
    } catch (IOException exception) {
      return printError(CliMessages.get("cli.chat.inputReadError", exception.getMessage()));
    }
  }

  /**
   * 返回当前系统用户名；缺失时使用 local。
   *
   * @return 用户标识
   */
  private static String currentUser() {
    // user 是操作系统提供的登录名。
    String user = System.getProperty("user.name", DEFAULT_USER);
    return user == null || user.isBlank() ? DEFAULT_USER : user;
  }

  /**
   * 输出错误并返回失败退出码。
   *
   * @param message 错误信息
   * @return 退出码 1
   */
  private int printError(String message) {
    spec.commandLine().getErr().println(CliMessages.errorPrefix() + message);
    return 1;
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
