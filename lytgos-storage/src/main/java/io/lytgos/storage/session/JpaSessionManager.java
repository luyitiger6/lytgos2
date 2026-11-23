package io.lytgos.storage.session;

import io.lytgos.core.llm.AgentMessage;
import io.lytgos.core.session.Session;
import io.lytgos.core.session.SessionManager;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 基于 JPA 的会话门面实现。
 *
 * <p>会话标识的唯一生成点在本类内部，格式为 {@code channel:user:profileName}。保存时整体覆盖 {@code messages_json}
 * 并刷新最后活跃时间；创建时间与会话状态在更新时保持不变。
 *
 * @author LytgOS2 Team
 */
@Component
public final class JpaSessionManager implements SessionManager {

  /** 会话标识分隔符。 */
  private static final String SESSION_ID_SEPARATOR = ":";

  /** 1.0 唯一写入的会话状态。 */
  private static final String STATUS_ACTIVE = "ACTIVE";

  /** 会话仓库。 */
  private final SessionRepository repository;

  /** 消息 JSON 编解码器。 */
  private final SessionJsonCodec codec;

  /**
   * 创建会话门面。
   *
   * @param repository 会话仓库
   * @param codec 消息 JSON 编解码器
   */
  public JpaSessionManager(SessionRepository repository, SessionJsonCodec codec) {
    // 两个协作者都不能为空，否则所有会话操作都会失败。
    this.repository = Objects.requireNonNull(repository, "repository");
    this.codec = Objects.requireNonNull(codec, "codec");
  }

  @Override
  public Session getOrCreate(String channel, String user, String profileName) {
    // 三个字段共同决定会话身份，先校验再生成标识。
    validateSegment("channel", channel);
    validateSegment("user", user);
    validateSegment("profileName", profileName);
    // sessionId 是本三元组唯一对应的会话标识。
    String sessionId = buildSessionId(channel, user, profileName);
    // existing 是数据库中已有的会话；存在时直接还原完整历史。
    Optional<SessionEntity> existing = repository.findById(sessionId);
    if (existing.isPresent()) {
      return toSession(existing.get());
    }
    // session 是新建的内存会话，创建后立即落库，保证 session list 能立即看到。
    Session session = new Session(sessionId, profileName, channel, user);
    createNew(session);
    return session;
  }

  @Override
  public Optional<Session> get(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return Optional.empty();
    }
    return repository.findById(sessionId).map(this::toSession);
  }

  @Override
  public void save(Session session) {
    Objects.requireNonNull(session, "session");
    // 保存前再次校验，防止调用方构造出与标识不一致的非法会话。
    validateSegment("channel", session.channel());
    validateSegment("user", session.userId());
    validateSegment("profileName", session.profileName());
    // expectedId 是三字段对应的唯一标识，必须与内存会话一致。
    String expectedId = buildSessionId(session.channel(), session.userId(), session.profileName());
    if (!expectedId.equals(session.sessionId())) {
      throw new IllegalArgumentException("会话标识与三元组不一致: " + session.sessionId());
    }
    // now 是本次保存的统一时间戳。
    String now = Instant.now().toString();
    // entity 是已有记录；不存在时创建新记录。
    SessionEntity entity =
        repository
            .findById(session.sessionId())
            .orElseGet(() ->
                new SessionEntity(
                    session.sessionId(),
                    session.profileName(),
                    session.channel(),
                    session.userId(),
                    codec.encode(List.of()),
                    STATUS_ACTIVE,
                    now,
                    now,
                    null));
    // 消息历史整体覆盖，时间戳刷新；createdAt 与 status 保持原值。
    entity.setMessagesJson(codec.encode(session.messages()));
    entity.setLastActiveAt(now);
    repository.save(entity);
  }

  /**
   * 创建并保存一个新会话。
   *
   * @param session 新会话
   */
  private void createNew(Session session) {
    // now 是创建与首次活跃的统一时间戳。
    String now = Instant.now().toString();
    // entity 是待插入的会话记录，消息历史为空数组。
    SessionEntity entity =
        new SessionEntity(
            session.sessionId(),
            session.profileName(),
            session.channel(),
            session.userId(),
            codec.encode(List.of()),
            STATUS_ACTIVE,
            now,
            now,
            null);
    repository.save(entity);
  }

  /**
   * 把持久化实体还原为领域会话。
   *
   * @param entity 会话实体
   * @return 领域会话
   */
  private Session toSession(SessionEntity entity) {
    // session 的标识与三元组来自数据库，历史由 JSON 解码后逐条追加。
    Session session =
        new Session(
            entity.getSessionId(),
            entity.getProfileName(),
            entity.getChannel(),
            entity.getUserId());
    // messages 是数据库中的完整消息历史。
    List<AgentMessage> messages = codec.decode(entity.getMessagesJson());
    for (AgentMessage message : messages) {
      session.append(message);
    }
    return session;
  }

  /**
   * 生成会话标识。
   *
   * @param channel 接入渠道
   * @param user 用户标识
   * @param profileName AgentProfile 名称
   * @return 会话标识
   */
  private static String buildSessionId(String channel, String user, String profileName) {
    return channel + SESSION_ID_SEPARATOR + user + SESSION_ID_SEPARATOR + profileName;
  }

  /**
   * 校验会话标识字段。
   *
   * @param fieldName 字段名
   * @param value 字段值
   */
  private static void validateSegment(String fieldName, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("会话字段不能为空: " + fieldName);
    }
    if (value.contains(SESSION_ID_SEPARATOR)) {
      throw new IllegalArgumentException("会话字段不能包含冒号: " + fieldName);
    }
  }
}
