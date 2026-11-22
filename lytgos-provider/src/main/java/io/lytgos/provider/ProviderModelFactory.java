package io.lytgos.provider;

import io.micrometer.observation.ObservationRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * 按全局 provider 配置构造显式的 provider 名称到模型映射。
 *
 * <p>本节的具体 provider 为 OpenAI 兼容模型；没有 key 或 base-url 的 provider 不构造模型， 但不会阻断应用启动。AgentProfile
 * 引用到未构造的 provider 时由 ProviderService 给出清晰错误。
 *
 * @author LytgOS2 Team
 */
public final class ProviderModelFactory {

  /** 日志记录器。 */
  private static final Logger LOG = LoggerFactory.getLogger(ProviderModelFactory.class);

  /**
   * 构造 provider 名称到 ChatModel 的不可变映射。
   *
   * @param properties 全局 provider 配置
   * @return provider 名称到模型的映射；没有可用 provider 时返回空 Map
   */
  public Map<String, ChatModel> create(ProviderProperties properties) {
    // models 保存显式映射，禁止后续调用方依赖类型扫描。
    Map<String, ChatModel> models = new LinkedHashMap<>();
    // ToolCallingManager 必须存在，但自动执行由 internalToolExecutionEnabled=false 关闭。
    ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
    // 一次 chat 只调用一次模型，不引入额外重试；该模板无状态，可被所有 provider 共享。
    RetryTemplate retryTemplate = new RetryTemplate();
    retryTemplate.setRetryPolicy(new NeverRetryPolicy());
    // 逐个 provider 构造模型，失败的 provider 只跳过并记录日志。
    for (ProviderProperties.ProviderConfig provider : properties.providers()) {
      // name 是 AgentProfile 引用的键。
      String name = provider.name();
      if (name == null || name.isBlank()) {
        LOG.warn("忽略缺少 name 的 provider 配置");
        continue;
      }
      if (models.containsKey(name)) {
        // 全局 provider 名称重复时保留首个，避免后配置静默覆盖。
        LOG.error("provider 名称重复，保留首个并忽略后续项: {}", name);
        continue;
      }
      // apiKey 为空表示该 provider 当前不可用，不阻断启动。
      String apiKey = provider.apiKey();
      if (apiKey == null || apiKey.isBlank()) {
        LOG.warn("provider {} 缺少 api-key，跳过模型构造", name);
        continue;
      }
      // baseUrl 是 OpenAI 兼容协议的连接地址。
      String baseUrl = provider.baseUrl();
      if (baseUrl == null || baseUrl.isBlank()) {
        LOG.warn("provider {} 缺少 base-url，跳过模型构造", name);
        continue;
      }
      // OpenAiApi 负责具体协议的 HTTP 调用。
      OpenAiApi api = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
      // 默认 options 只作为模型基线；每次调用会覆盖 model/temperature/tools。
      OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder().build();
      // 组装 provider 专属 ChatModel。
      OpenAiChatModel model =
          OpenAiChatModel.builder()
              .openAiApi(api)
              .defaultOptions(defaultOptions)
              .toolCallingManager(toolCallingManager)
              .retryTemplate(retryTemplate)
              .observationRegistry(ObservationRegistry.NOOP)
              .build();
      models.put(name, model);
    }
    // 对外暴露不可变映射，避免调用方修改 provider 集合。
    return Map.copyOf(models);
  }
}
