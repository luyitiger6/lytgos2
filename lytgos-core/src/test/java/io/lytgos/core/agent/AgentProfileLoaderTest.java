package io.lytgos.core.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * AgentProfileLoader 的字段解析、校验与容错测试。
 *
 * @author LytgOS2 Team
 */
class AgentProfileLoaderTest {

  /** 测试用 provider 名称。 */
  private static final String PROVIDER_NAME = "deepseek";

  /** 测试用默认模型名称。 */
  private static final String DEFAULT_MODEL = "deepseek-chat";

  /** YAML 中的 provider 配置段键名。 */
  private static final String PROVIDER_KEY = "provider:";

  /** 合法 AgentProfile 名称。 */
  private static final String VALID_AGENT = "valid-agent";

  /** 每个测试独占的临时目录。 */
  @TempDir private Path tempDir;

  @Test
  @DisplayName("合法 YAML 的全部约定字段都被解析")
  void loadsAllFieldsFromYaml() throws IOException {
    // yaml 是包含全部约定字段的 AgentProfile。
    String yaml =
        String.join(
            "\n",
            "name: ops-agent",
            "description: 运维助手",
            "identity:",
            "  agent_name: 运维小助手",
            "  prompt: 你负责回答运维相关问题",
            PROVIDER_KEY,
            "  name: " + PROVIDER_NAME,
            "  model: " + DEFAULT_MODEL,
            "  temperature: 0.3",
            "tools:",
            "  - http_get",
            "  - shell",
            "skills:",
            "  - daily-report",
            "mcp_servers:",
            "  - filesystem",
            "channels:",
            "  - cli",
            "notify_channels:",
            "  - webhook",
            "schedules:",
            "  - cron: \"0 9 * * *\"",
            "    zone: Asia/Shanghai",
            "    message: 早报",
            "bootstrap:",
            "  - SOUL.md",
            "  - USER.md",
            "settings:",
            "  max_iterations: 5",
            "  max_history_turns: 8",
            "");
    writeFile("ops-agent.yaml", yaml);
    // loader 使用已知 provider 集合与不解析占位符的解析器。
    AgentProfileLoader loader = loader();

    // profiles 是加载结果。
    List<AgentProfile> profiles = loader.load(tempDir);

    assertThat(profiles).hasSize(1);
    // AgentProfile 是唯一加载成功的 AgentProfile。
    AgentProfile agentProfile = profiles.get(0);
    assertThat(agentProfile.name()).isEqualTo("ops-agent");
    assertThat(agentProfile.description()).isEqualTo("运维助手");
    assertThat(agentProfile.identity().agentName()).isEqualTo("运维小助手");
    assertThat(agentProfile.identity().prompt()).isEqualTo("你负责回答运维相关问题");
    assertThat(agentProfile.provider().name()).isEqualTo(PROVIDER_NAME);
    assertThat(agentProfile.provider().model()).isEqualTo(DEFAULT_MODEL);
    assertThat(agentProfile.provider().temperature()).isEqualTo(0.3D);
    assertThat(agentProfile.tools()).containsExactly("http_get", "shell");
    assertThat(agentProfile.skills()).containsExactly("daily-report");
    assertThat(agentProfile.mcpServers()).containsExactly("filesystem");
    assertThat(agentProfile.channels()).containsExactly("cli");
    assertThat(agentProfile.notifyChannels()).containsExactly("webhook");
    assertThat(agentProfile.schedules()).hasSize(1);
    assertThat(agentProfile.schedules().get(0).cron()).isEqualTo("0 9 * * *");
    assertThat(agentProfile.schedules().get(0).zone()).isEqualTo("Asia/Shanghai");
    assertThat(agentProfile.schedules().get(0).message()).isEqualTo("早报");
    assertThat(agentProfile.bootstrap()).containsExactly("SOUL.md", "USER.md");
    assertThat(agentProfile.settings().maxIterations()).isEqualTo(5);
    assertThat(agentProfile.settings().maxHistoryTurns()).isEqualTo(8);
  }

