package io.lytgos.cli.bootstrap;

import io.lytgos.cli.i18n.CliMessages;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 首次运行自动安装 lytgos 启动器。
 *
 * <p>只写用户目录和用户级 PATH；安装失败只返回警告，不阻断 CLI。
 *
 * @author LytgOS2 Team
 */
public final class LauncherInstaller {

  /** 用户本地目录名。 */
  private static final String LOCAL_DIR = ".local";

  /** bin 目录名。 */
  private static final String BIN_DIR = "bin";

  /** Windows 启动脚本名。 */
  private static final String WINDOWS_LAUNCHER = "lytgos.cmd";

  /** Unix 启动脚本名。 */
  private static final String UNIX_LAUNCHER = "lytgos";

  /** 工具类不允许实例化。 */
  private LauncherInstaller() {}

  /**
   * 按当前进程环境安装启动器。
   *
   * @return 安装结果
   */
  public static LauncherInstallResult ensureInstalled() {
    return ensureInstalled(LauncherEnvironment.detect());
  }

  /**
   * 按给定环境安装启动器，便于测试。
   *
   * @param environment 安装环境
   * @return 安装结果
   */
  public static LauncherInstallResult ensureInstalled(LauncherEnvironment environment) {
    Objects.requireNonNull(environment, "environment");
    if (!environment.installEnabled() || environment.jarPath() == null) {
      return LauncherInstallResult.empty();
    }
    // messages 收集需要展示给用户的提示。
    List<String> messages = new ArrayList<>();
    try {
      // launcherDir 是用户级 bin 目录。
      Path launcherDir = environment.home().resolve(LOCAL_DIR).resolve(BIN_DIR);
      // launcherFile 是当前平台的启动脚本路径。
      Path launcherFile =
          launcherDir.resolve(
              environment.platform() == LauncherPlatform.WINDOWS
                  ? WINDOWS_LAUNCHER
                  : UNIX_LAUNCHER);
      Files.createDirectories(launcherDir);
      // content 是当前 JAR 对应的最新脚本内容。
      String content = LauncherScriptFactory.render(environment.platform(), environment.jarPath());
      // installed/updated 记录本次文件变化。
      boolean installed = false;
      boolean updated = false;
      if (Files.exists(launcherFile)) {
        // existing 是已有脚本内容。
        String existing = Files.readString(launcherFile, StandardCharsets.UTF_8);
        if (!LauncherScriptFactory.isManaged(environment.platform(), existing)) {
          messages.add(CliMessages.get("cli.launcher.conflict", launcherFile));
          return new LauncherInstallResult(messages);
        }
        if (!existing.equals(content)) {
          Files.writeString(launcherFile, content, StandardCharsets.UTF_8);
          updated = true;
        }
      } else {
        Files.writeString(launcherFile, content, StandardCharsets.UTF_8);
        installed = true;
      }
      if (environment.platform() != LauncherPlatform.WINDOWS) {
        makeExecutable(launcherFile);
      }
      // pathChanged 记录是否修改了用户 PATH。
      boolean pathChanged = false;
      if (!environment.pathEditor().isOnPath(launcherDir)) {
        environment.pathEditor().addToPath(launcherDir);
        pathChanged = true;
      }
      if (installed) {
        messages.add(CliMessages.get("cli.launcher.installed", launcherFile));
      }
      if (updated) {
        messages.add(CliMessages.get("cli.launcher.updated", launcherFile));
      }
      if (pathChanged) {
        messages.add(CliMessages.get("cli.launcher.pathUpdated", launcherDir));
      }
      return new LauncherInstallResult(messages);
    } catch (IOException | RuntimeException exception) {
      messages.add(CliMessages.get("cli.launcher.failed", exception.getMessage()));
      return new LauncherInstallResult(messages);
    }
  }

  /**
   * 给 Unix 脚本添加执行权限。
   *
   * @param launcherFile 启动脚本
   * @throws IOException 读取文件存储失败
   */
  private static void makeExecutable(Path launcherFile) throws IOException {
    if (Files.getFileStore(launcherFile).supportsFileAttributeView(PosixFileAttributeView.class)) {
      try {
        Files.setPosixFilePermissions(launcherFile, PosixFilePermissions.fromString("rwxr-xr-x"));
      } catch (UnsupportedOperationException exception) {
        // 非 POSIX 文件系统无需设置 Unix 权限。
        return;
      }
    }
  }
}
