package io.lytgos.boot.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 在 Spring 上下文初始化阶段创建工作区目录。
 *
 * <p>SQLite 数据源初始化前必须保证 {@code lytgos.workspace} 目录存在；AgentProfileLoader 也需要 对应的 {@code profiles/}
 * 目录。该初始化器在数据源 Bean 创建之前执行。
 *
 * @author LytgOS2 Team
 */
public class WorkspaceInitialization
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    // 读取工作区配置；默认值与 application.yaml 保持一致。
    String workspace = context.getEnvironment().getProperty("lytgos.workspace", ".lytgos");
    // 工作区根目录，例如 .lytgos/。
    Path workspacePath = Path.of(workspace);
    // AgentProfile 配置目录，例如 .lytgos/profiles/。
    Path profilesPath = workspacePath.resolve("profiles");
    try {
      // 创建目录；已存在时不会报错。
      Files.createDirectories(profilesPath);
    } catch (IOException exception) {
      // 目录创建失败会让后续数据源初始化失去落点，直接阻止启动。
      throw new IllegalStateException("无法创建工作区目录: " + profilesPath, exception);
    }
  }
}
