package io.lytgos.storage.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.lytgos.core.session.Session;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 会话门面的身份公式、幂等、隔离与保存行为测试。
 *
 * <p>使用内存 Map 替身代替 JPA 仓库，只验证门面自身的契约；真实 SQLite 往返由 {@link SessionRepositoryTest} 覆盖。
 *
 * @author LytgOS2 Team
 */
class SessionManagerTest {

  /** 测试渠道。 */
  private static final String CHANNEL = "cli";

  /** 测试用户。 */
  private static final String USER = "wang";

  /** 测试 AgentProfile。 */
  private static final String PROFILE = "default";

  /** 内存仓库替身。 */
  private SessionRepository repository;

  /** 被测会话门面。 */
  private JpaSessionManager sessionManager;

  /** 内存中的实体表。 */
  private Map<String, SessionEntity> storedEntities;

  /** 每个测试重新创建仓库替身和门面。 */
  @BeforeEach
  void setUp() {
    // storedEntities 模拟 sessions 表；预分配容量满足阿里规约。
    storedEntities = new HashMap<>(16);
    // repository 是 Mockito 仓库替身。
    repository = mock(SessionRepository.class);
    when(repository.findById(anyString()))
        .thenAnswer(invocation -> {
          // sessionId 是本次查询的会话标识。
          String sessionId = invocation.getArgument(0);
          return Optional.ofNullable(storedEntities.get(sessionId));
        });
    when(repository.save(any(SessionEntity.class)))
        .thenAnswer(invocation -> {
          // entity 是本次保存的实体。
          SessionEntity entity = invocation.getArgument(0);
          storedEntities.put(entity.getSessionId(), entity);
          return entity;
        });
    // sessionManager 使用真实的 JSON 编解码器。
    sessionManager = new JpaSessionManager(repository, new SessionJsonCodec());
  }

  @Test
  @DisplayName("同一三元组两次 getOrCreate 返回同一个会话")
  void sameTripleReturnsSameSession() {
    // first 是第一次获取的会话。
    Session first = sessionManager.getOrCreate(CHANNEL, USER, PROFILE);
    // second 是第二次获取的会话。
    Session second = sessionManager.getOrCreate(CHANNEL, USER, PROFILE);

    assertThat(first.sessionId()).isEqualTo("cli:wang:default");
    assertThat(second.sessionId()).isEqualTo(first.sessionId());
    assertThat(storedEntities).hasSize(1);
  }

  @Test
  @DisplayName("channel 不同则是不同会话")
  void differentChannelCreatesDifferentSession() {
    // cliSession 是 cli 渠道的会话。
    Session cliSession = sessionManager.getOrCreate(CHANNEL, USER, PROFILE);
    // webSession 是 web 渠道的会话。
    Session webSession = sessionManager.getOrCreate("web", USER, PROFILE);

    assertThat(webSession.sessionId()).isNotEqualTo(cliSession.sessionId());
    assertThat(storedEntities).hasSize(2);
  }

  @Test
  @DisplayName("user 不同则是不同会话")
  void differentUserCreatesDifferentSession() {
    // wangSession 是 wang 用户的会话。
    Session wangSession = sessionManager.getOrCreate(CHANNEL, USER, PROFILE);
    // liSession 是 li 用户的会话。
    Session liSession = sessionManager.getOrCreate(CHANNEL, "li", PROFILE);

    assertThat(liSession.sessionId()).isNotEqualTo(wangSession.sessionId());
    assertThat(storedEntities).hasSize(2);
  }

  @Test
  @DisplayName("profile 不同则是不同会话")
  void differentProfileCreatesDifferentSession() {
    // defaultSession 是 default Profile 的会话。
    Session defaultSession = sessionManager.getOrCreate(CHANNEL, USER, PROFILE);
    // weatherSession 是 weather Profile 的会话。
    Session weatherSession = sessionManager.getOrCreate(CHANNEL, USER, "weather");

    assertThat(weatherSession.sessionId()).isNotEqualTo(defaultSession.sessionId());
    assertThat(storedEntities).hasSize(2);
  }

  @Test
  @DisplayName("空白字段被拒绝")
  void rejectsBlankSegment() {
    assertThatThrownBy(() -> sessionManager.getOrCreate(CHANNEL, " ", PROFILE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("user");
  }

  @Test
  @DisplayName("包含冒号的字段被拒绝，标识只由门面生成")
  void rejectsSegmentContainingColon() {
    assertThatThrownBy(() -> sessionManager.getOrCreate("cli:web", USER, PROFILE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("channel");
  }

  @Test
  @DisplayName("查询不存在或空标识返回空")
  void getUnknownReturnsEmpty() {
    assertThat(sessionManager.get("cli:unknown:default")).isEmpty();
    assertThat(sessionManager.get(null)).isEmpty();
    assertThat(sessionManager.get(" ")).isEmpty();
  }

  @Test
  @DisplayName("保存后再次读取能看到完整消息历史")
  void savePersistsMessages() {
    // session 是新建会话。
    Session session = sessionManager.getOrCreate(CHANNEL, USER, PROFILE);
    session.appendUser("你好");
    sessionManager.save(session);

    // loaded 是从仓库替身重新还原的会话。
    Session loaded = sessionManager.get("cli:wang:default").orElseThrow();

    assertThat(loaded.messageCount()).isEqualTo(1);
    assertThat(loaded.messages().get(0).content()).isEqualTo("你好");
  }

  @Test
  @DisplayName("保存的会话标识必须与三元组一致")
  void rejectsMismatchedSessionId() {
    // session 的三元组与标识不一致。
    Session session = new Session("wrong-id", PROFILE, CHANNEL, USER);

    assertThatThrownBy(() -> sessionManager.save(session))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不一致");
  }
}
