package io.lytgos.cli.provider;

import io.lytgos.cli.i18n.CliMessages;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 从 classpath {@code application.yaml} 读取全局 Provider 配置。
 *
 * <p>轻命令不启动 Spring，因此直接解析 YAML；只读取 {@code name} 与 {@code base-url}，忽略 api-key。
 *
 * @author LytgOS2 Team
 */
public final class ProviderConfigReader {

  /** 配置文件资源名。 */
  private static final String CONFIG_RESOURCE = "/application.yaml";

  /**
   * 读取 Provider 列表。
   *
   * @return Provider 视图列表；配置不存在时返回空列表
   */
  public List<ProviderView> read() {
    try (InputStream input = ProviderConfigReader.class.getResourceAsStream(CONFIG_RESOURCE)) {
      if (input == null) {
        return List.of();
      }
      // document 是 application.yaml 的根文档。
      Object document = newYaml().load(input);
      if (!(document instanceof Map)) {
        throw new IllegalStateException(
            CliMessages.get("cli.provider.list.error", CONFIG_RESOURCE));
      }
      // lytgosNode 是 lytgos 配置节。
      Object lytgosNode = ((Map<?, ?>) document).get("lytgos");
      if (!(lytgosNode instanceof Map)) {
        return List.of();
      }
      // providersNode 是 lytgos.providers 列表。
      Object providersNode = ((Map<?, ?>) lytgosNode).get("providers");
      if (!(providersNode instanceof List)) {
        return List.of();
      }
      // views 保存解析出的 Provider 视图。
      List<ProviderView> views = new ArrayList<>();
      for (Object item : (List<?>) providersNode) {
        if (!(item instanceof Map)) {
          continue;
        }
        // providerNode 是单个 Provider 配置映射。
        Map<?, ?> providerNode = (Map<?, ?>) item;
        // nameNode 是 provider 名称节点。
        Object nameNode = providerNode.get("name");
        if (!(nameNode instanceof String) || ((String) nameNode).isBlank()) {
          continue;
        }
        // baseUrlNode 兼容 base-url 与 baseUrl 两种写法。
        Object baseUrlNode = providerNode.get("base-url");
        if (baseUrlNode == null) {
          baseUrlNode = providerNode.get("baseUrl");
        }
        // baseUrl 是可空的连接地址。
        String baseUrl = baseUrlNode instanceof String ? (String) baseUrlNode : null;
        views.add(new ProviderView((String) nameNode, baseUrl));
      }
      return List.copyOf(views);
    } catch (IOException exception) {
      throw new IllegalStateException(
          CliMessages.get("cli.provider.list.error", CONFIG_RESOURCE), exception);
    }
  }

  /**
   * 创建安全的 SnakeYAML 解析器。
   *
   * @return SnakeYAML 实例
   */
  private Yaml newYaml() {
    // loaderOptions 控制解析行为，SafeConstructor 禁止实例化任意 Java 类型。
    LoaderOptions loaderOptions = new LoaderOptions();
    return new Yaml(new SafeConstructor(loaderOptions));
  }
}
