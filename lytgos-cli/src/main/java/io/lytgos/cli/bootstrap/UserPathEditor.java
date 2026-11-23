package io.lytgos.cli.bootstrap;

import java.nio.file.Path;

/**
 * 用户级 PATH 编辑策略。
 *
 * @author LytgOS2 Team
 */
public interface UserPathEditor {

  /**
   * 判断目录是否已在用户 PATH 中。
   *
   * @param directory 目录
   * @return 已存在返回 true
   */
  boolean isOnPath(Path directory);

  /**
   * 把目录加入用户 PATH。
   *
   * @param directory 目录
   */
  void addToPath(Path directory);
}
