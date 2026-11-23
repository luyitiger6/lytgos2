package io.lytgos.cli.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unix shell profile PATH 编辑器测试。
 *
 * @author LytgOS2 Team
 */
class UnixUserPathEditorTest {

  /** 临时目录。 */
  @TempDir private Path tempDirectory;

  @Test
  @DisplayName("追加带标记的 PATH 块且幂等")
  void appendsMarkerBlockOnce() throws IOException {
    // home 是临时用户目录。
    Path home = Files.createDirectories(tempDirectory.resolve("home"));
    // profile 是已有 shell 配置。
    Path profile = home.resolve(".profile");
    Files.writeString(profile, "# existing\n", StandardCharsets.UTF_8);
    // editor 是待测编辑器。
    UnixUserPathEditor editor = new UnixUserPathEditor(home, "/usr/bin");

    editor.addToPath(home.resolve(".local").resolve("bin"));
    editor.addToPath(home.resolve(".local").resolve("bin"));

    // content 是追加后的 profile 内容。
    String content = Files.readString(profile, StandardCharsets.UTF_8);
    assertThat(content).contains("# >>> LytgOS2 CLI >>>");
    assertThat(content.split("# >>> LytgOS2 CLI >>>", -1)).hasSize(2);
  }

  @Test
  @DisplayName("识别已在 PATH 中的目录")
  void detectsExistingPath() {
    // target 是 Unix 风格的待检查目录。
    Path target = Path.of("/home/test/.local/bin");
    // editor 是待测编辑器。
    UnixUserPathEditor editor = new UnixUserPathEditor(Path.of("/home/test"), target + ":/usr/bin");

    assertThat(editor.isOnPath(target)).isTrue();
    assertThat(editor.isOnPath(Path.of("/home/test/other"))).isFalse();
  }

  @Test
  @DisplayName("没有 profile 时创建 .profile")
  void createsProfileWhenMissing() throws IOException {
    // home 是临时用户目录。
    Path home = Files.createDirectories(tempDirectory.resolve("home"));
    // editor 是待测编辑器。
    UnixUserPathEditor editor = new UnixUserPathEditor(home, "");

    editor.addToPath(home.resolve(".local").resolve("bin"));

    assertThat(home.resolve(".profile")).isRegularFile();
  }
}