  @Test
  @DisplayName("未知 provider 与损坏文件只跳过自身，不阻断合法 AgentProfile")
  void invalidFilesDoNotBlockOtherProfiles() throws IOException {
    // validYaml 是合法 AgentProfile。
    String validYaml = minimalProfileYaml(VALID_AGENT, DEFAULT_MODEL);
    // unknownProviderYaml 引用了全局不存在的 provider。
    String unknownProviderYaml =
        String.join(
            "\n",
            "name: broken-agent",
            PROVIDER_KEY,
            "  name: missing-provider",
            "  model: missing-model",
            "");
    // invalidYaml 是语法损坏的 YAML。
    String invalidYaml = "name: [unterminated";
    writeFile("a-valid.yaml", validYaml);
    writeFile("b-unknown-provider.yaml", unknownProviderYaml);
    writeFile("c-invalid.yaml", invalidYaml);
    // loader 使用已知 provider 集合。
    AgentProfileLoader loader = loader();

    // profiles 只包含合法文件。
    List<AgentProfile> profiles = loader.load(tempDir);

    assertThat(profiles).hasSize(1);
    assertThat(profiles.get(0).name()).isEqualTo(VALID_AGENT);
  }

  @Test
  @DisplayName("字符串中的环境变量占位符从注入解析器解析，支持缺省值")
  void resolvesEnvironmentPlaceholders() throws IOException {
    // yaml 在描述和模型字段中使用占位符。
    String yaml =
        String.join(
            "\n",
            "name: env-agent",
            "description: ${PROFILE_DESCRIPTION:默认描述}",
            PROVIDER_KEY,
            "  name: " + PROVIDER_NAME,
            "  model: ${MODEL_NAME}",
            "");
    writeFile("env-agent.yaml", yaml);
    // resolver 只认识 MODEL_NAME。
    MapEnvironmentResolver resolver =
        new MapEnvironmentResolver(Map.of("MODEL_NAME", DEFAULT_MODEL));
    // loader 使用受控解析器。
    AgentProfileLoader loader = new AgentProfileLoader(Set.of(PROVIDER_NAME), resolver);

    // profiles 是解析占位符后的加载结果。
    List<AgentProfile> profiles = loader.load(tempDir);

    assertThat(profiles).hasSize(1);
    assertThat(profiles.get(0).description()).isEqualTo("默认描述");
    assertThat(profiles.get(0).provider().model()).isEqualTo(DEFAULT_MODEL);
  }

  @Test
  @DisplayName("未解析且无缺省值的占位符使该文件加载失败，其他文件继续")
  void unresolvedPlaceholderSkipsOnlyThatFile() throws IOException {
    // validYaml 是不含占位符的合法 AgentProfile。
    String validYaml = minimalProfileYaml(VALID_AGENT, DEFAULT_MODEL);
    // unresolvedYaml 引用未解析的占位符。
    String unresolvedYaml = minimalProfileYaml("unresolved-agent", "${MISSING_ENV}");
    writeFile("a-valid.yaml", validYaml);
    writeFile("b-unresolved.yaml", unresolvedYaml);
    // loader 的解析器不返回 MISSING_ENV。
    AgentProfileLoader loader = loader();

    // profiles 只包含合法文件。
    List<AgentProfile> profiles = loader.load(tempDir);

    assertThat(profiles).hasSize(1);
    assertThat(profiles.get(0).name()).isEqualTo(VALID_AGENT);
  }

  @Test
  @DisplayName("重复 AgentProfile 名称保留文件名排序后的首个")
  void duplicateNamesKeepFirst() throws IOException {
    // firstYaml 是排序靠前的重复名称 AgentProfile。
    String firstYaml = minimalProfileYaml("duplicate-agent", "model-first");
    // secondYaml 是排序靠后的重复名称 AgentProfile。
    String secondYaml = minimalProfileYaml("duplicate-agent", "model-second");
    writeFile("a-first.yaml", firstYaml);
    writeFile("b-second.yaml", secondYaml);
    // loader 扫描目录。
    AgentProfileLoader loader = loader();

    // profiles 只保留首个。
    List<AgentProfile> profiles = loader.load(tempDir);

    assertThat(profiles).hasSize(1);
    assertThat(profiles.get(0).provider().model()).isEqualTo("model-first");
  }

