package io.lytgos.tool;

import io.lytgos.core.tool.LytgTool;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry for built-in Tools, MCP Tools, and {@code @Tool} beans.
 *
 * @author LytgOS2 Team
 */
public final class ToolRegistry {

  private final Map<String, LytgTool> tools = new ConcurrentHashMap<>();

  public void register(LytgTool tool) {
    Objects.requireNonNull(tool, "tool");
    tools.put(tool.name(), tool);
  }

  public Optional<LytgTool> find(String name) {
    return Optional.ofNullable(tools.get(name));
  }

  public int size() {
    return tools.size();
  }
}
