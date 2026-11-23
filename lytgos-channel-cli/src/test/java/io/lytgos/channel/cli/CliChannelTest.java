package io.lytgos.channel.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.lytgos.core.agent.AgentService;
import io.lytgos.core.session.Session;
import io.lytgos.core.session.SessionManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CLI 渠道的读入、转交、打印与退出测试。
 *
 * @author LytgOS2 Team
 */
class CliChannelTest {

  /** 测试渠道。 */
  private static final String CHANNEL = "cli";

  /** 测试用户。 */
  private static final String USER = "wang";

  /** 测试 AgentProfile。 */
  private static final String PROFILE = "default";

  /** 测试会话标识。 */
  private static final String SESSION_ID = "cli:wang:default";

  @Test
  @DisplayName("打印回复并持续到 /quit")
  void printsReplyAndContinuesUntilQuit() {
    // agentService 是 Agent 入口替身。
    AgentService agentService = mock(AgentService.class);
    // sessionManager 是会话门面替身。
    SessionManager sessionManager = mock(SessionManager.class);
    // session 是测试会话。
    Session session = new Session(SESSION_ID, PROFILE, CHANNEL, USER);
    when(sessionManager.getOrCreate(CHANNEL, USER, PROFILE)).thenReturn(session);
    when(agentService.process(session, "hello")).thenReturn("hi");
    // out 收集标准输出。
    StringWriter out = new StringWriter();
    // err 收集错误输出。
    StringWriter err = new StringWriter();

    // exitCode 是交互循环的退出码。
    int exitCode = runChannel(agentService, sessionManager, "hello\n/quit\n", out, err);

    assertThat(exitCode).isZero();
    assertThat(out.toString()).contains("> ").contains("hi");
    assertThat(err.toString()).isEmpty();
    verify(sessionManager).getOrCreate(CHANNEL, USER, PROFILE);
    verify(agentService).process(session, "hello");
  }

  @Test
  @DisplayName("输入流立即结束时正常退出")
  void exitsOnEndOfInput() {
    // agentService 是 Agent 入口替身。
    AgentService agentService = mock(AgentService.class);
    // sessionManager 是会话门面替身。
    SessionManager sessionManager = mock(SessionManager.class);
    // session 是测试会话。
    Session session = new Session(SESSION_ID, PROFILE, CHANNEL, USER);
    when(sessionManager.getOrCreate(CHANNEL, USER, PROFILE)).thenReturn(session);
    // out 收集标准输出。
    StringWriter out = new StringWriter();
    // err 收集错误输出。
    StringWriter err = new StringWriter();

    // exitCode 是交互循环的退出码。
    int exitCode = runChannel(agentService, sessionManager, "", out, err);

    assertThat(exitCode).isZero();
    assertThat(err.toString()).isEmpty();
    verify(sessionManager).getOrCreate(CHANNEL, USER, PROFILE);
  }

  @Test
  @DisplayName("Agent 处理失败时打印错误并继续")
  void agentFailurePrintsErrorAndContinues() {
    // agentService 是 Agent 入口替身。
    AgentService agentService = mock(AgentService.class);
    // sessionManager 是会话门面替身。
    SessionManager sessionManager = mock(SessionManager.class);
    // session 是测试会话。
    Session session = new Session(SESSION_ID, PROFILE, CHANNEL, USER);
    when(sessionManager.getOrCreate(CHANNEL, USER, PROFILE)).thenReturn(session);
    when(agentService.process(session, "bad")).thenThrow(new IllegalStateException("boom"));
    when(agentService.process(session, "ok")).thenReturn("fine");
    // out 收集标准输出。
    StringWriter out = new StringWriter();
    // err 收集错误输出。
    StringWriter err = new StringWriter();

    // exitCode 是交互循环的退出码；发生过错误应为 1。
    int exitCode = runChannel(agentService, sessionManager, "bad\nok\n/quit\n", out, err);

    assertThat(exitCode).isEqualTo(1);
    assertThat(err.toString()).contains("boom");
    assertThat(out.toString()).contains("fine");
  }

  @Test
  @DisplayName("多轮输入复用同一个会话")
  void usesOneSessionForAllTurns() {
    // agentService 是 Agent 入口替身。
    AgentService agentService = mock(AgentService.class);
    // sessionManager 是会话门面替身。
    SessionManager sessionManager = mock(SessionManager.class);
    // session 是测试会话。
    Session session = new Session(SESSION_ID, PROFILE, CHANNEL, USER);
    when(sessionManager.getOrCreate(CHANNEL, USER, PROFILE)).thenReturn(session);
    when(agentService.process(session, "one")).thenReturn("first");
    when(agentService.process(session, "two")).thenReturn("second");
    // out 收集标准输出。
    StringWriter out = new StringWriter();
    // err 收集错误输出。
    StringWriter err = new StringWriter();

    // exitCode 是交互循环的退出码。
    int exitCode = runChannel(agentService, sessionManager, "one\ntwo\n/quit\n", out, err);

    assertThat(exitCode).isZero();
    verify(sessionManager).getOrCreate(CHANNEL, USER, PROFILE);
    verify(agentService).process(session, "one");
    verify(agentService).process(session, "two");
    assertThat(out.toString()).contains("first").contains("second");
  }

  /**
   * 使用内存输入与输出运行一次 CLI 渠道。
   *
   * @param agentService Agent 入口替身
   * @param sessionManager 会话门面替身
   * @param inputText 输入文本
   * @param out 标准输出收集器
   * @param err 错误输出收集器
   * @return 交互循环退出码
   */
  private static int runChannel(
      AgentService agentService,
      SessionManager sessionManager,
      String inputText,
      StringWriter out,
      StringWriter err) {
    try (BufferedReader input = new BufferedReader(new StringReader(inputText));
        PrintWriter output = new PrintWriter(out, true);
        PrintWriter errorOutput = new PrintWriter(err, true)) {
      // channel 是被测 CLI 渠道。
      CliChannel channel =
          new CliChannel(
              agentService,
              sessionManager,
              USER,
              PROFILE,
              input,
              output,
              errorOutput,
              new TestMessages());
      return channel.run();
    } catch (IOException exception) {
      throw new IllegalStateException("测试输入输出流关闭失败", exception);
    }
  }

  /** 测试用英文文案。 */
  private static final class TestMessages implements CliChannelMessages {

    @Override
    public String inputReadError(String detail) {
      return "Error: Cannot read input: " + detail;
    }

    @Override
    public String agentError(String detail) {
      return "Error: Agent processing failed: " + detail;
    }
  }
}
