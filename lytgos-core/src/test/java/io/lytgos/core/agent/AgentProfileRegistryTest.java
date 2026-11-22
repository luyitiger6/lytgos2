package io.lytgos.core.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AgentProfileRegistry 的内存索引行为测试。
 *
 * @author LytgOS2 Team
 */
class AgentProfileRegistryTest {

  @Test
  @DisplayName("重复名称保留首个 AgentProfile")
  void duplicateNameKeepsFirstProfile() {
    // registry 是被测索引。
    AgentProfileRegistry registry = new AgentProfileRegistry();
    // firstProfile 是首个注册的同名 AgentProfile。
    AgentProfile firstProfile = agentProfile("agent", "model-first");
    // duplicateProfile 是同名的后续 AgentProfile。
    AgentProfile duplicateProfile = agentProfile("agent", "model-second");

    registry.register(firstProfile);
    registry.register(duplicateProfile);

    assertThat(registry.size()).isEqualTo(1);
    assertThat(registry.find("agent")).containsSame(firstProfile);
  }

  @Test
  @DisplayName("按名称查找不存在的 AgentProfile 返回空")
  void findMissingProfileReturnsEmpty() {
    // registry 是被测索引。
    AgentProfileRegistry registry = new AgentProfileRegistry();

    assertThat(registry.find("missing")).isEmpty();
  }

  @Test
  @DisplayName("索引大小反映已注册 AgentProfile 数量")
  void sizeReflectsRegisteredProfiles() {
    // registry 是被测索引。
    AgentProfileRegistry registry = new AgentProfileRegistry();

    registry.register(agentProfile("agent-a", "model-a"));
    registry.register(agentProfile("agent-b", "model-b"));

    assertThat(registry.size()).isEqualTo(2);
    assertThat(registry.find("agent-a")).isPresent();
    assertThat(registry.find("agent-b")).isPresent();
  }

  /**
   * 构造测试 AgentProfile。
   *
   * @param name AgentProfile 名称
   * @param model 模型名称
   * @return 测试 AgentProfile
   */
  private static AgentProfile agentProfile(String name, String model) {
    // providerRef 是测试用 provider 引用。
    AgentProfile.ProviderRef providerRef = new AgentProfile.ProviderRef("deepseek", model, 0.7D);
    return new AgentProfile(
        name, "测试 AgentProfile", null, providerRef, null, null, null, null, null, null, null, null);
  }
}
