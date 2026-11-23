package io.lytgos.cli.provider;

import java.util.Objects;

/**
 * Provider 的只读展示视图。
 *
 * <p>有意不包含 api-key 字段，从类型层面阻止命令输出凭证。
 *
 * @param name provider 名称，非空
 * @param baseUrl 连接地址，可空
 * @author LytgOS2 Team
 */
public record ProviderView(String name, String baseUrl) {

  public ProviderView {
    // 名称是列表的稳定标识，不能为空。
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) {
      throw new IllegalArgumentException("Provider 名称不能为空");
    }
  }
}
