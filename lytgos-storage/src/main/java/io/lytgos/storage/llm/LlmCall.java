package io.lytgos.storage.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code llm_calls} 表的 JPA 实体。
 *
 * <p>一次模型调用尝试对应一行；成功和失败都写入；只插入，不更新或删除。
 *
 * @author LytgOS2 Team
 */
@Entity
@Table(name = "llm_calls")
public class LlmCall {

  /** 自增主键。 */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 会话标识；无状态调用时为空。 */
  @Column(name = "session_id")
  private String sessionId;

  /** AgentProfile 名称。 */
  @Column(name = "profile_name", nullable = false)
  private String profileName;

  /** provider 名称。 */
  @Column(nullable = false)
  private String provider;

  /** 模型名称。 */
  @Column(nullable = false)
  private String model;

  /** 输入 token 数；provider 未返回时为空。 */
  @Column(name = "prompt_tokens")
  private Integer promptTokens;

  /** 输出 token 数；provider 未返回时为空。 */
  @Column(name = "completion_tokens")
  private Integer completionTokens;

  /** 总 token 数；provider 未返回时为空。 */
  @Column(name = "total_tokens")
  private Integer totalTokens;

  /** 调用耗时，毫秒。 */
  @Column(name = "duration_ms", nullable = false)
  private long durationMs;

  /** 是否成功；数据库中存 0/1。 */
  @Column(nullable = false)
  private boolean success;

  /** 失败原因；成功时为空。 */
  @Column(name = "error_message")
  private String errorMessage;

  /** ISO-8601 创建时间。 */
  @Column(name = "created_at", nullable = false)
  private String createdAt;

  /** JPA 需要的无参构造器。 */
  protected LlmCall() {}

  /**
   * 创建一条待保存的调用记录。
   *
   * @param sessionId 会话标识，可空
   * @param profileName AgentProfile 名称
   * @param provider provider 名称
   * @param model 模型名称
   * @param promptTokens 输入 token 数，可空
   * @param completionTokens 输出 token 数，可空
   * @param totalTokens 总 token 数，可空
   * @param durationMs 调用耗时，毫秒
   * @param success 是否成功
   * @param errorMessage 失败原因，可空
   * @param createdAt ISO-8601 创建时间
   */
  public LlmCall(
      String sessionId,
      String profileName,
      String provider,
      String model,
      Integer promptTokens,
      Integer completionTokens,
      Integer totalTokens,
      long durationMs,
      boolean success,
      String errorMessage,
      String createdAt) {
    // 逐个赋值，保持实体与 data-model.md §3 的列一一对应。
    this.sessionId = sessionId;
    this.profileName = profileName;
    this.provider = provider;
    this.model = model;
    this.promptTokens = promptTokens;
    this.completionTokens = completionTokens;
    this.totalTokens = totalTokens;
    this.durationMs = durationMs;
    this.success = success;
    this.errorMessage = errorMessage;
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
   * 返回 AgentProfile 名称。
   *
   * @return AgentProfile 名称
   */
  public String getProfileName() {
    return profileName;
  }

  /**
   * 返回 provider 名称。
   *
   * @return provider 名称
   */
  public String getProvider() {
    return provider;
  }

  /**
   * 返回模型名称。
   *
   * @return 模型名称
   */
  public String getModel() {
    return model;
  }

  /**
   * 返回输入 token 数。
   *
   * @return 输入 token 数，可空
   */
  public Integer getPromptTokens() {
    return promptTokens;
  }

  /**
   * 返回输出 token 数。
   *
   * @return 输出 token 数，可空
   */
  public Integer getCompletionTokens() {
    return completionTokens;
  }

  /**
   * 返回总 token 数。
   *
   * @return 总 token 数，可空
   */
  public Integer getTotalTokens() {
    return totalTokens;
  }

  /**
   * 返回调用耗时。
   *
   * @return 毫秒
   */
  public long getDurationMs() {
    return durationMs;
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
   * 返回创建时间。
   *
   * @return ISO-8601 时间
   */
  public String getCreatedAt() {
    return createdAt;
  }
}
