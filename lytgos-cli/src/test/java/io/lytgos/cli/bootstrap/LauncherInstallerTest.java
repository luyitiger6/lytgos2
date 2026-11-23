package io.lytgos.cli.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import io.lytgos.cli.i18n.CliMessages;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * launcher 自动安装测试。
 *
 * @author LytgOS2 Team
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class LauncherInstallerTest {

  /** 临时目录。 */
  @TempDir private Path tempDirectory;

  @BeforeEach
  void useEnglishMessages() {
    // 测试断言英文提示，避免依赖运行终端语言。
    CliMessages.initialize(Locale.ENGLISH);
  }

  @Test
  @DisplayName("首次安装写入脚本和 PATH，第二次幂等")
  void installsOnceAndIsIdempotent() throws IOException {
    // home 是临时用户目录。
    Path home = Files.createDirectories(tempDirectory.resolve("home"));
    // jar 是模拟的 JAR 文件。
    Path jar = createJar(tempDirectory.resolve("app.jar"));
    // editor 记录 PATH 修改。
    RecordingPathEditor editor = new RecordingPathEditor();
    // environment 是 Linux 安装环境。
    LauncherEnvironment environment =
        new LauncherEnvironment(LauncherPlatform.LINUX, home, jar, editor, true);

    // first 是首次安装结果。
    LauncherInstallResult first = LauncherInstaller.ensureInstalled(environment);
    // launcher 是生成的脚本路径。
    Path launcher = home.resolve(".local").resolve("bin").resolve("lytgos");

    assertThat(first.messages()).anyMatch(message -> message.contains("Installed lytgos launcher"));
    assertThat(launcher).isRegularFile();
    assertThat(Files.readString(launcher, StandardCharsets.UTF_8)).contains("app.jar");
    assertThat(editor.added).containsExactly(home.resolve(".local").resolve("bin"));

    // second 是重复安装结果。
    LauncherInstallResult second = LauncherInstaller.ensureInstalled(environment);
    assertThat(second.messages()).isEmpty();
    assertThat(editor.added).hasSize(1);
  }

  @Test
  @DisplayName("JAR 路径变化时更新脚本")
  void updatesWhenJarChanges() throws IOException {
    // home 是临时用户目录。
    Path home = Files.createDirectories(tempDirectory.resolve("home"));
    // firstJar 与 secondJar 是两个不同的 JAR 路径。
    Path firstJar = createJar(tempDirectory.resolve("first.jar"));
    Path secondJar = createJar(tempDirectory.resolve("second.jar"));
    // editor 记录 PATH 修改。
    RecordingPathEditor editor = new RecordingPathEditor();
    LauncherInstaller.ensureInstalled(
        new LauncherEnvironment(LauncherPlatform.LINUX, home, firstJar, editor, true));

    // result 是更新后的结果。
    LauncherInstallResult result =
        LauncherInstaller.ensureInstalled(
            new LauncherEnvironment(LauncherPlatform.LINUX, home, secondJar, editor, true));
    // launcher 是生成的脚本路径。
    Path launcher = home.resolve(".local").resolve("bin").resolve("lytgos");

    assertThat(result.messages()).anyMatch(message -> message.contains("Updated lytgos launcher"));
    assertThat(Files.readString(launcher, StandardCharsets.UTF_8)).contains("second.jar");
  }

  @Test
  @DisplayName("不覆盖非 LytgOS2 创建的脚本")
  void refusesUnmanagedFile() throws IOException {
    // home 是临时用户目录。
    Path home = Files.createDirectories(tempDirectory.resolve("home"));
    // launcher 是已有自定义脚本。
    Path launcher = home.resolve(".local").resolve("bin").resolve("lytgos");
    Files.createDirectories(launcher.getParent());
    Files.writeString(launcher, "custom script", StandardCharsets.UTF_8);
    // jar 是模拟的 JAR 文件。
    Path jar = createJar(tempDirectory.resolve("app.jar"));
    // editor 记录 PATH 修改。
    RecordingPathEditor editor = new RecordingPathEditor();

    // result 是冲突结果。
    LauncherInstallResult result =
        LauncherInstaller.ensureInstalled(
            new LauncherEnvironment(LauncherPlatform.LINUX, home, jar, editor, true));

    assertThat(result.messages()).anyMatch(message -> message.contains("Refusing"));
    assertThat(Files.readString(launcher, StandardCharsets.UTF_8)).isEqualTo("custom script");
    assertThat(editor.added).isEmpty();
  }

  @Test
  @DisplayName("禁用安装或不是 JAR 时跳过")
  void skipsWhenDisabledOrNotJar() throws IOException {
    // home 是临时用户目录。
    Path home = Files.createDirectories(tempDirectory.resolve("home"));
    // jar 是模拟的 JAR 文件。
    Path jar = createJar(tempDirectory.resolve("app.jar"));
    // editor 记录 PATH 修改。
    RecordingPathEditor editor = new RecordingPathEditor();

    assertThat(
            LauncherInstaller.ensureInstalled(
                    new LauncherEnvironment(LauncherPlatform.LINUX, home, jar, editor, false))
                .messages())
        .isEmpty();
    assertThat(
            LauncherInstaller.ensureInstalled(
                    new LauncherEnvironment(LauncherPlatform.LINUX, home, null, editor, true))
                .messages())
        .isEmpty();
    assertThat(home.resolve(".local").resolve("bin")).doesNotExist();
  }

  @Test
  @DisplayName("Windows 平台生成 lytgos.cmd")
  void installsWindowsScript() throws IOException {
    // home 是临时用户目录。
    Path home = Files.createDirectories(tempDirectory.resolve("home"));
    // jar 是模拟的 JAR 文件。
    Path jar = createJar(tempDirectory.resolve("app.jar"));
    // editor 记录 PATH 修改。
    RecordingPathEditor editor = new RecordingPathEditor();

    LauncherInstaller.ensureInstalled(
        new LauncherEnvironment(LauncherPlatform.WINDOWS, home, jar, editor, true));
    // launcher 是生成的 Windows 脚本。
    Path launcher = home.resolve(".local").resolve("bin").resolve("lytgos.cmd");

    assertThat(launcher).isRegularFile();
    assertThat(Files.readString(launcher, StandardCharsets.UTF_8)).contains("chcp 65001");
  }

  /**
   * 创建模拟 JAR 文件。
   *
   * @param file 文件路径
   * @return 文件路径
   * @throws IOException 创建失败
   */
  private static Path createJar(Path file) throws IOException {
    return Files.createFile(file);
  }

  /** 记录 PATH 操作的测试替身。 */
  private static final class RecordingPathEditor implements UserPathEditor {

    /** 是否已在 PATH。 */
    private boolean onPath;

    /** 已追加的目录。 */
    private final List<Path> added = new ArrayList<>();

    @Override
    public boolean isOnPath(Path directory) {
      return onPath;
    }

    @Override
    public void addToPath(Path directory) {
      onPath = true;
      added.add(directory);
    }
  }
}
