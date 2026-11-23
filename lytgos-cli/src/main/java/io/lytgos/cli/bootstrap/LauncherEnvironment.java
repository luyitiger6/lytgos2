package io.lytgos.cli.bootstrap;

import io.lytgos.cli.LytgosCli;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * launcher 安装环境快照，便于测试时替换 home、平台和 PATH 编辑器。
 *
 * @param platform 目标平台
 * @param home 用户主目录
 * @param jarPath 当前 JAR 路径；不是 JAR 时为 null
 * @param pathEditor PATH 编辑器
 * @param installEnabled 是否允许自动安装
 * @author LytgOS2 Team
 */
public record LauncherEnvironment(
    LauncherPlatform platform,
    Path home,
    Path jarPath,
    UserPathEditor pathEditor,
    boolean installEnabled) {

  /** JAR 文件后缀。 */
  private static final String JAR_SUFFIX = ".jar";

  /**
   * 读取当前进程环境。
   *
   * @return 环境快照
   */
  public static LauncherEnvironment detect() {
    // platform 由 os.name 决定。
    LauncherPlatform platform = LauncherPlatform.detect(System.getProperty("os.name", ""));
    // home 是当前用户主目录。
    Path home = Path.of(System.getProperty("user.home"));
    // pathEditor 按平台选择注册表或 shell profile。
    UserPathEditor pathEditor =
        platform == LauncherPlatform.WINDOWS
            ? new WindowsUserPathEditor()
            : new UnixUserPathEditor(home, System.getenv("PATH"));
    return new LauncherEnvironment(
        platform,
        home,
        currentJar(),
        pathEditor,
        !"1".equals(System.getenv("LYTGOS_NO_LAUNCHER_INSTALL")));
  }

  /**
   * 解析当前运行的 JAR 路径。
   *
   * @return JAR 路径；IDE/classes 目录时返回 null
   */
  private static Path currentJar() {
    // classPathJar 优先使用 java.class.path，适用于 java -jar。
    Path classPathJar = jarFromClassPath();
    if (classPathJar != null) {
      return classPathJar;
    }
    // commandJar 适用于部分启动器只设置 sun.java.command 的情况。
    Path commandJar = jarFromCommand();
    if (commandJar != null) {
      return commandJar;
    }
    return jarFromCodeSource();
  }

  /**
   * 从 java.class.path 解析 JAR。
   *
   * @return JAR 路径；类路径不是单个 JAR 时返回 null
   */
  private static Path jarFromClassPath() {
    // classPath 是 JVM 启动类路径。
    String classPath = System.getProperty("java.class.path", "");
    // entries 是类路径条目；java -jar 时通常只有一个 JAR。
    String[] entries = classPath.split(File.pathSeparator);
    return entries.length == 1 ? jarPath(entries[0]) : null;
  }

  /**
   * 从 sun.java.command 解析 JAR。
   *
   * @return JAR 路径；命令不是 JAR 时返回 null
   */
  private static Path jarFromCommand() {
    // command 是 JVM 启动命令。
    String command = System.getProperty("sun.java.command", "");
    if (command.isBlank()) {
      return null;
    }
    // firstToken 是命令的第一个参数。
    String firstToken = command.split("\\s+", 2)[0];
    return jarPath(firstToken);
  }

  /**
   * 从 CodeSource 解析 JAR。
   *
   * @return JAR 路径；不是 JAR 时返回 null
   */
  private static Path jarFromCodeSource() {
    try {
      // location 是当前类的代码来源。
      URL location = LytgosCli.class.getProtectionDomain().getCodeSource().getLocation();
      return jarPath(Path.of(location.toURI()).toString());
    } catch (URISyntaxException | RuntimeException exception) {
      return null;
    }
  }

  /**
   * 校验并规范化 JAR 路径。
   *
   * @param value 候选路径
   * @return JAR 路径；不存在或不是 JAR 时返回 null
   */
  private static Path jarPath(String value) {
    if (value == null || value.isBlank() || !value.endsWith(JAR_SUFFIX)) {
      return null;
    }
    try {
      // path 是候选 JAR 路径。
      Path path = Path.of(value);
      return Files.isRegularFile(path) ? path.toAbsolutePath().normalize() : null;
    } catch (InvalidPathException exception) {
      return null;
    }
  }
}
