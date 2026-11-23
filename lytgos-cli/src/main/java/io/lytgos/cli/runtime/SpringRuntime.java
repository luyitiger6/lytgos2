package io.lytgos.cli.runtime;

import io.lytgos.cli.i18n.CliMessages;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * CLI 重命令的 Spring 运行时启动器。
 *
 * <p>CLI 模块不能编译依赖 boot 模块（boot 已经依赖 CLI），因此这里用反射加载组装根 {@code
 * io.lytgos.boot.LytgosApplication}。Spring 启动后由调用方获取 Bean；{@link #close()} 与关闭钩子都会释放上下文。
 *
 * @author LytgOS2 Team
 */
public final class SpringRuntime {

  /** Spring Boot 组装根类名。 */
  private static final String APPLICATION_CLASS_NAME = "io.lytgos.boot.LytgosApplication";

  /** 关闭钩子线程名。 */
  private static final String SHUTDOWN_THREAD_NAME = "lytgos-runtime-shutdown";

  /** 关闭信号；serve/gateway 用它阻塞主线程。 */
  private final CountDownLatch terminationLatch = new CountDownLatch(1);

  /** 当前 Spring 上下文；未启动或已关闭时为空。 */
  private final AtomicReference<ConfigurableApplicationContext> contextReference =
      new AtomicReference<>();

  /**
   * 启动 Spring 运行时。
   *
   * @param webType Web 应用类型；chat/gateway 使用 NONE，serve 使用 SERVLET
   * @return 已启动的 Spring 上下文
   */
  public ConfigurableApplicationContext start(WebApplicationType webType) {
    // Web 应用类型决定是否创建嵌入式服务器，不能为空。
    Objects.requireNonNull(webType, "webType");
    if (contextReference.get() != null) {
      throw new IllegalStateException(CliMessages.get("cli.runtime.alreadyStarted"));
    }
    try {
      // 反射加载 boot 组装根，保持 CLI 对 boot 的编译期零依赖。
      Class<?> applicationClass = Class.forName(APPLICATION_CLASS_NAME);
      // context 是本次启动的 Spring 上下文。
      ConfigurableApplicationContext context =
          new SpringApplicationBuilder(applicationClass).web(webType).run();
      contextReference.set(context);
      // 注册关闭钩子：Ctrl+C 时关闭 Spring 上下文并唤醒阻塞的 serve/gateway。
      Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, SHUTDOWN_THREAD_NAME));
      return context;
    } catch (ClassNotFoundException exception) {
      throw new IllegalStateException(
          CliMessages.get("cli.runtime.classNotFound", APPLICATION_CLASS_NAME), exception);
    } catch (RuntimeException exception) {
      // 启动失败时释放可能已经创建的上下文与等待线程。
      shutdown();
      throw new IllegalStateException(CliMessages.get("cli.runtime.startFailed"), exception);
    }
  }

  /**
   * 从当前上下文获取 Bean。
   *
   * @param type Bean 类型
   * @param <T> Bean 泛型
   * @return Bean 实例
   */
  @SuppressWarnings("PMD.CloseResource")
  public <T> T getBean(Class<T> type) {
    // 调用方必须先 start；否则给出明确错误而不是空指针。
    Objects.requireNonNull(type, "type");
    // context 是当前已启动的上下文。
    ConfigurableApplicationContext context = contextReference.get();
    if (context == null) {
      throw new IllegalStateException(CliMessages.get("cli.runtime.notStarted"));
    }
    return context.getBean(type);
  }

  /**
   * 阻塞当前线程，直到进程收到关闭信号。
   *
   * @throws InterruptedException 等待期间线程被中断
   */
  public void awaitTermination() throws InterruptedException {
    terminationLatch.await();
  }

  /** 关闭上下文并唤醒等待线程；可重复调用。 */
  public void close() {
    shutdown();
  }

  /** 关闭上下文并唤醒等待线程；可重复调用。 */
  @SuppressWarnings("PMD.CloseResource")
  private synchronized void shutdown() {
    // current 是本次需要关闭的上下文；取出后立即清空，避免重入。
    ConfigurableApplicationContext current = contextReference.getAndSet(null);
    try {
      if (current != null && current.isActive()) {
        current.close();
      }
    } finally {
      // 无论上下文是否关闭成功都要唤醒 serve/gateway，避免进程挂住。
      terminationLatch.countDown();
    }
  }
}
