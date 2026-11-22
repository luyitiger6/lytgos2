package io.lytgos.core;

/** Uniform Tool abstraction shared by built-in Tools, MCP Tools, and {@code @Tool} beans. */
public interface LytgTool {

  String name();

  String description();

  String inputSchema();

  ToolResult execute(String inputJson);
}
