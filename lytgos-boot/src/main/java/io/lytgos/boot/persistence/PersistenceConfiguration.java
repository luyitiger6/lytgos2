package io.lytgos.boot.persistence;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA 实体与仓库扫描配置。
 *
 * <p>启动类位于 {@code io.lytgos.boot}，默认扫描范围不包含 {@code io.lytgos.storage}； 这里显式把实体与 Spring Data
 * 仓库扫描范围扩展到整个 LytgOS 包。
 *
 * @author LytgOS2 Team
 */
@Configuration
@EntityScan(basePackages = "io.lytgos")
@EnableJpaRepositories(basePackages = "io.lytgos")
public class PersistenceConfiguration {}
