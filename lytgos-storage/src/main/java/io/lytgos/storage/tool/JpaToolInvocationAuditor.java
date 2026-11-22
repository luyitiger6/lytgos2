package io.lytgos.storage.tool;

import io.lytgos.core.audit.ToolInvocationAuditor;
import io.lytgos.core.audit.ToolInvocationRecord;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 基于 JPA 的工具调用审计实现。
 *
 * <p>把 core 的审计记录映射为 {@link ToolInvocation} 实体并写入 SQLite。
 *
 * @author LytgOS2 Team
 */
@Component
public class JpaToolInvocationAuditor implements ToolInvocationAuditor {

  /** 工具调用记录仓库。 */
  private final ToolInvocationRepository repository;

  /**
   * 创建审计实现。
   *
   * @param repository 工具调用记录仓库
   */
  public JpaToolInvocationAuditor(ToolInvocationRepository repository) {
    this.repository = repository;
  }

  @Override
  public void record(ToolInvocationRecord auditRecord) {
    // entity 是本条审计记录对应的持久化实体。
    ToolInvocation entity =
        new ToolInvocation(
            auditRecord.sessionId(),
            auditRecord.toolName(),
            auditRecord.inputJson(),
            auditRecord.success(),
            auditRecord.errorMessage(),
            auditRecord.durationMs(),
            Instant.now().toString());
    // 审计失败会让 ToolExecutor 向上抛出，不静默吞掉。
    repository.save(entity);
  }
}
