package io.lytgos.core.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.lytgos.core.audit.ToolInvocationAuditor;
import io.lytgos.core.audit.ToolInvocationRecord;
import io.lytgos.core.llm.AgentToolCall;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ToolExecutor 的成功、失败、未知工具与异常路径测试。
 *
 * @author LytgOS2 Team
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class ToolExecutorTest {

  @Test
  @DisplayName("工具执行成功时返回成功结果并写成功审计")
  void successWritesSuccessAudit() {
    // auditor 保存工具调用审计。
    RecordingAuditor auditor = new RecordingAuditor();
    // executor 是被测工具执行器。
    ToolExecutor executor =
        new ToolExecutor(Map.of("echo", StubTool.success("echo", "ok")), auditor);
    // call 是测试工具调用。
    AgentToolCall call = new AgentToolCall("call-1", "echo", "{}");

    // result 是工具执行结果。
    ToolResult result = executor.execute("session-1", call);

    assertThat(result.success()).isTrue();
    assertThat(result.content()).isEqualTo("ok");
    assertThat(auditor.records).hasSize(1);
    // record 是成功审计记录。
    ToolInvocationRecord record = auditor.records.get(0);
    assertThat(record.sessionId()).isEqualTo("session-1");
    assertThat(record.toolName()).isEqualTo("echo");
    assertThat(record.inputJson()).isEqualTo("{}");
    assertThat(record.success()).isTrue();
    assertThat(record.errorMessage()).isNull();
    assertThat(record.durationMs()).isGreaterThanOrEqualTo(0L);
  }

  @Test
  @DisplayName("工具返回失败结果时原样返回并写失败审计")
  void failureResultWritesFailureAudit() {
    // auditor 保存工具调用审计。
    RecordingAuditor auditor = new RecordingAuditor();
    // executor 是被测工具执行器。
    ToolExecutor executor =
        new ToolExecutor(Map.of("weather", StubTool.failure("weather", "timeout")), auditor);
    // call 是测试工具调用。
    AgentToolCall call = new AgentToolCall("call-1", "weather", "{}");

    // result 是工具执行结果。
    ToolResult result = executor.execute("session-1", call);

    assertThat(result.success()).isFalse();
    assertThat(result.error()).isEqualTo("timeout");
    assertThat(auditor.records).hasSize(1);
    assertThat(auditor.records.get(0).success()).isFalse();
    assertThat(auditor.records.get(0).errorMessage()).isEqualTo("timeout");
  }

  @Test
  @DisplayName("工具不存在时返回失败结果并写失败审计")
  void unknownToolWritesFailureAudit() {
    // auditor 保存工具调用审计。
    RecordingAuditor auditor = new RecordingAuditor();
    // executor 的工具集合为空。
    ToolExecutor executor = new ToolExecutor(Map.of(), auditor);
    // call 指向未注册工具。
    AgentToolCall call = new AgentToolCall("call-1", "missing", "{}");

    // result 是工具执行结果。
    ToolResult result = executor.execute("session-1", call);

    assertThat(result.success()).isFalse();
    assertThat(result.error()).contains("工具不存在").contains("missing");
    assertThat(auditor.records).hasSize(1);
    assertThat(auditor.records.get(0).success()).isFalse();
    assertThat(auditor.records.get(0).errorMessage()).contains("missing");
  }

  @Test
  @DisplayName("工具抛异常时包装为失败结果并写失败审计，异常不穿透")
  void toolExceptionIsWrappedAndAudited() {
    // auditor 保存工具调用审计。
    RecordingAuditor auditor = new RecordingAuditor();
    // executor 使用会抛异常的工具。
    ToolExecutor executor =
        new ToolExecutor(Map.of("shell", StubTool.throwing("shell", "boom")), auditor);
    // call 是测试工具调用。
    AgentToolCall call = new AgentToolCall("call-1", "shell", "{}");

    // result 是工具执行结果，异常已被包装。
    ToolResult result = executor.execute("session-1", call);

    assertThat(result.success()).isFalse();
    assertThat(result.error()).contains("工具执行失败").contains("boom");
    assertThat(auditor.records).hasSize(1);
    assertThat(auditor.records.get(0).success()).isFalse();
    assertThat(auditor.records.get(0).errorMessage()).contains("boom");
  }

  /**
   * 记录审计数据的测试替身。
   *
   * @author LytgOS2 Team
   */
  private static final class RecordingAuditor implements ToolInvocationAuditor {

    /** 已记录的审计数据。 */
    private final List<ToolInvocationRecord> records = new ArrayList<>();

    @Override
    public void record(ToolInvocationRecord record) {
      records.add(record);
    }
  }

  /**
   * 可控结果的测试工具。
   *
   * @author LytgOS2 Team
   */
  private static final class StubTool implements LytgTool {

    /** 工具名称。 */
    private final String toolName;

    /** 固定返回结果。 */
    private final ToolResult fixedResult;

    /** 非空时执行抛出该异常。 */
    private final RuntimeException executionFailure;

    /**
     * 创建测试工具。
     *
     * @param name 工具名称
     * @param result 固定结果
     * @param failure 执行异常
     */
    private StubTool(String name, ToolResult result, RuntimeException failure) {
      this.toolName = name;
      this.fixedResult = result;
      this.executionFailure = failure;
    }

    /**
     * 创建成功工具。
     *
     * @param name 工具名称
     * @param content 成功内容
     * @return 测试工具
     */
    private static StubTool success(String name, String content) {
      return new StubTool(name, ToolResult.ok(content), null);
    }

    /**
     * 创建返回失败结果的工具。
     *
     * @param name 工具名称
     * @param error 失败原因
     * @return 测试工具
     */
    private static StubTool failure(String name, String error) {
      return new StubTool(name, ToolResult.error(error), null);
    }

    /**
     * 创建执行抛异常的工具。
     *
     * @param name 工具名称
     * @param message 异常消息
     * @return 测试工具
     */
    private static StubTool throwing(String name, String message) {
      return new StubTool(name, null, new IllegalStateException(message));
    }

    @Override
    public String name() {
      return toolName;
    }

    @Override
    public String description() {
      return "stub";
    }

    @Override
    public String inputSchema() {
      return "{}";
    }

    @Override
    public ToolResult execute(String inputJson) {
      if (executionFailure != null) {
        throw executionFailure;
      }
      return fixedResult;
    }
  }
}
