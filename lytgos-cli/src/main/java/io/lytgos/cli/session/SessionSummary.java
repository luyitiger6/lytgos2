package io.lytgos.cli.session;

import java.util.Objects;

/**
 * 会话摘要视图，供 {@code session list} 展示。
 *
 * @param sessionId 会话标识
 * @param profileName AgentProfile 名称
 * @param channel 接入渠道
 * @param userId 用户标识
 * @param status 会话状态
 * @param lastActiveAt 最后活跃时间
 * @author LytgOS2 Team
 */
public record SessionSummary(
    String sessionId,
    String profileName,
    String channel,
    String userId,
    String status,
    String lastActiveAt) {

  public SessionSummary {
    // 所有展示字段都来自非空数据库列，构造时统一校验。
    Objects.requireNonNull(sessionId, "sessionId");
    Objects.requireNonNull(profileName, "profileName");
    Objects.requireNonNull(channel, "channel");
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(lastActiveAt, "lastActiveAt");
  }
}
