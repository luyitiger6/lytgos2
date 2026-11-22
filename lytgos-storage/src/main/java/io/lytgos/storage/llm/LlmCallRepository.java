package io.lytgos.storage.llm;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code llm_calls} 表的 Spring Data JPA 仓库。
 *
 * <p>本节只使用保存能力；审计查询接口留到后续扩展阶段。
 *
 * @author LytgOS2 Team
 */
public interface LlmCallRepository extends JpaRepository<LlmCall, Long> {}
