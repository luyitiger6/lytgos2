package io.lytgos.cli.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * CLI 文案资源门面。
 *
 * <p>默认英文，启动时由 {@link CliLocaleResolver} 选择中文或英文资源。命令和渠道统一通过本类取文案， 避免在代码里散落中文字符串。
 *
 * @author LytgOS2 Team
 */
public final class CliMessages {

  /** 资源包基名。 */
  private static final String BUNDLE_NAME = "i18n.cli";

  /** 初始化锁，避免使用类对象作为同步监视器。 */
  private static final Object LOCK = new Object();

  /** 当前资源包；默认英文。 */
  private static volatile ResourceBundle messages =
      ResourceBundle.getBundle(BUNDLE_NAME, Locale.ENGLISH, classLoader());

  /** 当前 locale。 */
  private static volatile Locale currentLocale = Locale.ENGLISH;

  /** 工具类不允许实例化。 */
  private CliMessages() {}

  /**
   * 初始化当前语言。
   *
   * @param locale 目标 locale
   */
  public static void initialize(Locale locale) {
    synchronized (LOCK) {
      currentLocale = locale == null ? Locale.ENGLISH : locale;
      messages = ResourceBundle.getBundle(BUNDLE_NAME, currentLocale, classLoader());
    }
  }

  /**
   * 取文案。
   *
   * @param key 资源键
   * @param args 格式化参数
   * @return 本地化文本；键缺失时返回键名
   */
  public static String get(String key, Object... args) {
    // bundle 是当前语言的资源包快照。
    ResourceBundle bundle = messages;
    if (!bundle.containsKey(key)) {
      return key;
    }
    // pattern 是资源中的原始模板。
    String pattern = bundle.getString(key);
    if (args == null || args.length == 0) {
      return pattern;
    }
    return new MessageFormat(pattern, currentLocale).format(args);
  }

  /**
   * 返回当前资源包，供 Picocli 解析 descriptionKey。
   *
   * @return 资源包
   */
  public static ResourceBundle bundle() {
    return messages;
  }

  /**
   * 返回统一错误前缀。
   *
   * @return 错误前缀
   */
  public static String errorPrefix() {
    return get("cli.error.prefix") + " ";
  }

  /**
   * 判断指定键是否存在。
   *
   * @param key 资源键
   * @return 存在返回 true
   */
  public static boolean contains(String key) {
    try {
      return messages.containsKey(key);
    } catch (MissingResourceException exception) {
      return false;
    }
  }

  /**
   * 返回适合资源加载的类加载器。
   *
   * @return 上下文类加载器；缺失时回退本类加载器
   */
  private static ClassLoader classLoader() {
    // contextLoader 是线程上下文类加载器，打包后更可靠。
    ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
    return contextLoader == null ? ClassLoader.getSystemClassLoader() : contextLoader;
  }
}
