package io.lytgos.cli.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 在 Windows 用户环境变量中维护 PATH。
 *
 * <p>只读写 HKCU\Environment，不修改系统 PATH，不要求管理员权限。新终端会读取新的用户 PATH。
 *
 * @author LytgOS2 Team
 */
public final class WindowsUserPathEditor implements UserPathEditor {

  /** 用户环境变量注册表位置。 */
  private static final String ENVIRONMENT_KEY = "HKCU\\Environment";

  /** PATH 变量名。 */
  private static final String PATH_NAME = "Path";

  /** Windows PATH 分隔符。 */
  private static final String PATH_SEPARATOR = ";";

  /** 行分隔符正则。 */
  private static final String LINE_SEPARATOR_REGEX = "\\R";

  /** 注册表命令名。 */
  private static final String REG_COMMAND = "reg";

  /** 进程环境变量 PATH 名。 */
  private static final String PATH_ENV_NAME = "PATH";

  /** 可展开字符串注册表类型。 */
  private static final String REG_EXPAND_SZ = "REG_EXPAND_SZ";

  @Override
  public boolean isOnPath(Path directory) {
    // target 是待比较的规范路径。
    String target = normalize(directory);
    // currentPath 是注册表中的用户 PATH；进程 PATH 也作为补充。
    String currentPath =
        readUserPath() + PATH_SEPARATOR + System.getenv().getOrDefault(PATH_ENV_NAME, "");
    for (String entry : currentPath.split(PATH_SEPARATOR)) {
      if (entry.isBlank()) {
        continue;
      }
      // expanded 是展开 %VAR% 后的 PATH 项。
      String expanded = expandEnvironment(entry.trim());
      if (target.equalsIgnoreCase(normalizeText(expanded))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void addToPath(Path directory) {
    // currentPath 是当前用户 PATH。
    String currentPath = readUserPath();
    // newPath 是追加目标目录后的 PATH。
    String newPath =
        currentPath.isBlank() ? directory.toString() : currentPath + PATH_SEPARATOR + directory;
    writeUserPath(newPath);
  }

  /**
   * 读取 HKCU 用户 PATH。
   *
   * @return PATH 值；不存在时返回空字符串
   */
  private String readUserPath() {
    // output 是 reg query 命令输出。
    String output = run(List.of(REG_COMMAND, "query", ENVIRONMENT_KEY, "/v", PATH_NAME), false);
    for (String line : output.split(LINE_SEPARATOR_REGEX)) {
      // parts 按连续空白拆分，第三段是 PATH 值。
      String[] parts = line.trim().split("\\s{2,}", 3);
      if (parts.length == 3 && PATH_NAME.equalsIgnoreCase(parts[0])) {
        return parts[2].trim();
      }
    }
    return "";
  }

  /**
   * 写入 HKCU 用户 PATH。
   *
   * @param value 新 PATH
   */
  private void writeUserPath(String value) {
    run(
        List.of(
            REG_COMMAND,
            "add",
            ENVIRONMENT_KEY,
            "/v",
            PATH_NAME,
            "/t",
            REG_EXPAND_SZ,
            "/d",
            value,
            "/f"),
        true);
  }

  /**
   * 执行注册表命令。
   *
   * @param command 命令参数
   * @param failOnError 非零退出码是否抛错
   * @return 命令输出
   */
  private static String run(List<String> command, boolean failOnError) {
    try {
      // process 是注册表命令进程。
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      // output 是命令标准输出/错误输出。
      String output;
      try (InputStream input = process.getInputStream()) {
        output = new String(input.readAllBytes(), Charset.defaultCharset());
      }
      // exitCode 是命令退出码。
      int exitCode = process.waitFor();
      if (failOnError && exitCode != 0) {
        throw new IllegalStateException("Registry command failed: " + output.trim());
      }
      return output;
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot run registry command", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Registry command interrupted", exception);
    }
  }

  /**
   * 展开 Windows 环境变量占位符。
   *
   * @param value 原始 PATH 项
   * @return 展开后的文本
   */
  private static String expandEnvironment(String value) {
    // result 保存逐段替换结果。
    StringBuilder result = new StringBuilder();
    int index = 0;
    while (index < value.length()) {
      int start = value.indexOf('%', index);
      if (start < 0) {
        result.append(value.substring(index));
        break;
      }
      int end = value.indexOf('%', start + 1);
      if (end < 0) {
        result.append(value.substring(index));
        break;
      }
      result.append(value, index, start);
      // name 是 %VAR% 中的变量名。
      String name = value.substring(start + 1, end);
      // replacement 是环境变量值；找不到时保留原占位符。
      String replacement = System.getenv(name);
      result.append(replacement == null ? value.substring(start, end + 1) : replacement);
      index = end + 1;
    }
    return result.toString();
  }

  /**
   * 归一化路径文本。
   *
   * @param directory 路径
   * @return 小写规范路径
   */
  private static String normalize(Path directory) {
    return normalizeText(directory.toAbsolutePath().normalize().toString());
  }

  /**
   * 归一化路径字符串。
   *
   * @param value 路径文本
   * @return 小写文本
   */
  private static String normalizeText(String value) {
    return Objects.requireNonNull(value).toLowerCase(Locale.ROOT);
  }
}
