package io.lytgos.storage.llm;

import io.lytgos.core.audit.LlmCallAuditor;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 基于 JPA 的 LLM 调用审计实现。
 *
 * <p>把 core 的审计记录映射为 {@link LlmCall} 实体并写入 SQLite；不向调用方暴露存储细节。
 *
 * @author LytgOS2 Team
 */
@Component
public class JpaLlmCallAuditor implements LlmCallAuditor {

  /** 审计记录仓库。 */
  private final LlmCallRepository repository;

  /**
   * 创建审计实现。
   *
   * @param repository 审计记录仓库
   */
  public JpaLlmCallAuditor(LlmCallRepository repository) {
    this.repository = repository;
  }

  @Override
  public void record(LlmCallRecord auditRecord) {
    // entity 是本条审计记录对应的持久化实体。
    LlmCall entity =
        new LlmCall(
            auditRecord.sessionId(),
            auditRecord.profileName(),
            auditRecord.provider(),
            auditRecord.model(),
            auditRecord.promptTokens(),
            auditRecord.completionTokens(),
            auditRecord.totalTokens(),
            auditRecord.durationMs(),
            auditRecord.success(),
            auditRecord.errorMessage(),
            Instant.now().toString());
    // 失败会让 ProviderService 按 contracts/provider.md 的约定上抛，不静默吞掉。
    repository.save(entity);
  }
}
