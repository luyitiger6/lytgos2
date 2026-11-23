package io.lytgos.storage.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code sessions} 表的 JPA 实体。
 *
 * <p>一条记录对应一个 channel/user/profile 三元组；消息历史整体保存在 {@code messages_json} 一列，核心阶段不按消息拆表。
 *
 * @author LytgOS2 Team
 */
@Entity
@Table(name = "sessions")
public class SessionEntity {

  /** 会话标识，主键；由 JpaSessionManager 按三元组生成。 */
  @Id
  @Column(name = "session_id")
  private String sessionId;

  /** AgentProfile 名称。 */
  @Column(name = "profile_name", nullable = false)
  private String profileName;

  /** 接入渠道。 */
  @Column(name = "channel", nullable = false)
  private String channel;

  /** 用户标识。 */
  @Column(name = "user_id", nullable = false)
  private String userId;

  /** 消息历史 JSON 数组。 */
  @Column(name = "messages_json", nullable = false)
  private String messagesJson;

  /** 会话状态；1.0 只写入 ACTIVE。 */
  @Column(name = "status", nullable = false)
  private String status;

  /** ISO-8601 创建时间。 */
  @Column(name = "created_at", nullable = false)
  private String createdAt;

  /** ISO-8601 最后活跃时间。 */
  @Column(name = "last_active_at", nullable = false)
  private String lastActiveAt;

  /** ISO-8601 归档时间；未归档时为空。 */
  @Column(name = "archived_at")
  private String archivedAt;

  /** JPA 需要的无参构造器。 */
  protected SessionEntity() {}

  /**
   * 创建一条会话记录。
   *
   * @param sessionId 会话标识
   * @param profileName AgentProfile 名称
   * @param channel 接入渠道
   * @param userId 用户标识
   * @param messagesJson 消息历史 JSON
   * @param status 会话状态
   * @param createdAt 创建时间
   * @param lastActiveAt 最后活跃时间
   * @param archivedAt 归档时间，可空
   */
  public SessionEntity(
      String sessionId,
      String profileName,
      String channel,
      String userId,
      String messagesJson,
      String status,
      String createdAt,
      String lastActiveAt,
      String archivedAt) {
    // 逐列赋值，保持实体与 data-model.md §3.1 的列一一对应。
    this.sessionId = sessionId;
    this.profileName = profileName;
    this.channel = channel;
    this.userId = userId;
    this.messagesJson = messagesJson;
    this.status = status;
    this.createdAt = createdAt;
    this.lastActiveAt = lastActiveAt;
    this.archivedAt = archivedAt;
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
   * 返回接入渠道。
   *
   * @return 接入渠道
   */
  public String getChannel() {
    return channel;
  }

  /**
   * 返回用户标识。
   *
   * @return 用户标识
   */
  public String getUserId() {
    return userId;
  }

  /**
   * 返回消息历史 JSON。
   *
   * @return 消息历史 JSON
   */
  public String getMessagesJson() {
    return messagesJson;
  }

  /**
   * 设置消息历史 JSON。
   *
   * @param messagesJson 消息历史 JSON
   */
  public void setMessagesJson(String messagesJson) {
    this.messagesJson = messagesJson;
  }

  /**
   * 返回会话状态。
   *
   * @return 会话状态
   */
  public String getStatus() {
    return status;
  }

  /**
   * 返回创建时间。
   *
   * @return 创建时间
   */
  public String getCreatedAt() {
    return createdAt;
  }

  /**
   * 返回最后活跃时间。
   *
   * @return 最后活跃时间
   */
  public String getLastActiveAt() {
    return lastActiveAt;
  }

  /**
   * 设置最后活跃时间。
   *
   * @param lastActiveAt 最后活跃时间
   */
  public void setLastActiveAt(String lastActiveAt) {
    this.lastActiveAt = lastActiveAt;
  }

  /**
   * 返回归档时间。
   *
   * @return 归档时间，可空
   */
  public String getArchivedAt() {
    return archivedAt;
  }
}
