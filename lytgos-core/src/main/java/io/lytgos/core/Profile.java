package io.lytgos.core;

import java.util.List;

/** Runtime profile derived from an Agent's {@code AGENT.md} frontmatter. */
public record Profile(String name, String provider, String model, List<String> tools) {

  public Profile {
    tools = tools == null ? List.of() : List.copyOf(tools);
  }
}
