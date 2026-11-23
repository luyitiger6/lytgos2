package io.lytgos.cli.session;

import io.lytgos.cli.i18n.CliMessages;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.sqlite.SQLiteConfig;

/**
 * 以只读方式直读 SQLite 的 {@code sessions} 表。
 *
 * <p>轻命令不启动 Spring；数据库或表不存在时返回空结果，绝不隐式建表。
 *
 * @author LytgOS2 Team
 */
public final class SessionSummaryReader {

  /** 判断 sessions 表是否存在的 SQL。 */
  private static final String TABLE_EXISTS_SQL =
      "select count(*) from sqlite_master where type = 'table' and name = 'sessions'";

  /** 会话摘要查询 SQL。 */
  private static final String LIST_SQL =
      "select session_id, profile_name, channel, user_id, status, last_active_at "
          + "from sessions order by last_active_at desc";

  /** 会话计数 SQL。 */
  private static final String COUNT_SQL = "select count(*) from sessions";

  /** SQLite 数据库文件。 */
  private final Path database;

  /**
   * 创建只读会话摘要读取器。
   *
   * @param database SQLite 数据库文件
   */
  public SessionSummaryReader(Path database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  /**
   * 判断数据库和 sessions 表是否已初始化。
   *
   * @return 已初始化返回 true
   */
  public boolean isInitialized() {
    if (!Files.isRegularFile(database)) {
      return false;
    }
    try (Connection connection = openReadOnly();
        PreparedStatement statement = connection.prepareStatement(TABLE_EXISTS_SQL);
        ResultSet resultSet = statement.executeQuery()) {
      return resultSet.next() && resultSet.getInt(1) > 0;
    } catch (SQLException exception) {
      throw new IllegalStateException(
          CliMessages.get("cli.session.list.error", database), exception);
    }
  }

  /**
   * 按最后活跃时间倒序列出会话摘要。
   *
   * @return 会话摘要列表；未初始化时为空列表
   */
  public List<SessionSummary> list() {
    if (!isInitialized()) {
      return List.of();
    }
    try (Connection connection = openReadOnly();
        PreparedStatement statement = connection.prepareStatement(LIST_SQL);
        ResultSet resultSet = statement.executeQuery()) {
      // summaries 保存本次查询结果。
      List<SessionSummary> summaries = new ArrayList<>();
      while (resultSet.next()) {
        summaries.add(
            new SessionSummary(
                resultSet.getString("session_id"),
                resultSet.getString("profile_name"),
                resultSet.getString("channel"),
                resultSet.getString("user_id"),
                resultSet.getString("status"),
                resultSet.getString("last_active_at")));
      }
      return List.copyOf(summaries);
    } catch (SQLException exception) {
      throw new IllegalStateException(
          CliMessages.get("cli.session.list.error", database), exception);
    }
  }

  /**
   * 统计会话数量。
   *
   * @return 会话数量；未初始化时返回 0
   */
  public long count() {
    if (!isInitialized()) {
      return 0L;
    }
    try (Connection connection = openReadOnly();
        PreparedStatement statement = connection.prepareStatement(COUNT_SQL);
        ResultSet resultSet = statement.executeQuery()) {
      return resultSet.next() ? resultSet.getLong(1) : 0L;
    } catch (SQLException exception) {
      throw new IllegalStateException(
          CliMessages.get("cli.session.count.error", database), exception);
    }
  }

  /**
   * 打开只读 SQLite 连接。
   *
   * @return 只读连接
   * @throws SQLException 打开失败
   */
  private Connection openReadOnly() throws SQLException {
    // config 强制只读模式，避免查询命令意外写入数据库。
    SQLiteConfig config = new SQLiteConfig();
    config.setReadOnly(true);
    return config.createConnection("jdbc:sqlite:" + database.toAbsolutePath());
  }
}
