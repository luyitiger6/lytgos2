package io.lytgos.core.llm;

import java.util.Objects;

/**
 * 模型请求执行的一次工具调用。
 *
 * @param id 模型提供的调用 ID，用于关联工具结果
 * @param name 工具名称
 * @param arguments 工具输入 JSON
 * @author LytgOS2 Team
 */
public record AgentToolCall(String id, String name, String arguments) {

  public AgentToolCall {
    // id 缺失时使用空字符串，避免下游处理 null。
    id = id == null ? "" : id;
    // 工具名是执行入口的键，必须存在。
    name = Objects.requireNonNull(name, "name");
    // 空输入按空 JSON 对象处理，保证工具能收到合法 JSON。
    arguments = arguments == null || arguments.isBlank() ? "{}" : arguments;
  }
}
