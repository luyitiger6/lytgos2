package io.lytgos.cli.session;

import io.lytgos.cli.i18n.CliMessages;
import io.lytgos.cli.workspace.WorkspacePaths;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * 列出 SQLite 中的会话摘要。
 *
 * <p>只读查询，不启动 Spring；数据库或 sessions 表不存在时输出空说明。
 *
 * @author LytgOS2 Team
 */
@CommandLine.Command(name = "list", mixinStandardHelpOptions = true, description = "列出会话摘要")
@SuppressWarnings("PMD.CloseResource")
public final class SessionListCommand implements Callable<Integer> {

  /** 只读会话摘要读取器。 */
  private final SessionSummaryReader reader;

  /** Picocli 注入的命令模型。 */
  @CommandLine.Spec private CommandSpec spec;

  /** 按默认工作区数据库创建命令；Picocli 使用此构造器。 */
  public SessionListCommand() {
    this(new SessionSummaryReader(WorkspacePaths.defaultPaths().database()));
  }

  /**
   * 创建会话列表命令。
   *
   * @param reader 只读会话摘要读取器
   */
  public SessionListCommand(SessionSummaryReader reader) {
    this.reader = Objects.requireNonNull(reader, "reader");
  }

  @Override
  public Integer call() {
    try {
      // summaries 是按最后活跃时间倒序排列的会话摘要。
      List<SessionSummary> summaries = reader.list();
      // out 是命令标准输出。
      PrintWriter out = spec.commandLine().getOut();
      if (summaries.isEmpty()) {
        out.println(CliMessages.get("cli.session.list.empty"));
        return 0;
      }
      out.printf(
          "%-40s %-12s %-8s %-12s %-8s %s%n",
          "SESSION ID", "PROFILE", "CHANNEL", "USER", "STATUS", "LAST ACTIVE");
      for (SessionSummary summary : summaries) {
        out.printf(
            "%-40s %-12s %-8s %-12s %-8s %s%n",
            summary.sessionId(),
            summary.profileName(),
            summary.channel(),
            summary.userId(),
            summary.status(),
            summary.lastActiveAt());
      }
      return 0;
    } catch (RuntimeException exception) {
      // 数据库损坏等读取失败返回非零退出码，不打印堆栈。
      spec.commandLine().getErr().println(CliMessages.errorPrefix() + exception.getMessage());
      return 1;
    }
  }
}
