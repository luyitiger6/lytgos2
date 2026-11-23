package io.lytgos.cli.profile;

import io.lytgos.cli.i18n.CliMessages;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * AgentProfile 摘要视图，供 {@code profile list} 展示。
 *
 * @param name AgentProfile 名称
 * @param provider provider 名称，可空
 * @param model 模型名称，可空
 * @param path YAML 文件路径
 * @author LytgOS2 Team
 */
public record ProfileSummary(String name, String provider, String model, Path path) {

  public ProfileSummary {
    // 名称与路径是列表的最小展示信息，不能为空。
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(path, "path");
    if (name.isBlank()) {
      throw new IllegalArgumentException("AgentProfile 名称不能为空");
    }
  }

  /**
   * 读取一个 AgentProfile YAML 文件并生成摘要。
   *
   * @param file YAML 文件
   * @return AgentProfile 摘要
   */
  public static ProfileSummary read(Path file) {
    // document 是 YAML 根文档。
    Object document;
    try (InputStream input = Files.newInputStream(file)) {
      document = newYaml().load(input);
    } catch (IOException exception) {
      throw new IllegalStateException(CliMessages.get("cli.profile.show.error", file), exception);
    }
    if (!(document instanceof Map)) {
      throw new IllegalStateException(CliMessages.get("cli.profile.parseError", file));
    }
    // root 是 AgentProfile 根映射。
    Map<?, ?> root = (Map<?, ?>) document;
    // providerName 与 model 来自可选 provider 映射。
    String providerName = providerField(root, "name");
    String model = providerField(root, "model");
    return new ProfileSummary(ProfileNames.nameOf(file), providerName, model, file);
  }

  /**
   * 读取 provider 映射中的字符串字段。
   *
   * @param root AgentProfile 根映射
   * @param field provider 子字段名
   * @return 字段值；provider 或字段缺失时返回 null
   */
  private static String providerField(Map<?, ?> root, String field) {
    // providerNode 是 provider 配置节。
    Object providerNode = root.get("provider");
    if (!(providerNode instanceof Map)) {
      return null;
    }
    // value 是 provider 子字段节点。
    Object value = ((Map<?, ?>) providerNode).get(field);
    return value instanceof String ? (String) value : null;
  }

  /**
   * 创建安全的 SnakeYAML 解析器。
   *
   * @return SnakeYAML 实例
   */
  private static Yaml newYaml() {
    // loaderOptions 控制解析行为，SafeConstructor 禁止实例化任意 Java 类型。
    LoaderOptions loaderOptions = new LoaderOptions();
    return new Yaml(new SafeConstructor(loaderOptions));
  }
}
