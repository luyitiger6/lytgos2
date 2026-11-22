package io.lytgos.storage.tool;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code tool_invocations} 表的 Spring Data JPA 仓库。
 *
 * <p>本节只使用保存能力；审计查询接口留到后续扩展阶段。
 *
 * @author LytgOS2 Team
 */
public interface ToolInvocationRepository extends JpaRepository<ToolInvocation, Long> {}
