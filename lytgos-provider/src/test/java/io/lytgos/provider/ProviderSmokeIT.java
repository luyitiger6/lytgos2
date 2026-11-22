package io.lytgos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.lytgos.core.agent.AgentProfile;
import io.lytgos.core.audit.LlmCallAuditor;
import io.lytgos.core.audit.LlmCallAuditor.LlmCallRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * 使用真实 DeepSeek 账号的集成冒烟。
 *
 * <p>默认被 Surefire 的 {@code integration} 组排除；需要设置 {@code DEEPSEEK_API_KEY} 后通过 {@code -Pintegration
 * -Dtest=ProviderSmokeIT} 手动运行。类名沿用课件约定，P3C 驼峰规则不识别 IT 后缀；本类用 {@code @SuppressWarnings("all")} 让
 * P3C 与 PMD 7 两套 suppression 语义同时成立，仅影响该测试类的静态命名检查。
 *
 * @author LytgOS2 Team
 */
@SuppressWarnings("all")
@Tag("integration")
class ProviderSmokeIT {

  @Test
  @DisplayName("真实 DeepSeek 调用返回非空响应并记录成功审计")
  void realDeepSeekCallReturnsResponseAndAuditsSuccess() {
    // apiKey 是运行环境提供的 DeepSeek 凭证。
    String apiKey = System.getenv("DEEPSEEK_API_KEY");
    assumeFalse(apiKey == null || apiKey.isBlank(), "缺少 DEEPSEEK_API_KEY，跳过真实模型冒烟测试");
    // properties 只包含本次冒烟使用的 deepseek provider。
    ProviderProperties properties =
        new ProviderProperties(
            List.of(
                new ProviderProperties.ProviderConfig(
                    "deepseek", "https://api.deepseek.com", apiKey)));
    // models 是显式 provider 名称到模型的映射。
    Map<String, ChatModel> models = new ProviderModelFactory().create(properties);
    // auditor 记录真实调用的审计数据。
    RecordingAuditor auditor = new RecordingAuditor();
    // service 是被测的 Provider 薄封装。
    ProviderService service = new ProviderService(models, auditor);
    // providerRef 指向真实 DeepSeek 模型。
    AgentProfile.ProviderRef providerRef =
        new AgentProfile.ProviderRef("deepseek", "deepseek-chat", 0.0D);
    // AgentProfile 是本次冒烟使用的 AgentProfile。
    AgentProfile agentProfile =
        new AgentProfile(
            "smoke-agent-profile",
            "冒烟测试",
            null,
            providerRef,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    // response 是真实模型返回的响应。
    ChatResponse response = service.chat(null, agentProfile, new Prompt("请只回复：OK"));

    assertThat(response).isNotNull();
    assertThat(response.getResult().getOutput().getText()).isNotBlank();
    assertThat(auditor.records).hasSize(1);
    assertThat(auditor.records.get(0).success()).isTrue();
    assertThat(auditor.records.get(0).provider()).isEqualTo("deepseek");
    assertThat(auditor.records.get(0).model()).isEqualTo("deepseek-chat");
  }

  /**
   * 收集真实调用审计数据的测试替身。
   *
   * @author LytgOS2 Team
   */
  private static final class RecordingAuditor implements LlmCallAuditor {

    /** 已记录的审计数据。 */
    private final List<LlmCallRecord> records = new ArrayList<>();

    @Override
    public void record(LlmCallRecord record) {
      records.add(record);
    }
  }
}
