package io.lytgos.core.session;

import java.util.Optional;

/**
 * 会话统一门面。
 *
 * <p>所有入口（CLI、Web、定时任务）只提供 channel/user/profile 三元组，会话标识的生成只发生在这里的实现类内部。 同一三元组必须返回同一会话；任一维度不同即不同会话。
 *
 * @author LytgOS2 Team
 */
public interface SessionManager {

  /**
   * 按三元组获取或创建会话。
   *
   * @param channel 接入渠道，非空且不含冒号
   * @param user 用户标识，非空且不含冒号
   * @param profileName AgentProfile 名称，非空且不含冒号
   * @return 已存在或新建的会话
   */
  Session getOrCreate(String channel, String user, String profileName);

  /**
   * 按会话标识查询会话。
   *
   * @param sessionId 会话标识；为空或不存在时返回空
   * @return 会话
   */
  Optional<Session> get(String sessionId);

  /**
   * 保存会话消息与最后活跃时间。
   *
   * @param session 待保存会话
   */
  void save(Session session);
}
