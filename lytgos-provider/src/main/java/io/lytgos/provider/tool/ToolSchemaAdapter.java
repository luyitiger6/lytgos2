package io.lytgos.provider.tool;

import io.lytgos.core.tool.LytgTool;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * 把 LytgOS 的 Tool 定义翻译成 Spring AI 的 ToolCallback。
 *
 * <p>这里只翻译 schema，不执行工具；{@link ToolCallback#call(String)} 会直接拒绝执行， 工具调度权归后续 ReActLoop +
 * ToolExecutor。
 *
 * @author LytgOS2 Team
 */
public final class ToolSchemaAdapter {

  /**
   * 把工具定义翻译成 Spring AI 回调列表。
   *
   * @param tools 可用工具；可以为空
   * @return 只含 schema 的 ToolCallback 列表；不会返回 null
   */
  public List<ToolCallback> toToolCallbacks(List<LytgTool> tools) {
    if (tools == null || tools.isEmpty()) {
      return List.of();
    }
    // callbacks 保存本次翻译结果，顺序与输入工具一致。
    List<ToolCallback> callbacks = new ArrayList<>(tools.size());
    for (LytgTool tool : tools) {
      // definition 只包含 name/description/inputSchema，不包含执行逻辑。
      ToolDefinition definition =
          DefaultToolDefinition.builder()
              .name(tool.name())
              .description(tool.description())
              .inputSchema(tool.inputSchema())
              .build();
      callbacks.add(new SchemaOnlyToolCallback(definition));
    }
    return List.copyOf(callbacks);
  }

  /**
   * 只暴露 schema 的 ToolCallback；任何执行请求都会被拒绝。
   *
   * @author LytgOS2 Team
   */
  private static final class SchemaOnlyToolCallback implements ToolCallback {

    /** 工具定义。 */
    private final ToolDefinition definition;

    /**
     * 创建只读回调。
     *
     * @param definition 工具定义
     */
    private SchemaOnlyToolCallback(ToolDefinition definition) {
      this.definition = definition;
    }

    @Override
    public ToolDefinition getToolDefinition() {
      return definition;
    }

    @Override
    public String call(String toolInput) {
      // 工具输入被明确拒绝，确保 Provider 层不会意外执行工具。
      throw new UnsupportedOperationException("工具执行归 ReAct/ToolExecutor: " + definition.name());
    }
  }
}
