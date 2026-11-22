package io.lytgos.core;

/**
 * Uniform Tool abstraction shared by built-in Tools, MCP Tools, and {@code @Tool} beans.
 */
public interface OryxTool {

    String name();

    String description();

    String inputSchema();

    ToolResult execute(String inputJson);
}
