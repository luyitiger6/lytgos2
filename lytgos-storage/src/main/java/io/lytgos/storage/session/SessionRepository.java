package io.lytgos.storage.session;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code sessions} 表的 Spring Data JPA 仓库。
 *
 * <p>核心阶段只需要按主键查询和保存；会话标识公式与会话历史编解码由 {@link JpaSessionManager} 负责。
 *
 * @author LytgOS2 Team
 */
public interface SessionRepository extends JpaRepository<SessionEntity, String> {}
