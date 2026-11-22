package io.lytgos.core.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 加载 Agent 的 Bootstrap 与 Skill 元数据。
 *
 * <p>每次组装 prompt 都重新读取文件，不缓存；显式引用的 Skill 缺失要报错，Bootstrap 缺失至少记录警告。
 *
 * @author LytgOS2 Team
 */
public final class ContextLoader {

  /** 日志记录器。 */
  private static final Logger LOG = LoggerFactory.getLogger(ContextLoader.class);

  /** Skill 描述文件名。 */
  private static final String SKILL_FILE_NAME = "SKILL.md";

  /** Skill 元数据目录名。 */
  private static final String SKILLS_DIRECTORY = "skills";

  /** YAML frontmatter 分隔线。 */
  private static final String FRONTMATTER_DELIMITER = "---";

  /** 工作区根目录的规范化绝对路径。 */
  private final Path workspace;

  /**
   * 创建工作区上下文加载器。
   *
   * @param workspace 工作区根目录，例如 .lytgos
   */
  public ContextLoader(Path workspace) {
    // 工作区路径必须存在配置，统一转成绝对规范化路径用于边界检查。
    this.workspace = Objects.requireNonNull(workspace, "workspace").toAbsolutePath().normalize();
  }

  /**
   * 加载 Bootstrap 与 Skill 元数据。
   *
   * @param agentProfile 当前 AgentProfile
   * @return 拼接后的上下文文本；没有内容时返回空字符串
   */
  public String buildContext(AgentProfile agentProfile) {
    Objects.requireNonNull(agentProfile, "agentProfile");
    // builder 保存按 Bootstrap → Skill 顺序拼接的上下文。
    StringBuilder builder = new StringBuilder();
    appendBootstrap(builder, agentProfile);
    appendSkills(builder, agentProfile);
    return builder.toString();
  }

  /**
   * 追加 Bootstrap 文件内容。
   *
   * @param builder 上下文构建器
   * @param agentProfile 当前 AgentProfile
   */
  private void appendBootstrap(StringBuilder builder, AgentProfile agentProfile) {
    for (String bootstrapName : agentProfile.bootstrap()) {
      // file 是工作区内的 Bootstrap 文件；越界路径直接拒绝。
      Path file = resolveBootstrapPath(bootstrapName);
      if (!Files.isRegularFile(file)) {
        if (LOG.isWarnEnabled()) {
          LOG.warn("Bootstrap 文件不存在，跳过: {}", file);
        }
        continue;
      }
      try {
        // content 是 Bootstrap 文件全文。
        String content = Files.readString(file, StandardCharsets.UTF_8);
        builder
            .append("\n## Bootstrap: ")
            .append(bootstrapName)
            .append('\n')
            .append(content)
            .append('\n');
      } catch (IOException exception) {
        if (LOG.isWarnEnabled()) {
          LOG.warn("Bootstrap 文件读取失败，跳过: {}", file, exception);
        }
      }
    }
  }

  /**
   * 追加 Skill 元数据。
   *
   * @param builder 上下文构建器
   * @param agentProfile 当前 AgentProfile
   */
  private void appendSkills(StringBuilder builder, AgentProfile agentProfile) {
    for (String skillName : agentProfile.skills()) {
      // skillFile 是 Skill 的 SKILL.md；缺失时直接报错，避免人格悄悄丢失。
      Path skillFile = resolveSkillFile(skillName);
      if (!Files.isRegularFile(skillFile)) {
        throw new IllegalArgumentException("Skill 不存在: " + skillName);
      }
      try {
        // content 是 SKILL.md 全文，用于解析 frontmatter。
        String content = Files.readString(skillFile, StandardCharsets.UTF_8);
        // frontmatter 是 SKILL.md 头部的 YAML 元数据。
        Map<?, ?> frontmatter = parseFrontmatter(content, skillFile);
        // resolvedName 优先使用 frontmatter 中的 name，缺失时回退到目录名。
        String resolvedName = optionalString(frontmatter, "name");
        if (resolvedName == null || resolvedName.isBlank()) {
          resolvedName = skillName;
        }
        // description 是 frontmatter 中的描述，缺失时使用空字符串。
        String description = optionalString(frontmatter, "description");
        builder
            .append("\n## Skill: ")
            .append(resolvedName)
            .append("\n- 描述: ")
            .append(description == null || description.isBlank() ? "未提供" : description)
            .append("\n- 路径: ")
            .append(skillFile.toAbsolutePath())
            .append('\n');
      } catch (IOException exception) {
        throw new IllegalArgumentException("读取 Skill 失败: " + skillName, exception);
      }
    }
  }

