package io.lytgos.core.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * 从 {@code .lytgos/profiles/*.yaml} 加载 AgentProfile。
 *
 * <p>加载器使用 SnakeYAML 解析为 Map 后手工映射，避免依赖 SnakeYAML 对 record 构造器的推断； 单文件损坏或校验失败只跳过该文件，不阻断其他
 * AgentProfile。AgentProfile 引用的 provider 必须存在于 全局 provider 配置中，否则该文件加载失败并记录清晰错误。
 *
 * @author LytgOS2 Team
 */
public final class AgentProfileLoader {

  /** 日志记录器。 */
  private static final Logger LOG = LoggerFactory.getLogger(AgentProfileLoader.class);

  /** YAML 文件扩展名。 */
  private static final String YAML_SUFFIX = ".yaml";

  /** 另一种 YAML 文件扩展名。 */
  private static final String YML_SUFFIX = ".yml";

  /** 字段校验错误消息的统一前缀。 */
  private static final String FIELD_PREFIX = "字段 ";

  /** 全局配置中存在的 provider 名称集合。 */
  private final Set<String> availableProviders;

  /** 环境变量解析器；测试可注入受控实现。 */
  private final Function<String, String> environmentResolver;

  /**
   * 创建 AgentProfileLoader。
   *
   * @param availableProviders 全局配置中存在的 provider 名称
   * @param environmentResolver 环境变量解析器；为 null 时使用 {@link System#getenv}
   */
  public AgentProfileLoader(
      Collection<String> availableProviders, Function<String, String> environmentResolver) {
    // 复制 provider 名称集合，避免加载期间被外部修改；重复名称按集合语义去重。
    this.availableProviders =
        availableProviders == null
            ? Set.of()
            : Set.copyOf(
                availableProviders.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .collect(Collectors.toSet()));
    // 默认解析器读取进程环境变量；测试可替换。
    this.environmentResolver = environmentResolver == null ? System::getenv : environmentResolver;
  }

  /**
   * 扫描目录并加载所有 YAML AgentProfile。
   *
   * @param profilesDirectory AgentProfile 目录
   * @return 加载成功的 AgentProfile 列表；目录不存在时返回空列表
   */
  public List<AgentProfile> load(Path profilesDirectory) {
    if (profilesDirectory == null || !Files.isDirectory(profilesDirectory)) {
      LOG.info("AgentProfile 目录不存在，返回空列表: {}", profilesDirectory);
      return List.of();
    }
    // profiles 保存本次加载成功的 AgentProfile，顺序与文件名排序一致。
    List<AgentProfile> profiles = new ArrayList<>();
    // loadedNames 用于检测重复名称，保留首个出现项。
    Set<String> loadedNames = new LinkedHashSet<>();
    try (Stream<Path> files = Files.list(profilesDirectory)) {
      // candidates 是按文件名排序后的候选 YAML 文件，保证重复名称的判断稳定。
      List<Path> candidates =
          files.filter(Files::isRegularFile).filter(this::isYamlFile).sorted().toList();
      for (Path file : candidates) {
        // agentProfile 为单文件加载结果；null 表示该文件失败但已被记录。
        AgentProfile agentProfile = loadSingle(file);
        if (agentProfile == null) {
          continue;
        }
        if (!loadedNames.add(agentProfile.name())) {
          // 重复名称保留首个文件，避免后续文件静默覆盖。
          if (LOG.isErrorEnabled()) {
            LOG.error("AgentProfile 名称重复，保留首个并忽略文件: {} ({})", file, agentProfile.name());
          }
          continue;
        }
        profiles.add(agentProfile);
      }
    } catch (IOException exception) {
      LOG.error("扫描 AgentProfile 目录失败: {}", profilesDirectory, exception);
    }
    return List.copyOf(profiles);
  }

  /**
   * 加载单个 YAML 文件。
   *
   * @param file AgentProfile 文件
   * @return 加载成功的 AgentProfile；失败时返回 null 并记录错误
   */
  private AgentProfile loadSingle(Path file) {
    try (InputStream input = Files.newInputStream(file)) {
      // document 是 SnakeYAML 解析出的原始文档。
      Object document = newYaml().load(input);
      if (!(document instanceof Map)) {
        throw new IllegalArgumentException("AgentProfile 根节点必须是 YAML 映射");
      }
      // rawMap 是未解析占位符的原始映射。
      Map<?, ?> rawMap = (Map<?, ?>) document;
      // resolvedMap 已完成 ${ENV} 占位符替换。
      Map<?, ?> resolvedMap = (Map<?, ?>) resolveValue(rawMap);
      return toProfile(resolvedMap);
    } catch (IOException | RuntimeException exception) {
      // 单文件失败只记录日志，不影响其他 AgentProfile 的加载。
      if (LOG.isErrorEnabled()) {
        LOG.error("加载 AgentProfile 失败，跳过文件 {}: {}", file, exception.getMessage(), exception);
      }
      return null;
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

  /**
   * 递归解析字符串中的环境变量占位符。
   *
   * @param value 原始 YAML 值
   * @return 完成占位符替换的值
   */
  private Object resolveValue(Object value) {
    if (value instanceof String) {
      return resolveString((String) value);
    }
    if (value instanceof Map) {
      // resolvedMap 保持原有键顺序，只替换值中的占位符。
      Map<Object, Object> resolvedMap = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
        resolvedMap.put(entry.getKey(), resolveValue(entry.getValue()));
      }
      return resolvedMap;
    }
    if (value instanceof List) {
      // resolvedList 保持原有列表顺序，逐项替换占位符。
      List<Object> resolvedList = new ArrayList<>();
      for (Object item : (List<?>) value) {
        resolvedList.add(resolveValue(item));
      }
      return resolvedList;
    }
    return value;
  }

  /**
   * 解析单个字符串中的环境变量占位符，支持 {@code ${NAME}} 与 {@code ${NAME:default}}。
   *
   * @param value 原始字符串
   * @return 替换后的字符串
   */
  private String resolveString(String value) {
    // builder 保存替换结果。
    StringBuilder builder = new StringBuilder();
    // searchIndex 是下一段未处理文本的起点。
    int searchIndex = 0;
    while (searchIndex < value.length()) {
      // placeholderStart 是下一个 "${" 的位置。
      int placeholderStart = value.indexOf("${", searchIndex);
      if (placeholderStart < 0) {
        builder.append(value, searchIndex, value.length());
        break;
      }
      // placeholderEnd 是占位符结束的 "}" 位置。
      int placeholderEnd = value.indexOf('}', placeholderStart + 2);
      if (placeholderEnd < 0) {
        builder.append(value, searchIndex, value.length());
        break;
      }
      builder.append(value, searchIndex, placeholderStart);
      // placeholder 是 ${...} 中间的内容。
      String placeholder = value.substring(placeholderStart + 2, placeholderEnd);
      // separatorIndex 是环境变量名与缺省值之间的冒号位置。
      int separatorIndex = placeholder.indexOf(':');
      // variableName 是待解析的环境变量名。
      String variableName;
      // defaultValue 是冒号后的缺省值；没有冒号时为空字符串。
      String defaultValue = "";
      if (separatorIndex < 0) {
        variableName = placeholder;
      } else {
        variableName = placeholder.substring(0, separatorIndex);
        defaultValue = placeholder.substring(separatorIndex + 1);
      }
      if (variableName.isBlank()) {
        throw new IllegalArgumentException("环境变量占位符缺少变量名: " + placeholder);
      }
      // resolvedValue 是环境变量解析器返回的值。
      String resolvedValue = environmentResolver.apply(variableName);
      if (resolvedValue == null) {
        if (separatorIndex < 0) {
          throw new IllegalArgumentException("无法解析环境变量占位符: " + variableName);
        }
        resolvedValue = defaultValue;
      }
      builder.append(resolvedValue);
      searchIndex = placeholderEnd + 1;
    }
    return builder.toString();
  }

  /**
   * 判断文件是否使用 YAML 扩展名。
   *
   * @param file 候选文件
   * @return 是 YAML 文件时返回 true
   */
  private boolean isYamlFile(Path file) {
    // fileNamePath 是文件名部分；根路径没有文件名时直接排除。
    Path fileNamePath = file.getFileName();
    if (fileNamePath == null) {
      return false;
    }
    // fileName 是只包含文件名的字符串。
    String fileName = fileNamePath.toString().toLowerCase(Locale.ROOT);
    return fileName.endsWith(YAML_SUFFIX) || fileName.endsWith(YML_SUFFIX);
  }

  /**
   * 把 YAML 映射转换为 AgentProfile。
   *
   * @param map 已完成占位符替换的 YAML 映射
   * @return AgentProfile 实例
   */
  private AgentProfile toProfile(Map<?, ?> map) {
    // name 是 AgentProfile 的唯一名称。
    String name = requiredString(map, "name");
    // identityMap 是可选的身份信息映射。
    Map<?, ?> identityMap = optionalMap(map, "identity");
    // identity 是身份信息；缺少时保留 null，由 AgentProfile 统一补空对象。
    AgentProfile.Identity identity =
        identityMap == null
            ? null
            : new AgentProfile.Identity(
                optionalString(identityMap, "agent_name"), optionalString(identityMap, "prompt"));
    // providerMap 是必填的 provider 选择映射。
    Map<?, ?> providerMap = requiredMap(map, "provider");
    // providerName 是 AgentProfile 引用的全局 provider 名称。
    String providerName = requiredString(providerMap, "name");
    // model 是本次调用的模型名称。
    String model = requiredString(providerMap, "model");
    if (!availableProviders.contains(providerName)) {
      throw new IllegalArgumentException(
          "AgentProfile " + name + " 引用了全局未配置的 provider: " + providerName);
    }
    // temperature 可空，缺省值由 AgentProfile 补为 0.7。
    Double temperature = optionalDouble(providerMap, "temperature");
    // provider 是映射后的 provider 引用。
    AgentProfile.ProviderRef provider =
        new AgentProfile.ProviderRef(providerName, model, temperature);
    // schedules 是定时任务定义列表。
    List<AgentProfile.ScheduleSpec> schedules = scheduleList(map, "schedules");
    // settingsMap 是可选的运行时设置映射。
    Map<?, ?> settingsMap = optionalMap(map, "settings");
    // settings 是运行时设置；缺少时保留 null，由 AgentProfile 使用默认值。
    AgentProfile.Settings settings =
        settingsMap == null
            ? null
            : new AgentProfile.Settings(
                optionalInteger(settingsMap, "max_iterations"),
                optionalInteger(settingsMap, "max_history_turns"));
    return new AgentProfile(
        name,
        optionalString(map, "description"),
        identity,
        provider,
        stringList(map, "tools"),
        stringList(map, "skills"),
        stringList(map, "mcp_servers"),
        stringList(map, "channels"),
        stringList(map, "notify_channels"),
        schedules,
        stringList(map, "bootstrap"),
        settings);
  }

  /**
   * 读取必填字符串字段。
   *
   * @param map 所在映射
   * @param key 字段名
   * @return 字段值
   */
  private String requiredString(Map<?, ?> map, String key) {
    // value 是字段的原始值。
    String value = optionalString(map, key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("缺少必填字段: " + key);
    }
    return value;
  }

  /**
   * 读取可选字符串字段。
   *
   * @param map 所在映射
   * @param key 字段名
   * @return 字段值；不存在时返回 null
   */
  private String optionalString(Map<?, ?> map, String key) {
    if (map == null) {
      return null;
    }
    // value 是字段的原始值。
    Object value = map.get(key);
    if (value == null) {
      return null;
    }
    if (!(value instanceof String)) {
      throw new IllegalArgumentException(FIELD_PREFIX + key + " 必须是字符串");
    }
    return (String) value;
  }

  /**
   * 读取必填映射字段。
   *
   * @param map 所在映射
   * @param key 字段名
   * @return 子映射
   */
  private Map<?, ?> requiredMap(Map<?, ?> map, String key) {
    // value 是子映射；缺失时直接报错。
    Map<?, ?> value = optionalMap(map, key);
    if (value == null) {
      throw new IllegalArgumentException("缺少必填映射字段: " + key);
    }
    return value;
  }

  /**
   * 读取可选映射字段。
   *
   * <p>返回 null 表示字段不存在；这是可选语义，不能用空 Map 代替。
   *
   * @param map 所在映射
   * @param key 字段名
   * @return 子映射；不存在时返回 null
   */
  @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
  private Map<?, ?> optionalMap(Map<?, ?> map, String key) {
    if (map == null) {
      return null;
    }
    // value 是字段的原始值。
    Object value = map.get(key);
    if (value == null) {
      return null;
    }
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException(FIELD_PREFIX + key + " 必须是映射");
    }
    return (Map<?, ?>) value;
  }

  /**
   * 读取可选浮点字段。
   *
   * @param map 所在映射
   * @param key 字段名
   * @return 字段值；不存在时返回 null
   */
  private Double optionalDouble(Map<?, ?> map, String key) {
    if (map == null) {
      return null;
    }
    // value 是字段的原始值。
    Object value = map.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    if (value instanceof String) {
      try {
        return Double.valueOf((String) value);
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException(FIELD_PREFIX + key + " 必须是数字", exception);
      }
    }
    throw new IllegalArgumentException(FIELD_PREFIX + key + " 必须是数字");
  }

  /**
   * 读取可选整数字段。
   *
   * @param map 所在映射
   * @param key 字段名
   * @return 字段值；不存在时返回 null
   */
  private Integer optionalInteger(Map<?, ?> map, String key) {
    if (map == null) {
      return null;
    }
    // value 是字段的原始值。
    Object value = map.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    if (value instanceof String) {
      try {
        return Integer.valueOf((String) value);
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException(FIELD_PREFIX + key + " 必须是整数", exception);
      }
    }
    throw new IllegalArgumentException(FIELD_PREFIX + key + " 必须是整数");
  }

  /**
   * 读取字符串列表字段。
   *
   * @param map 所在映射
   * @param key 字段名
   * @return 字符串列表；不存在时返回空列表
   */
  private List<String> stringList(Map<?, ?> map, String key) {
    if (map == null) {
      return List.of();
    }
    // value 是字段的原始值。
    Object value = map.get(key);
    if (value == null) {
      return List.of();
    }
    if (!(value instanceof List)) {
      throw new IllegalArgumentException(FIELD_PREFIX + key + " 必须是列表");
    }
    // values 保存逐项校验后的字符串。
    List<String> values = new ArrayList<>();
    for (Object item : (List<?>) value) {
      if (!(item instanceof String)) {
        throw new IllegalArgumentException(FIELD_PREFIX + key + " 的元素必须是字符串");
      }
      values.add((String) item);
    }
    return List.copyOf(values);
  }

  /**
   * 读取定时任务列表字段。
   *
   * @param map 所在映射
   * @param key 字段名
   * @return 定时任务列表；不存在时返回空列表
   */
  private List<AgentProfile.ScheduleSpec> scheduleList(Map<?, ?> map, String key) {
    if (map == null) {
      return List.of();
    }
    // value 是字段的原始值。
    Object value = map.get(key);
    if (value == null) {
      return List.of();
    }
    if (!(value instanceof List)) {
      throw new IllegalArgumentException(FIELD_PREFIX + key + " 必须是列表");
    }
    // schedules 保存逐项映射后的定时任务。
    List<AgentProfile.ScheduleSpec> schedules = new ArrayList<>();
    for (Object item : (List<?>) value) {
      if (!(item instanceof Map)) {
        throw new IllegalArgumentException(FIELD_PREFIX + key + " 的元素必须是映射");
      }
      // scheduleMap 是单个定时任务映射。
      Map<?, ?> scheduleMap = (Map<?, ?>) item;
      schedules.add(
          new AgentProfile.ScheduleSpec(
              optionalString(scheduleMap, "cron"),
              optionalString(scheduleMap, "zone"),
              optionalString(scheduleMap, "message")));
    }
    return List.copyOf(schedules);
  }
}
