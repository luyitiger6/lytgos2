package io.lytgos.cli.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * launcher 脚本模板测试。
 *
 * @author LytgOS2 Team
 */
class LauncherScriptFactoryTest {

  @Test
  @DisplayName("Windows 脚本包含 UTF-8、JAR 路径和参数透传")
  void rendersWindowsScript() {
    // content 是生成的 Windows 脚本。
    String content =
        LauncherScriptFactory.render(LauncherPlatform.WINDOWS, Path.of("C:\\apps\\lytgos app.jar"));

    assertThat(content).contains("@echo off");
    assertThat(content).contains("chcp 65001");
    assertThat(content).contains("-Dstdout.encoding=UTF-8");
    assertThat(content).contains("lytgos app.jar");
    assertThat(content).contains("%*");
    assertThat(LauncherScriptFactory.isManaged(LauncherPlatform.WINDOWS, content)).isTrue();
  }

  @Test
  @DisplayName("Unix 脚本包含 shebang、exec 和参数透传")
  void rendersUnixScript() {
    // content 是生成的 Unix 脚本。
    String content =
        LauncherScriptFactory.render(LauncherPlatform.LINUX, Path.of("/opt/lytgos app.jar"));

    assertThat(content).startsWith("#!/usr/bin/env sh");
    assertThat(content).contains("exec java");
    assertThat(content).contains("lytgos app.jar");
    assertThat(content).contains("\"$@\"");
    assertThat(LauncherScriptFactory.isManaged(LauncherPlatform.LINUX, content)).isTrue();
  }

  @Test
  @DisplayName("识别早期无标记的 LytgOS2 脚本")
  void recognizesLegacyScript() {
    // legacy 是早期手工脚本片段。
    String legacy = "@echo off\njava -jar lytgos.jar %*";

    assertThat(LauncherScriptFactory.isManaged(LauncherPlatform.WINDOWS, legacy)).isTrue();
    assertThat(LauncherScriptFactory.isManaged(LauncherPlatform.WINDOWS, "custom script"))
        .isFalse();
  }
}