  /**
   * 解析工作区内的 Bootstrap 路径并校验边界。
   *
   * @param bootstrapName Bootstrap 文件名
   * @return 规范化路径
   */
  private Path resolveBootstrapPath(String bootstrapName) {
    // file 是规范化后的候选路径。
    Path file = workspace.resolve(bootstrapName).normalize();
    if (!file.startsWith(workspace)) {
      throw new IllegalArgumentException("Bootstrap 路径越界: " + bootstrapName);
    }
    return file;
  }

  /**
   * 解析 Skill 文件路径并校验边界。
   *
   * @param skillName Skill 名称
   * @return SKILL.md 路径
   */
  private Path resolveSkillFile(String skillName) {
    // skillsRoot 是工作区下的 Skill 根目录。
    Path skillsRoot = workspace.resolve(SKILLS_DIRECTORY).normalize();
    // skillFile 是规范化后的 SKILL.md 路径。
    Path skillFile = skillsRoot.resolve(skillName).resolve(SKILL_FILE_NAME).normalize();
    if (!skillFile.startsWith(skillsRoot)) {
      throw new IllegalArgumentException("Skill 路径越界: " + skillName);
    }
    return skillFile;
  }

  /**
   * 解析 YAML frontmatter。
   *
   * @param content SKILL.md 全文
   * @param skillFile Skill 文件路径，用于错误信息
   * @return frontmatter 映射；没有 frontmatter 时返回空映射
   */
  private Map<?, ?> parseFrontmatter(String content, Path skillFile) {
    // lines 是 SKILL.md 的逐行内容。
    List<String> lines = content.lines().toList();
    if (lines.isEmpty() || !FRONTMATTER_DELIMITER.equals(lines.get(0).trim())) {
      return Map.of();
    }
    // endIndex 是结束分隔线的下标。
    int endIndex = -1;
    for (int index = 1; index < lines.size(); index++) {
      if (FRONTMATTER_DELIMITER.equals(lines.get(index).trim())) {
        endIndex = index;
        break;
      }
    }
    if (endIndex < 0) {
      throw new IllegalArgumentException("Skill frontmatter 未闭合: " + skillFile);
    }
    // yamlText 是分隔线之间的 YAML 文本。
    String yamlText = String.join("\n", lines.subList(1, endIndex));
    // loaded 是 SnakeYAML 解析结果。
    Object loaded = newYaml().load(yamlText);
    if (!(loaded instanceof Map)) {
      throw new IllegalArgumentException("Skill frontmatter 必须是 YAML 映射: " + skillFile);
    }
    return (Map<?, ?>) loaded;
  }

  /**
   * 创建安全的 YAML 解析器。
   *
   * @return SnakeYAML 实例
   */
  private Yaml newYaml() {
    // loaderOptions 控制解析行为，SafeConstructor 禁止实例化任意 Java 类型。
    LoaderOptions loaderOptions = new LoaderOptions();
    return new Yaml(new SafeConstructor(loaderOptions));
  }

  /**
   * 读取可选字符串字段。
   *
   * @param map 所在映射
   * @param key 字段名
   * @return 字段值；不存在时返回 null
   */
  private String optionalString(Map<?, ?> map, String key) {
    // value 是字段的原始值。
    Object value = map.get(key);
    return value instanceof String ? (String) value : null;
  }
}
