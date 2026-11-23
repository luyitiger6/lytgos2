package io.lytgos.cli.profile;

import io.lytgos.cli.i18n.CliMessages;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * AgentProfile 名称校验与受控路径解析。
 *
 * <p>名称只允许字母、数字、下划线和连字符；解析结果必须仍位于 profiles 目录内，防止路径穿越。
 *
 * @author LytgOS2 Team
 */
public final class ProfileNames {

  /** 合法的 AgentProfile 名称模式。 */
  private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

  /** YAML 文件扩展名。 */
  private static final String YAML_SUFFIX = ".yaml";

  /** 另一种 YAML 文件扩展名。 */
  private static final String YML_SUFFIX = ".yml";

  /** 工具类不允许实例化。 */
  private ProfileNames() {}

  /**
   * 判断名称是否合法。
   *
   * @param name AgentProfile 名称
   * @return 合法时返回 true
   */
  public static boolean isValid(String name) {
    return name != null && NAME_PATTERN.matcher(name).matches();
  }

  /**
   * 解析 AgentProfile YAML 文件路径。
   *
   * @param profilesDirectory profiles 目录
   * @param name AgentProfile 名称
   * @return 受控的 YAML 文件路径
   */
  public static Path resolve(Path profilesDirectory, String name) {
    if (!isValid(name)) {
      throw new IllegalArgumentException(CliMessages.get("cli.profile.nameInvalid", name));
    }
    // normalizedDirectory 是 profiles 目录的绝对规范路径。
    Path normalizedDirectory = profilesDirectory.toAbsolutePath().normalize();
    // target 是名称对应的 YAML 文件规范路径。
    Path target = normalizedDirectory.resolve(name + YAML_SUFFIX).normalize();
    if (!normalizedDirectory.equals(target.getParent())) {
      throw new IllegalArgumentException(CliMessages.get("cli.profile.pathEscape", name));
    }
    return target;
  }

  /**
   * 判断文件是否为 YAML。
   *
   * @param file 候选文件
   * @return 是 YAML 文件时返回 true
   */
  public static boolean isYaml(Path file) {
    // fileName 是只包含文件名的字符串。
    String fileName = fileName(file).toLowerCase(Locale.ROOT);
    return fileName.endsWith(YAML_SUFFIX) || fileName.endsWith(YML_SUFFIX);
  }

  /**
   * 从文件名提取 AgentProfile 名称。
   *
   * @param file YAML 文件
   * @return 去扩展名后的名称
   */
  public static String nameOf(Path file) {
    // fileName 是只包含文件名的字符串。
    String fileName = fileName(file);
    if (fileName.endsWith(YAML_SUFFIX)) {
      return fileName.substring(0, fileName.length() - YAML_SUFFIX.length());
    }
    if (fileName.endsWith(YML_SUFFIX)) {
      return fileName.substring(0, fileName.length() - YML_SUFFIX.length());
    }
    return fileName;
  }

  /**
   * 读取文件名并校验存在。
   *
   * @param file 文件路径
   * @return 文件名
   */
  private static String fileName(Path file) {
    Objects.requireNonNull(file, "file");
    // name 是路径的最后一段；根路径没有文件名。
    Path name = file.getFileName();
    if (name == null) {
      throw new IllegalArgumentException("文件路径没有文件名: " + file);
    }
    return name.toString();
  }
}
