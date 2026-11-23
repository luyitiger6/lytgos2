package io.lytgos.storage.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lytgos.core.llm.AgentMessage;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 会话消息列表与 JSON 文本之间的编解码器。
 *
 * <p>消息历史整体存入 {@code sessions.messages_json}；编解码失败必须抛出异常，不能静默丢失历史。
 *
 * @author LytgOS2 Team
 */
@Component
public final class SessionJsonCodec {

  /** 空消息列表的标准 JSON。 */
  private static final String EMPTY_JSON = "[]";

  /** 消息列表的反序列化类型。 */
  private static final TypeReference<List<AgentMessage>> MESSAGE_LIST_TYPE =
      new TypeReference<>() {};

  /** Jackson 对象映射器；由本类持有，避免使用全局静态实例。 */
  private final ObjectMapper objectMapper;

  /** 创建编解码器。 */
  public SessionJsonCodec() {
    this.objectMapper = new ObjectMapper();
  }

  /**
   * 把消息列表编码为 JSON。
   *
   * @param messages 消息列表，可空
   * @return JSON 数组文本
   */
  public String encode(List<AgentMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return EMPTY_JSON;
    }
    try {
      return objectMapper.writeValueAsString(messages);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("会话消息 JSON 编码失败", exception);
    }
  }

  /**
   * 把 JSON 解码为消息列表。
   *
   * @param json JSON 数组文本，可空
   * @return 消息列表；空白输入返回空列表
   */
  public List<AgentMessage> decode(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      // decoded 是反序列化后的消息列表；JSON null 按空列表处理。
      List<AgentMessage> decoded = objectMapper.readValue(json, MESSAGE_LIST_TYPE);
      return Objects.isNull(decoded) ? List.of() : List.copyOf(decoded);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "会话消息 JSON 无法解析: " + exception.getOriginalMessage(), exception);
    }
  }
}
