package io.lytgos.memory;

import java.util.List;

/**
 * Unified memory facade used by the ReAct loop.
 *
 * @author LytgOS2 Team
 */
public interface MemoryService {

  /**
   * Loads long-term memory content.
   *
   * @return long-term memory content
   */
  String loadLongTermMemory();

  /**
   * Appends content to the given memory scope.
   *
   * @param content content to append
   * @param scope target memory scope
   */
  void append(String content, MemoryScope scope);

  /**
   * Recalls memory entries relevant to the query.
   *
   * @param query recall query
   * @return matching memory entries
   */
  List<String> recall(String query);
}
