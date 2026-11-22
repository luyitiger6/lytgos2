package io.lytgos.core;

/**
 * Uniform Tool abstraction shared by built-in Tools, MCP Tools, and {@code @Tool} beans.
 *
 * @author LytgOS2 Team
 */
public interface LytgTool {

  /**
   * Returns the tool name.
   *
   * @return tool name
   */
  String name();

  /**
   * Returns the tool description.
   *
   * @return tool description
   */
  String description();

  /**
   * Returns the JSON schema describing the tool input.
   *
   * @return JSON schema
   */
  String inputSchema();

  /**
   * Executes the tool.
   *
   * @param inputJson tool input as JSON
   * @return tool execution result
   */
  ToolResult execute(String inputJson);
}
