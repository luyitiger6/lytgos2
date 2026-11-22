package io.lytgos.storage.tool;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code tool_invocations} 表的 JPA 实体。
 *
 * <p>一次工具调用尝试对应一行；成功和失败都写入；只插入，不更新或删除。
 *
 * @author LytgOS2 Team
 */
@Entity
@Table(name = "tool_invocations")
public class ToolInvocation {

  /** 自增主键。 */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 会话标识；无状态调用时为空。 */
  @Column(name = "session_id")
  private String sessionId;

  /** 工具名称。 */
  @Column(name = "tool_name", nullable = false)
  private String toolName;

  /** 工具输入 JSON。 */
  @Column(name = "input_json")
  private String inputJson;

  /** 是否成功；数据库中存 0/1。 */
  @Column(nullable = false)
  private boolean success;

  /** 失败原因；成功时为空。 */
  @Column(name = "error_message")
  private String errorMessage;

  /** 执行耗时，毫秒。 */
  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  /** ISO-8601 创建时间。 */
  @Column(name = "created_at", nullable = false)
  private String createdAt;

  /** JPA 需要的无参构造器。 */
  protected ToolInvocation() {}

  /**
   * 创建一条待保存的工具调用记录。
   *
   * @param sessionId 会话标识，可空
   * @param toolName 工具名称
   * @param inputJson 工具输入 JSON，可空
   * @param success 是否成功
   * @param errorMessage 失败原因，可空
   * @param durationMs 执行耗时，毫秒
   * @param createdAt ISO-8601 创建时间
   */
  public ToolInvocation(
      String sessionId,
      String toolName,
      String inputJson,
      boolean success,
      String errorMessage,
      long durationMs,
      String createdAt) {
    // 逐个赋值，保持实体与 data-model.md §7 的列一一对应。
    this.sessionId = sessionId;
    this.toolName = toolName;
    this.inputJson = inputJson;
    this.success = success;
    this.errorMessage = errorMessage;
    this.durationMs = durationMs;
    this.createdAt = createdAt;
  }

  /**
   * 返回主键。
   *
   * @return 主键
   */
  public Long getId() {
    return id;
  }

  /**
   * 返回会话标识。
   *
   * @return 会话标识
   */
  public String getSessionId() {
    return sessionId;
  }

  /**
   * 返回工具名称。
   *
   * @return 工具名称
   */
  public String getToolName() {
    return toolName;
  }

  /**
   * 返回工具输入。
   *
   * @return 工具输入 JSON
   */
  public String getInputJson() {
    return inputJson;
  }

  /**
   * 返回是否成功。
   *
   * @return 成功为 true
   */
  public boolean isSuccess() {
    return success;
  }

  /**
   * 返回失败原因。
   *
   * @return 失败原因，可空
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  /**
   * 返回执行耗时。
   *
   * @return 毫秒
   */
  public long getDurationMs() {
    return durationMs;
  }

  /**
   * 返回创建时间。
   *
   * @return ISO-8601 时间
   */
  public String getCreatedAt() {
    return createdAt;
  }
}
