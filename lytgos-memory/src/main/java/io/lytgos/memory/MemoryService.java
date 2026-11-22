package io.lytgos.memory;

import java.util.List;

/** Unified memory facade used by the ReAct loop. */
public interface MemoryService {

  String loadLongTermMemory();

  void append(String content, MemoryScope scope);

  List<String> recall(String query);
}
