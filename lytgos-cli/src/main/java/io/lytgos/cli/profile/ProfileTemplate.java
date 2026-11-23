package io.lytgos.cli.profile;

/**
 * AgentProfile YAML 模板。
 *
 * <p>{@code init} 与 {@code profile create} 共用同一份模板，保证默认 provider/model 口径一致。
 *
 * @author LytgOS2 Team
 */
public final class ProfileTemplate {

  /** 工具类不允许实例化。 */
  private ProfileTemplate() {}

  /**
   * 生成默认 AgentProfile YAML。
   *
   * @param name AgentProfile 名称
   * @return YAML 文本
   */
  public static String defaultYaml(String name) {
    return "name: "
        + name
        + "\n"
        + "description: 默认 AgentProfile，由 lytgos init 创建\n"
        + "provider:\n"
        + "  name: deepseek\n"
        + "  model: deepseek-chat\n"
        + "  temperature: 0.7\n"
        + "settings:\n"
        + "  max_iterations: 10\n"
        + "  max_history_turns: 20\n";
  }
}
