package io.lytgos.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.lytgos.core.session.Session;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AgentService 的上下文可见性、异常清理与保存钩子测试。
 *
 * @author LytgOS2 Team
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class AgentServiceTest {

  @AfterEach
  void clearContext() {
    // 防止测试之间残留 ThreadLocal 状态。
    AgentProfileContext.clear();
  }

  @Test
  @DisplayName("处理期间上下文可见，成功后保存钩子被调用且上下文被清理")
  void contextIsVisibleAndSaveHookInvoked() {
    // registry 返回测试 AgentProfile。
    AgentProfileRegistry registry = mock(AgentProfileRegistry.class);
    // reActLoop 在处理期间检查当前上下文。
    ReActLoop reActLoop = mock(ReActLoop.class);
    // sessionSaver 记录保存调用。
    @SuppressWarnings("unchecked")
    Consumer<Session> sessionSaver = mock(Consumer.class);
    // agentProfile 是测试配置。
    AgentProfile agentProfile = agentProfile();
    // session 是测试会话。
    Session session = new Session("session-1", "test-agent", "cli", "user-1");
    when(registry.find("test-agent")).thenReturn(Optional.of(agentProfile));
    when(reActLoop.run(any(Session.class), eq("hi"), eq(agentProfile)))
        .thenAnswer(invocation -> {
          // invocation 是 Mockito 调用上下文。
          assertThat(AgentProfileContext.current()).isSameAs(agentProfile);
          return "reply";
        });
    // service 是被测对象。
    AgentService service = new AgentService(registry, reActLoop, sessionSaver);

    // reply 是处理结果。
    String reply = service.process(session, "hi");

    assertThat(reply).isEqualTo("reply");
    verify(sessionSaver).accept(session);
    assertThat(AgentProfileContext.current()).isNull();
  }

  @Test
  @DisplayName("处理抛异常时上下文仍被清理，保存钩子不被调用")
  void contextIsClearedWhenProcessingThrows() {
    // registry 返回测试 AgentProfile。
    AgentProfileRegistry registry = mock(AgentProfileRegistry.class);
    // reActLoop 抛出业务异常。
    ReActLoop reActLoop = mock(ReActLoop.class);
    // sessionSaver 不应被调用。
    @SuppressWarnings("unchecked")
    Consumer<Session> sessionSaver = mock(Consumer.class);
    // agentProfile 是测试配置。
    AgentProfile agentProfile = agentProfile();
    // session 是测试会话。
    Session session = new Session("session-1", "test-agent", "cli", "user-1");
    when(registry.find("test-agent")).thenReturn(Optional.of(agentProfile));
    when(reActLoop.run(any(Session.class), eq("hi"), eq(agentProfile)))
        .thenThrow(new IllegalStateException("boom"));
    // service 是被测对象。
    AgentService service = new AgentService(registry, reActLoop, sessionSaver);

    assertThatThrownBy(() -> service.process(session, "hi"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
    assertThat(AgentProfileContext.current()).isNull();
    verify(sessionSaver, never()).accept(any());
  }

  @Test
  @DisplayName("AgentProfile 不存在时在进入循环前报错")
  void unknownAgentProfileFailsBeforeLoop() {
    // registry 不包含会话引用的 AgentProfile。
    AgentProfileRegistry registry = mock(AgentProfileRegistry.class);
    // reActLoop 不应被调用。
    ReActLoop reActLoop = mock(ReActLoop.class);
    // sessionSaver 不应被调用。
    @SuppressWarnings("unchecked")
    Consumer<Session> sessionSaver = mock(Consumer.class);
    // session 引用不存在的 AgentProfile。
    Session session = new Session("session-1", "missing", "cli", "user-1");
    when(registry.find("missing")).thenReturn(Optional.empty());
    // service 是被测对象。
    AgentService service = new AgentService(registry, reActLoop, sessionSaver);

    assertThatThrownBy(() -> service.process(session, "hi"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing");
    verifyNoInteractions(reActLoop);
    verifyNoInteractions(sessionSaver);
    assertThat(AgentProfileContext.current()).isNull();
  }

  @Test
  @DisplayName("保存钩子失败时异常上抛且上下文被清理")
  void saveHookFailurePropagatesAndClearsContext() {
    // registry 返回测试 AgentProfile。
    AgentProfileRegistry registry = mock(AgentProfileRegistry.class);
    // reActLoop 返回正常答复。
    ReActLoop reActLoop = mock(ReActLoop.class);
    // sessionSaver 抛出保存异常。
    @SuppressWarnings("unchecked")
    Consumer<Session> sessionSaver = mock(Consumer.class);
    // agentProfile 是测试配置。
    AgentProfile agentProfile = agentProfile();
    // session 是测试会话。
    Session session = new Session("session-1", "test-agent", "cli", "user-1");
    when(registry.find("test-agent")).thenReturn(Optional.of(agentProfile));
    when(reActLoop.run(any(Session.class), eq("hi"), eq(agentProfile))).thenReturn("reply");
    doThrow(new IllegalStateException("save failed")).when(sessionSaver).accept(session);
    // service 是被测对象。
    AgentService service = new AgentService(registry, reActLoop, sessionSaver);

    assertThatThrownBy(() -> service.process(session, "hi"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("save failed");
    assertThat(AgentProfileContext.current()).isNull();
  }

  /**
   * 创建测试 AgentProfile。
   *
   * @return 测试 AgentProfile
   */
  private static AgentProfile agentProfile() {
    // identity 是测试身份。
    AgentProfile.Identity identity = new AgentProfile.Identity("测试助手", "你是测试助手");
    // providerRef 是测试 provider 引用。
    AgentProfile.ProviderRef providerRef =
        new AgentProfile.ProviderRef("deepseek", "deepseek-chat", 0.0D);
    // settings 是测试运行时设置。
    AgentProfile.Settings settings = new AgentProfile.Settings(10, 20);
    return new AgentProfile(
        "test-agent",
        "测试 Agent",
        identity,
        providerRef,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        settings);
  }
}
