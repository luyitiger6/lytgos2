package io.lytgos.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.lytgos.core.LytgTool;
import io.lytgos.core.ToolResult;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

  @Test
  void registersAndFindsToolByName() {
    ToolRegistry registry = new ToolRegistry();
    registry.register(new StubTool("echo"));

    assertThat(registry.size()).isEqualTo(1);
    assertThat(registry.find("echo")).map(LytgTool::name).contains("echo");
  }

  @Test
  void returnsEmptyForUnknownTool() {
    ToolRegistry registry = new ToolRegistry();

    assertThat(registry.find("missing")).isEmpty();
  }

  private record StubTool(String name) implements LytgTool {

    @Override
    public String description() {
      return "stub";
    }

    @Override
    public String inputSchema() {
      return "{}";
    }

    @Override
    public ToolResult execute(String inputJson) {
      return ToolResult.ok(inputJson);
    }
  }
}
