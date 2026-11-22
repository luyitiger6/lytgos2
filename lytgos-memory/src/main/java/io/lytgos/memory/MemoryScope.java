package io.lytgos.memory;

/**
 * Long-term memory partitions.
 *
 * @author LytgOS2 Team
 */
public enum MemoryScope {
  /** Core memory, never truncated. */
  CORE,
  /** Archived memory, eligible for retrieval. */
  ARCHIVAL
}