  @Test
  @DisplayName("可选字段缺省时使用默认温度、默认设置和空列表")
  void optionalFieldsUseDefaults() throws IOException {
    // yaml 只包含必填字段。
    String yaml = minimalProfileYaml("minimal-agent", DEFAULT_MODEL);
    writeFile("minimal-agent.yaml", yaml);
    // loader 扫描目录。
    AgentProfileLoader loader = loader();

    // profiles 是加载结果。
    List<AgentProfile> profiles = loader.load(tempDir);

    assertThat(profiles).hasSize(1);
    // AgentProfile 是使用默认值的 AgentProfile。
    AgentProfile agentProfile = profiles.get(0);
    assertThat(agentProfile.provider().temperature()).isEqualTo(0.7D);
    assertThat(agentProfile.settings().maxIterations()).isEqualTo(10);
    assertThat(agentProfile.settings().maxHistoryTurns()).isEqualTo(20);
    assertThat(agentProfile.tools()).isEmpty();
    assertThat(agentProfile.skills()).isEmpty();
    assertThat(agentProfile.mcpServers()).isEmpty();
    assertThat(agentProfile.channels()).isEmpty();
    assertThat(agentProfile.notifyChannels()).isEmpty();
    assertThat(agentProfile.schedules()).isEmpty();
    assertThat(agentProfile.bootstrap()).isEmpty();
  }

  @Test
  @DisplayName("AgentProfile 目录不存在时返回空列表")
  void missingDirectoryReturnsEmptyList() {
    // missingDirectory 是不存在的目录。
    Path missingDirectory = tempDir.resolve("missing");
    // loader 扫描不存在的目录。
    AgentProfileLoader loader = loader();

    assertThat(loader.load(missingDirectory)).isEmpty();
  }

  @Test
  @DisplayName("非 YAML 文件被忽略")
  void nonYamlFilesAreIgnored() throws IOException {
    // yaml 是合法 AgentProfile。
    String yaml = minimalProfileYaml("yaml-agent", DEFAULT_MODEL);
    writeFile("profile.yaml", yaml);
    writeFile("readme.txt", "ignored");
    // loader 扫描目录。
    AgentProfileLoader loader = loader();

    // profiles 只包含 YAML 文件。
    List<AgentProfile> profiles = loader.load(tempDir);

    assertThat(profiles).hasSize(1);
    assertThat(profiles.get(0).name()).isEqualTo("yaml-agent");
  }

  /**
   * 构造使用已知 provider 集合的加载器。
   *
   * @return AgentProfileLoader
   */
  private static AgentProfileLoader loader() {
    return new AgentProfileLoader(Set.of(PROVIDER_NAME), new MapEnvironmentResolver(Map.of()));
  }

  /**
   * 构造只含必填字段的 AgentProfile YAML。
   *
   * @param name AgentProfile 名称
   * @param model 模型名称
   * @return YAML 文本
   */
  private static String minimalProfileYaml(String name, String model) {
    // yaml 是只含必填字段的 AgentProfile 文本。
    String yaml =
        String.join(
            "\n",
            "name: " + name,
            PROVIDER_KEY,
            "  name: " + PROVIDER_NAME,
            "  model: " + model,
            "");
    return yaml;
  }

  /**
   * 写入测试文件。
   *
   * @param fileName 文件名
   * @param content 文件内容
   * @return 写入后的文件路径
   * @throws IOException 写入失败时抛出
   */
  private Path writeFile(String fileName, String content) throws IOException {
    // file 是待写入的文件路径。
    Path file = tempDir.resolve(fileName);
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }

  /**
   * 基于 Map 的受控环境变量解析器。
   *
   * @author LytgOS2 Team
   */
  private static final class MapEnvironmentResolver implements Function<String, String> {

    /** 环境变量名到值的映射。 */
    private final Map<String, String> values;

    /**
     * 创建受控解析器。
     *
     * @param values 环境变量映射
     */
    private MapEnvironmentResolver(Map<String, String> values) {
      this.values = values;
    }

    @Override
    public String apply(String variableName) {
      // variableName 是待解析的环境变量名。
      return values.get(variableName);
    }
  }
}
