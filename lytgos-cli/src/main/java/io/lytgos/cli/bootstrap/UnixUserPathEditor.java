package io.lytgos.cli.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 在 Unix shell 配置中维护用户级 PATH。
 *
 * <p>只在带标记的块中追加，不修改其他配置；已存在标记时跳过，保证幂等。
 *
 * @author LytgOS2 Team
 */
public final class UnixUserPathEditor implements UserPathEditor {

  /** 标记开始行。 */
  private static final String MARKER_START = "# >>> LytgOS2 CLI >>>";

  /** PATH 分隔符。 */
  private static final String PATH_SEPARATOR = ":";

  /** PATH 块内容。 */
  private static final String PATH_BLOCK =
      MARKER_START
          + "\n"
          + "case \":$PATH:\" in *\":$HOME/.local/bin:\"*) ;; "
          + "*) export PATH=\"$HOME/.local/bin:$PATH\";; esac\n"
          + "# <<< LytgOS2 CLI <<<\n";

  /** 用户主目录。 */
  private final Path home;

  /** 当前进程 PATH。 */
  private final String pathEnvironment;

  /**
   * 创建 Unix PATH 编辑器。
   *
   * @param home 用户主目录
   * @param pathEnvironment 当前 PATH
   */
  public UnixUserPathEditor(Path home, String pathEnvironment) {
    this.home = Objects.requireNonNull(home, "home");
    this.pathEnvironment = pathEnvironment == null ? "" : pathEnvironment;
  }

  @Override
  public boolean isOnPath(Path directory) {
    // target 是待比较的规范路径。
    String target = directory.toAbsolutePath().normalize().toString();
    for (String entry : pathEnvironment.split(PATH_SEPARATOR)) {
      if (entry.isBlank()) {
        continue;
      }
      if (target.equals(Path.of(entry).toAbsolutePath().normalize().toString())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void addToPath(Path directory) {
    // profiles 是需要检查的 shell 配置文件。
    List<Path> profiles = profileFiles();
    for (Path profile : profiles) {
      appendIfNeeded(profile);
    }
  }

  /**
   * 返回需要维护的 profile 文件。
   *
   * @return profile 文件列表
   */
  private List<Path> profileFiles() {
    // candidates 是常见 shell 配置文件。
    List<Path> candidates =
        List.of(
            home.resolve(".profile"),
            home.resolve(".bashrc"),
            home.resolve(".zshrc"),
            home.resolve(".zprofile"));
    // existing 是实际存在的文件。
    List<Path> existing = new ArrayList<>();
    for (Path candidate : candidates) {
      if (Files.isRegularFile(candidate)) {
        existing.add(candidate);
      }
    }
    if (existing.isEmpty()) {
      existing.add(home.resolve(".profile"));
    }
    return existing;
  }

  /**
   * 向单个 profile 追加 PATH 块。
   *
   * @param profile profile 文件
   */
  private static void appendIfNeeded(Path profile) {
    try {
      // content 是现有内容，用于判断是否已经存在标记。
      String content =
          Files.exists(profile) ? Files.readString(profile, StandardCharsets.UTF_8) : "";
      if (content.contains(MARKER_START)) {
        return;
      }
      // prefix 保证块前有换行。
      String prefix = content.isEmpty() || content.endsWith("\n") ? "" : "\n";
      Files.writeString(
          profile,
          prefix + PATH_BLOCK,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot update shell profile: " + profile, exception);
    }
  }
}
