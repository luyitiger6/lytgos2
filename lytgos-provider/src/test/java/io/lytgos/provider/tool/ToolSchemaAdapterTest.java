package io.lytgos.provider.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.lytgos.core.tool.LytgTool;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * ToolSchemaAdapter 的 schema 翻译与执行隔离测试。
 *
 * @author LytgOS2 Team
 */
class ToolSchemaAdapterTest {

  @Test
  @DisplayName("工具 name、description、inputSchema 逐字段翻译到 ToolDefinition")
  void mapsNameDescriptionAndInputSchema() {
    // tool 是带完整元数据的测试工具。
    LytgTool tool = mockTool("weather", "查询天气", "{\"type\":\"object\"}");
    // adapter 是被测适配器。
    ToolSchemaAdapter adapter = new ToolSchemaAdapter();

    // callbacks 是翻译后的工具回调。
    List<ToolCallback> callbacks = adapter.toToolCallbacks(List.of(tool));

    assertThat(callbacks).hasSize(1);
    // definition 是回调暴露的工具定义。
    ToolDefinition definition = callbacks.get(0).getToolDefinition();
    assertThat(definition.name()).isEqualTo("weather");
    assertThat(definition.description()).isEqualTo("查询天气");
    assertThat(definition.inputSchema()).isEqualTo("{\"type\":\"object\"}");
    verify(tool, never()).execute(anyString());
  }

  @Test
  @DisplayName("工具回调的 call 直接拒绝执行，不触发工具执行入口")
  void callIsRejectedAndToolIsNotExecuted() {
    // tool 是带完整元数据的测试工具。
    LytgTool tool = mockTool("shell", "执行命令", "{\"type\":\"object\"}");
    // adapter 是被测适配器。
    ToolSchemaAdapter adapter = new ToolSchemaAdapter();
    // callback 是翻译后的工具回调。
    ToolCallback callback = adapter.toToolCallbacks(List.of(tool)).get(0);

    assertThatThrownBy(() -> callback.call("{\"command\":\"dir\"}"))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("ReAct/ToolExecutor")
        .hasMessageContaining("shell");
    verify(tool, never()).execute(anyString());
  }

  @Test
  @DisplayName("空工具列表返回空列表")
  void emptyToolListReturnsEmptyList() {
    // adapter 是被测适配器。
    ToolSchemaAdapter adapter = new ToolSchemaAdapter();
    assertThat(adapter.toToolCallbacks(List.of())).isEmpty();
  }

  @Test
  @DisplayName("null 工具列表返回空列表")
  void nullToolListReturnsEmptyList() {
    // adapter 是被测适配器。
    ToolSchemaAdapter adapter = new ToolSchemaAdapter();
    assertThat(adapter.toToolCallbacks(null)).isEmpty();
  }

  /**
   * 构造测试用工具替身。
   *
   * @param name 工具名称
   * @param description 工具描述
   * @param inputSchema 输入 JSON Schema
   * @return 工具替身
   */
  private static LytgTool mockTool(String name, String description, String inputSchema) {
    // tool 是 Mockito 生成的工具替身。
    LytgTool tool = mock(LytgTool.class);
    when(tool.name()).thenReturn(name);
    when(tool.description()).thenReturn(description);
    when(tool.inputSchema()).thenReturn(inputSchema);
    return tool;
  }
}
