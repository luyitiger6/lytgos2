package io.lytgos.tool;

import io.lytgos.core.OryxTool;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry for built-in Tools, MCP Tools, and {@code @Tool} beans.
 */
public final class ToolRegistry {

    private final Map<String, OryxTool> tools = new ConcurrentHashMap<>();

    public void register(OryxTool tool) {
        Objects.requireNonNull(tool, "tool");
        tools.put(tool.name(), tool);
    }

    public Optional<OryxTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public int size() {
        return tools.size();
    }
}
